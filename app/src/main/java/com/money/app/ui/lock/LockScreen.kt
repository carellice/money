package com.money.app.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.money.app.data.Palette
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.blob

object Biometric {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onFail: (String) -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Usa impronta, volto o PIN del telefono")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        )
    }
}

@Composable
fun LockScreen(onUnlockRequest: () -> Unit) {
    val c = Ui.colors
    LaunchedEffect(Unit) { onUnlockRequest() }
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(200.dp)
                    .blob(Brush.linearGradient(listOf(c.accent, Color(Palette.ROSE))), seed = 9, amplitude = 0.1f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Fingerprint, null, tint = Ink, modifier = Modifier.size(84.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Money è bloccata", style = Ui.type.title, color = c.ink)
            Text("I tuoi conti restano tra te e te.", style = Ui.type.body, color = c.inkSoft)
        }
        PrimaryButton(
            "Sblocca",
            onUnlockRequest,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
            icon = Icons.Rounded.Fingerprint,
        )
    }
}
