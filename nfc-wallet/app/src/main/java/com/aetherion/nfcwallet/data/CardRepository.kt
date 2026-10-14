package com.aetherion.nfcwallet.data

import com.aetherion.nfcwallet.security.KeyTier
import com.aetherion.nfcwallet.security.VaultCrypto
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * All crypto happens here. Functions throw when the OS needs a fresh unlock
 * (see [VaultCrypto.needsAuth]); the ViewModel catches that, prompts, and retries.
 */
class CardRepository(private val dao: CardDao) {

    val rows: Flow<List<CardEntity>> = dao.observeAll()

    private fun aad(id: String) = id.toByteArray(Charsets.UTF_8)

    /** Forces the OS to check the unlock window for the META key without touching data. */
    fun checkUnlocked() {
        VaultCrypto.encrypt(KeyTier.META, ByteArray(1), ByteArray(0))
    }

    suspend fun save(meta: CardMeta, dump: CardDump): String {
        val id = UUID.randomUUID().toString()
        val metaBytes = VaultJson.encodeToString(CardMeta.serializer(), meta).toByteArray()
        val dumpBytes = VaultJson.encodeToString(CardDump.serializer(), dump).toByteArray()
        try {
            val entity = CardEntity(
                id = id,
                metaBlob = VaultCrypto.encrypt(KeyTier.META, metaBytes, aad(id)),
                dumpBlob = VaultCrypto.encrypt(KeyTier.DUMP, dumpBytes, aad(id)),
            )
            dao.upsert(entity)
        } finally {
            metaBytes.fill(0)
            dumpBytes.fill(0)
        }
        return id
    }

    fun decryptMeta(entity: CardEntity): CardMeta {
        val bytes = VaultCrypto.decrypt(KeyTier.META, entity.metaBlob, aad(entity.id))
        return try {
            VaultJson.decodeFromString(CardMeta.serializer(), String(bytes))
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun loadDump(id: String): CardDump {
        val entity = dao.get(id) ?: throw NoSuchElementException("Card not found")
        val bytes = VaultCrypto.decrypt(KeyTier.DUMP, entity.dumpBlob, aad(id))
        return try {
            VaultJson.decodeFromString(CardDump.serializer(), String(bytes))
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun updateMeta(id: String, transform: (CardMeta) -> CardMeta) {
        val entity = dao.get(id) ?: return
        val updated = transform(decryptMeta(entity))
        val bytes = VaultJson.encodeToString(CardMeta.serializer(), updated).toByteArray()
        try {
            dao.upsert(
                CardEntity(
                    id = id,
                    metaBlob = VaultCrypto.encrypt(KeyTier.META, bytes, aad(id)),
                    dumpBlob = entity.dumpBlob,
                ),
            )
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun delete(id: String) = dao.delete(id)

    /** Removes every card and destroys both Keystore keys. */
    suspend fun wipeEverything() {
        dao.deleteAll()
        VaultCrypto.deleteAllKeys()
    }
}
