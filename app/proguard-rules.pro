# Duzup release rules. Hilt, Room, WorkManager and kotlinx.serialization ship their own consumer rules;
# only app-specific reflection needs to be kept here.

# Receivers declared in the manifest are instantiated by the system by class name.
-keep class com.duzup.app.core.notifications.AlarmReceiver
-keep class com.duzup.app.core.notifications.NotificationActionReceiver
-keep class com.duzup.app.core.notifications.SystemEventsReceiver

# WorkManager instantiates workers reflectively.
-keep class com.duzup.app.core.notifications.MaintenanceWorker { *; }

# Backup file format: keep generated serializers for the @Serializable DTOs.
-keepclassmembers class com.duzup.app.core.backup.** {
    *** Companion;
}
-keepclasseswithmembers class com.duzup.app.core.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
