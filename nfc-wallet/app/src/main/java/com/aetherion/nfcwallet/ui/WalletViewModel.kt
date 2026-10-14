package com.aetherion.nfcwallet.ui

import android.app.Application
import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aetherion.nfcwallet.WalletApp
import com.aetherion.nfcwallet.data.CardDump
import com.aetherion.nfcwallet.data.CardEntity
import com.aetherion.nfcwallet.data.CardMeta
import com.aetherion.nfcwallet.data.WalletCard
import com.aetherion.nfcwallet.nfc.TagReader
import com.aetherion.nfcwallet.nfc.TagWriter
import com.aetherion.nfcwallet.security.Authenticator
import com.aetherion.nfcwallet.security.VaultCrypto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NfcStatus { OK, DISABLED, UNSUPPORTED }

sealed interface NfcMode {
    data object Idle : NfcMode
    data object Scan : NfcMode
    data object Write : NfcMode
}

sealed interface ScanState {
    data object Idle : ScanState
    data object Waiting : ScanState
    data object Reading : ScanState
    data class Result(val result: TagReader.Result) : ScanState
    data class Error(val message: String) : ScanState
}

sealed interface WriteState {
    data object Idle : WriteState
    data class Waiting(val cardId: String, val isClassic: Boolean) : WriteState
    data object Writing : WriteState
    data class Done(val verified: Boolean, val detail: String) : WriteState
    data class Failed(val message: String) : WriteState
}

data class Revealed(val id: String, val dump: CardDump)

class WalletViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as WalletApp).repository

    @Volatile
    var authenticator: Authenticator? = null

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _vaultBroken = MutableStateFlow(false)
    val vaultBroken: StateFlow<Boolean> = _vaultBroken.asStateFlow()

    private val _needsScreenLock = MutableStateFlow(false)
    val needsScreenLock: StateFlow<Boolean> = _needsScreenLock.asStateFlow()

    private val _nfcStatus = MutableStateFlow(NfcStatus.OK)
    val nfcStatus: StateFlow<NfcStatus> = _nfcStatus.asStateFlow()

    private val _nfcMode = MutableStateFlow<NfcMode>(NfcMode.Idle)
    val nfcMode: StateFlow<NfcMode> = _nfcMode.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _write = MutableStateFlow<WriteState>(WriteState.Idle)
    val write: StateFlow<WriteState> = _write.asStateFlow()

    private val _magic = MutableStateFlow(false)
    val magic: StateFlow<Boolean> = _magic.asStateFlow()

    private val _revealed = MutableStateFlow<Revealed?>(null)
    val revealed: StateFlow<Revealed?> = _revealed.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var writeDump: CardDump? = null
    private var currentWriteId: String? = null
    private var unlockJob: Job? = null
    private var autoPromptSuppressed = false

    /** Decrypted list entries. Empty whenever the wallet is locked. */
    val cards: StateFlow<List<WalletCard>> = combine(repo.rows, _unlocked) { rows, ok ->
        if (ok) decryptAll(rows) else emptyList()
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun decryptAll(rows: List<CardEntity>): List<WalletCard> {
        val out = ArrayList<WalletCard>(rows.size)
        var failures = 0
        for (row in rows) {
            try {
                out += WalletCard(row.id, repo.decryptMeta(row))
            } catch (e: Exception) {
                when {
                    VaultCrypto.needsAuth(e) -> {
                        _unlocked.value = false
                        return emptyList()
                    }
                    VaultCrypto.isInvalidated(e) -> {
                        _vaultBroken.value = true
                        return emptyList()
                    }
                    else -> failures++
                }
            }
        }
        // Every row failing for a non-auth reason means the key is gone (e.g. screen lock removed).
        if (rows.isNotEmpty() && failures == rows.size) {
            _vaultBroken.value = true
            return emptyList()
        }
        return out.sortedBy { it.meta.name.lowercase() }
    }

    // ---- lock / unlock ----------------------------------------------------------------------

    /** Prompts when the app comes to the foreground, unless the user just dismissed a prompt. */
    fun autoUnlock() {
        if (!autoPromptSuppressed) unlock()
    }

    fun unlock() {
        if (unlockJob?.isActive == true || _unlocked.value) return
        unlockJob = viewModelScope.launch {
            val auth = authenticator ?: return@launch
            if (!auth.canAuthenticate()) {
                _needsScreenLock.value = true
                return@launch
            }
            _needsScreenLock.value = false
            if (!auth.authenticate("Unlock NFC Wallet", "Your cards are encrypted until you unlock.")) {
                autoPromptSuppressed = true // user cancelled: wait for the Unlock button
                return@launch
            }
            autoPromptSuppressed = false
            val check = runCatching { withContext(Dispatchers.Default) { repo.checkUnlocked() } }.exceptionOrNull()
            when {
                check == null -> _unlocked.value = true
                VaultCrypto.isInvalidated(check) -> _vaultBroken.value = true
                else -> _message.value = "Couldn't open the vault: ${check.message}"
            }
        }
    }

    /** Drops every decrypted thing from memory. Called when the app leaves the foreground. */
    fun lock() {
        autoPromptSuppressed = false
        _unlocked.value = false
        _revealed.value = null
        cancelScan()
        cancelWrite()
    }

    fun setNfcStatus(status: NfcStatus) {
        _nfcStatus.value = status
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Runs [block]; if the OS wants a fresh unlock, prompts once and retries. Null on failure/cancel. */
    private suspend fun <T> guarded(title: String, block: suspend () -> T): T? {
        repeat(2) { attempt ->
            try {
                return withContext(Dispatchers.Default) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when {
                    VaultCrypto.needsAuth(e) && attempt == 0 -> {
                        if (authenticator?.authenticate(title, null) != true) return null
                    }
                    VaultCrypto.isInvalidated(e) -> {
                        _vaultBroken.value = true
                        return null
                    }
                    else -> {
                        _message.value = "Something went wrong: ${e.message}"
                        return null
                    }
                }
            }
        }
        return null
    }

    // ---- scanning & saving ------------------------------------------------------------------

    fun startScan() {
        _scan.value = ScanState.Waiting
        _nfcMode.value = NfcMode.Scan
    }

    fun cancelScan() {
        _scan.value = ScanState.Idle
        if (_nfcMode.value == NfcMode.Scan) _nfcMode.value = NfcMode.Idle
    }

    fun saveScanned(name: String, colorIndex: Int, iconKey: String, note: String) {
        val result = (_scan.value as? ScanState.Result)?.result ?: return
        viewModelScope.launch {
            val meta = CardMeta(
                name = name.trim(),
                colorIndex = colorIndex,
                iconKey = iconKey,
                note = note.trim(),
                family = result.family,
                capability = result.capability,
                summary = result.summary,
                createdAt = System.currentTimeMillis(),
            )
            val id = guarded("Unlock to save this card") { repo.save(meta, result.dump) }
            if (id != null) _scan.value = ScanState.Idle
        }
    }

    // ---- NFC callback -----------------------------------------------------------------------

    /** Called from the NFC reader-mode callback thread. */
    fun onTag(tag: Tag) {
        when (_nfcMode.value) {
            NfcMode.Idle -> Unit
            NfcMode.Scan -> {
                _nfcMode.value = NfcMode.Idle // one tag per attempt
                _scan.value = ScanState.Reading
                viewModelScope.launch {
                    _scan.value = try {
                        ScanState.Result(withContext(Dispatchers.IO) { TagReader.read(tag) })
                    } catch (e: Exception) {
                        ScanState.Error("Couldn't read that tag (${e.message ?: "tag lost"}). Hold it steady and try again.")
                    }
                }
            }
            NfcMode.Write -> {
                val dump = writeDump ?: return
                _nfcMode.value = NfcMode.Idle
                val cardId = (_write.value as? WriteState.Waiting)?.cardId
                _write.value = WriteState.Writing
                viewModelScope.launch {
                    val outcome = withContext(Dispatchers.IO) { TagWriter.write(tag, dump, _magic.value) }
                    _write.value = when (outcome) {
                        is TagWriter.Outcome.Success -> WriteState.Done(outcome.verified, outcome.detail)
                        is TagWriter.Outcome.Failure -> WriteState.Failed(outcome.message)
                    }
                    if (outcome is TagWriter.Outcome.Success && cardId != null) {
                        runCatching {
                            withContext(Dispatchers.Default) {
                                repo.updateMeta(cardId) { it.copy(lastUsedAt = System.currentTimeMillis()) }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- detail screen ----------------------------------------------------------------------

    fun reveal(id: String) {
        viewModelScope.launch {
            val dump = guarded("Unlock to view card data") { repo.loadDump(id) } ?: return@launch
            _revealed.value = Revealed(id, dump)
        }
    }

    fun hideReveal() {
        _revealed.value = null
    }

    fun updateCard(id: String, name: String, colorIndex: Int, iconKey: String, note: String) {
        viewModelScope.launch {
            guarded("Unlock to edit this card") {
                repo.updateMeta(id) {
                    it.copy(name = name.trim(), colorIndex = colorIndex, iconKey = iconKey, note = note.trim())
                }
            }
        }
    }

    fun deleteCard(id: String) {
        viewModelScope.launch {
            // Make sure the unlock window is fresh so the list refresh after deletion can decrypt.
            val ok = guarded("Unlock to delete this card") { repo.checkUnlocked() }
            if (ok != null) {
                repo.delete(id)
                if (_revealed.value?.id == id) _revealed.value = null
            }
        }
    }

    // ---- writing (copying to a blank tag) ---------------------------------------------------

    fun startWrite(id: String) {
        viewModelScope.launch {
            val dump = guarded("Unlock to copy this card") { repo.loadDump(id) } ?: return@launch
            writeDump = dump
            currentWriteId = id
            _magic.value = false
            _write.value = WriteState.Waiting(id, isClassic = dump.classic != null)
            _nfcMode.value = NfcMode.Write
        }
    }

    fun retryWrite() {
        val id = currentWriteId ?: return
        val dump = writeDump ?: return
        _write.value = WriteState.Waiting(id, isClassic = dump.classic != null)
        _nfcMode.value = NfcMode.Write
    }

    fun setMagic(enabled: Boolean) {
        _magic.value = enabled
    }

    fun cancelWrite() {
        writeDump = null
        currentWriteId = null
        _write.value = WriteState.Idle
        if (_nfcMode.value == NfcMode.Write) _nfcMode.value = NfcMode.Idle
    }

    // ---- danger zone ------------------------------------------------------------------------

    fun resetVault() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.wipeEverything() }
            _vaultBroken.value = false
            lock()
        }
    }

    override fun onCleared() {
        writeDump = null
        super.onCleared()
    }
}
