package com.forensim.app.feature.student

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Today's date that stays current: it flips at local midnight while the screen is open, and is
 * re-checked whenever the app comes back to the foreground (an app left in memory overnight would
 * otherwise keep showing yesterday's term, quiz and exhibit).
 */
@Composable
fun rememberToday(): State<LocalDate> {
    val today = remember { mutableStateOf(LocalDate.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today.value = LocalDate.now() }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, midnight).toMillis().coerceAtLeast(0) + 500)
            today.value = LocalDate.now()
        }
    }
    return today
}
