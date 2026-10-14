package com.aetherion.nfcwallet.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

interface Authenticator {
    /** True if the device has a secure lock screen / enrolled biometrics we can use. */
    fun canAuthenticate(): Boolean

    /** Shows the system prompt. Returns true only on a successful unlock. */
    suspend fun authenticate(title: String, subtitle: String?): Boolean
}

class AuthGate(private val activity: FragmentActivity) : Authenticator {

    private val mutex = Mutex()

    /** While true the activity may be stopped by the system credential screen; don't lock. */
    @Volatile
    var promptInFlight: Boolean = false
        private set

    override fun canAuthenticate(): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun authenticate(title: String, subtitle: String?): Boolean =
        mutex.withLock {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    promptInFlight = true
                    val prompt = BiometricPrompt(
                        activity,
                        ContextCompat.getMainExecutor(activity),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                promptInFlight = false
                                if (cont.isActive) cont.resume(true)
                            }

                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                promptInFlight = false
                                if (cont.isActive) cont.resume(false)
                            }
                        },
                    )
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                        .build()
                    prompt.authenticate(info)
                    cont.invokeOnCancellation {
                        promptInFlight = false
                        prompt.cancelAuthentication()
                    }
                }
            }
        }
}
