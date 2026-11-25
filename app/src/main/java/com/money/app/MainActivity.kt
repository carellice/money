package com.money.app

import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.money.app.data.Settings
import com.money.app.ui.MoneyRoot
import com.money.app.ui.components.LocalHideAmounts
import com.money.app.ui.components.LocalHideBalances
import com.money.app.ui.lock.Biometric
import com.money.app.ui.lock.LockScreen
import com.money.app.ui.onboarding.OnboardingScreen
import com.money.app.ui.tour.Guide
import com.money.app.ui.tour.LocalGuide
import com.money.app.ui.theme.MoneyTheme
import com.money.app.ui.theme.isDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.YearMonth
import kotlin.math.sqrt

class MainActivity : FragmentActivity(), SensorEventListener {
    private val container get() = (application as MoneyApp).container

    private var locked by mutableStateOf(false)
    private var deepLink by mutableStateOf<String?>(null)
    private val guide = Guide(onboarding = false)
    private var lockEnabled = false
    private var stoppedAt = 0L
    private var authenticating = false
    private val sensors by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private var lastJolt = 0L
    private var jolts = 0
    private var lastShake = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Read once synchronously so the first frame has the right theme and lock state.
        val initial: Settings = runBlocking { container.settings.current() }
        lockEnabled = initial.lockEnabled
        if (savedInstanceState == null) locked = initial.lockEnabled
        guide.onboarding = !initial.onboarded
        handleIntent(intent)

        lifecycleScope.launch {
            container.settings.settings.collect { lockEnabled = it.lockEnabled }
        }
        lifecycleScope.launch {
            container.repository.errors.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
        }
        // Server mode: changes made elsewhere (PWA, other phones) show up on opening and while the app stays open.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    container.repository.refresh()
                    delay(REFRESH_MS)
                }
            }
        }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initial)
            // Dueffe is dark only.
            val dark = settings.dueffe || isDarkTheme(settings.theme)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MoneyTheme(dark, settings.accent, settings.skeuo && !settings.metro && !settings.nothing, settings.metro && !settings.nothing, settings.nothing && !settings.dueffe, settings.dueffe) {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalHideAmounts provides settings.hideAmounts, LocalHideBalances provides settings.hideBalances, LocalGuide provides guide) {
                        MoneyRoot(deepLink, onDeepLinkHandled = { deepLink = null })
                    }
                    AnimatedVisibility(
                        visible = locked && settings.lockEnabled,
                        enter = fadeIn(),
                        exit = fadeOut() + scaleOut(targetScale = 1.1f),
                    ) {
                        LockScreen(onUnlockRequest = ::unlock)
                    }
                    AnimatedVisibility(
                        visible = guide.onboarding,
                        enter = fadeIn(),
                        exit = fadeOut() + scaleOut(targetScale = 1.1f),
                    ) {
                        OnboardingScreen(
                            settings = settings,
                            onTheme = { lifecycleScope.launch { container.settings.setTheme(it) } },
                            onAccent = { lifecycleScope.launch { container.settings.setAccent(it) } },
                            onDone = { tour ->
                                guide.onboarding = false
                                if (tour) guide.start()
                                lifecycleScope.launch { container.settings.setOnboarded() }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_MONTH)?.let { runCatching { container.month.value = YearMonth.parse(it) } }
        intent.getStringExtra(EXTRA_ROUTE)?.let { deepLink = it }
    }

    override fun onResume() {
        super.onResume()
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensors.unregisterListener(this)
    }

    /** Shaking the phone hides the amounts, or shows them again: two strong jolts close together. */
    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (g < SHAKE_G) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastShake < SHAKE_COOLDOWN_MS) return
        jolts = if (now - lastJolt < JOLT_GAP_MS) jolts + 1 else 1
        lastJolt = now
        if (jolts < 2) return
        jolts = 0
        lastShake = now
        window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        lifecycleScope.launch { container.settings.run { setHideAmounts(!current().hideAmounts) } }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onStop() {
        super.onStop()
        if (!authenticating) stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Short trips outside the app (camera, file picker) don't lock it again.
        if (lockEnabled && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > LOCK_AFTER_MS) locked = true
        stoppedAt = 0
    }

    private fun unlock() {
        if (authenticating) return
        if (!Biometric.available(this)) {
            locked = false
            return
        }
        authenticating = true
        Biometric.authenticate(
            this,
            "Sblocca Money",
            onSuccess = {
                authenticating = false
                locked = false
            },
            onFail = { authenticating = false },
        )
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_MONTH = "month"
        private const val LOCK_AFTER_MS = 30_000L
        private const val REFRESH_MS = 30_000L
        private const val SHAKE_G = 2.5f
        private const val JOLT_GAP_MS = 500L
        private const val SHAKE_COOLDOWN_MS = 1_000L
    }
}
