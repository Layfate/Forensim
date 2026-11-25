package com.forensim.app.feature.student

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.forensim.app.ForensimApplication
import com.forensim.app.MainActivity
import com.forensim.app.R
import com.forensim.app.core.data.DailyRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * "Keep your streak" reminder: once a day in the early evening, if a signed-in student hasn't
 * answered today's Quiz of the day yet, a notification nudges them. It respects Settings >
 * Notifications > Study reminders and the system notification permission.
 */
object StreakReminder {
    private const val WORK = "streak-reminder"
    const val CHANNEL = "study_reminders"
    private val AT: LocalTime = LocalTime.of(19, 0)

    /** Schedules the daily check (idempotent; safe to call on every app start). */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(AT)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notify_reminders), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.notify_reminders_sub) })
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val app = applicationContext as? ForensimApplication ?: return Result.success()
            val c = app.container
            if (!c.settings.settings.first().notifications.reminders) return Result.success()
            if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return Result.success()
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return Result.success()
            if (c.supabase.session.value == null) return Result.success() // signed out
            // Students only.
            val role = runCatching { (c.supabase.rpcRaw("my_role") as? JsonPrimitive)?.contentOrNull }.getOrNull()
            if (role != "student") return Result.success()

            c.daily.refresh()
            val today = LocalDate.now()
            val entries = c.daily.entries.value
            if (entries[today.toString()]?.quizChoice != null) return Result.success() // already answered today
            notify(applicationContext, DailyRepository.streak(entries, today))
            return Result.success()
        }
    }

    /** Settings > Notifications: shows what the reminder looks like right now. */
    fun sendTest(context: Context) = notify(context, 3)

    @android.annotation.SuppressLint("MissingPermission") // checked just below
    private fun notify(context: Context, streak: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val (title, body) = if (streak > 0)
            context.getString(R.string.reminder_keep_title, streak) to context.getString(R.string.reminder_keep_body)
        else context.getString(R.string.reminder_start_title) to context.getString(R.string.reminder_start_body)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_flame)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1001, n) } // SecurityException if permission was revoked
    }
}
