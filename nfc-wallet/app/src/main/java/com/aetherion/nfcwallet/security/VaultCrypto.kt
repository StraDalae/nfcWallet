package com.aetherion.nfcwallet.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Two Keystore-backed AES-256-GCM keys, both bound to the user's biometric / screen-lock.
 *
 * META  - protects names, colours, notes, card type (everything the wallet list shows).
 *         Usable for [authWindowSeconds] after an unlock, so browsing the wallet is smooth.
 * DUMP  - protects the raw tag data (UID, memory pages, sectors, keys). Short window, so
 *         revealing or copying a card re-prompts unless you unlocked a moment ago.
 *
 * Key material never leaves the Keystore (StrongBox where the device has it). Without a
 * fresh authentication the OS refuses to use the key, so the database is inert ciphertext.
 */
enum class KeyTier(val alias: String, val authWindowSeconds: Int) {
    META("nfcwallet.meta.v1", 300),
    DUMP("nfcwallet.dump.v1", 30),
}

object VaultCrypto {
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val FORMAT_VERSION: Byte = 1
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun key(tier: KeyTier): SecretKey {
        (keyStore().getKey(tier.alias, null) as? SecretKey)?.let { return it }
        return generate(tier)
    }

    private fun generate(tier: KeyTier): SecretKey {
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(
            tier.alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                tier.authWindowSeconds,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
            // Adding a fingerprint must not destroy the vault; the device PIN already unlocks it.
            .setInvalidatedByBiometricEnrollment(false)
            .setUnlockedDeviceRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        return try {
            gen.init(spec(strongBox = true))
            gen.generateKey()
        } catch (e: ProviderException) {
            // StrongBoxUnavailableException is a ProviderException: fall back to the TEE.
            gen.init(spec(strongBox = false))
            gen.generateKey()
        }
    }

    /** Output layout: [version:1][iv:12][ciphertext + GCM tag]. [aad] binds the blob to its row. */
    fun encrypt(tier: KeyTier, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(tier))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return byteArrayOf(FORMAT_VERSION) + cipher.iv + ciphertext
    }

    fun decrypt(tier: KeyTier, blob: ByteArray, aad: ByteArray): ByteArray {
        require(blob.size > 1 + IV_LEN + TAG_BITS / 8 && blob[0] == FORMAT_VERSION) { "Unrecognised blob" }
        val iv = blob.copyOfRange(1, 1 + IV_LEN)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(tier), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(blob, 1 + IV_LEN, blob.size - 1 - IV_LEN)
    }

    fun deleteAllKeys() {
        val ks = keyStore()
        KeyTier.values().forEach { runCatching { ks.deleteEntry(it.alias) } }
    }

    /** True when the OS wants a fresh unlock before it will use the key. */
    fun needsAuth(t: Throwable?): Boolean = causeChain(t).any { it is UserNotAuthenticatedException }

    /** True when the key was destroyed (lock screen removed, etc.). Stored cards are unrecoverable. */
    fun isInvalidated(t: Throwable?): Boolean = causeChain(t).any { it is KeyPermanentlyInvalidatedException }

    private fun causeChain(t: Throwable?): Sequence<Throwable> =
        generateSequence(t) { it.cause }.take(8)
}
