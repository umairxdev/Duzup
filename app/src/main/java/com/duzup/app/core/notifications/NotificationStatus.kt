package com.duzup.app.core.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** What the user has actually allowed right now. Re-read whenever the app returns to the foreground. */
data class NotificationStatus(
    val notificationsAllowed: Boolean,
    val permissionRequestable: Boolean,
    val exactAlarmsAllowed: Boolean,
    val ignoringBatteryOptimizations: Boolean,
) {
    companion object {
        fun read(context: Context): NotificationStatus {
            val manager = context.getSystemService(NotificationManager::class.java)
            val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            val power = context.getSystemService(PowerManager::class.java)
            return NotificationStatus(
                notificationsAllowed = manager.areNotificationsEnabled() && permissionGranted,
                permissionRequestable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionGranted,
                exactAlarmsAllowed = exact,
                ignoringBatteryOptimizations = power.isIgnoringBatteryOptimizations(context.packageName),
            )
        }

        fun notificationSettingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        fun channelSettingsIntent(context: Context, channelId: String): Intent =
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)

        fun exactAlarmSettingsIntent(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
            } else {
                notificationSettingsIntent(context)
            }

        /** Opens the system battery optimization list (no special permission needed). */
        fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}

@Composable
fun rememberNotificationStatus(): State<NotificationStatus> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(NotificationStatus.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.value = NotificationStatus.read(context) }
    return state
}

/** Returns a function that asks for POST_NOTIFICATIONS on Android 13+ (no-op on older versions). */
@Composable
fun rememberNotificationPermissionRequester(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(granted)
    }
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onResult(NotificationStatus.read(context).notificationsAllowed)
            }
        }
    }
}
