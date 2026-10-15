package com.duzup.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.duzup.app.core.database.BillDao
import com.duzup.app.core.database.CategoryDao
import com.duzup.app.core.database.OccurrenceDao
import com.duzup.app.core.database.ReminderDao
import com.duzup.app.core.database.DuzupDatabase
import com.duzup.app.core.notifications.AndroidAlarmScheduler
import com.duzup.app.core.notifications.NotificationHelper
import com.duzup.app.core.time.SystemTimeProvider
import com.duzup.app.core.time.TimeProvider
import com.duzup.app.domain.reminders.AlarmScheduler
import com.duzup.app.domain.reminders.ReminderNotifier
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): DuzupDatabase =
        Room.databaseBuilder(context, DuzupDatabase::class.java, DuzupDatabase.NAME)
            .addMigrations(*DuzupDatabase.MIGRATIONS)
            .build()

    @Provides fun billDao(db: DuzupDatabase): BillDao = db.billDao()
    @Provides fun occurrenceDao(db: DuzupDatabase): OccurrenceDao = db.occurrenceDao()
    @Provides fun reminderDao(db: DuzupDatabase): ReminderDao = db.reminderDao()
    @Provides fun categoryDao(db: DuzupDatabase): CategoryDao = db.categoryDao()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("duzup_prefs") }

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun timeProvider(impl: SystemTimeProvider): TimeProvider
    @Binds abstract fun alarmScheduler(impl: AndroidAlarmScheduler): AlarmScheduler
    @Binds abstract fun reminderNotifier(impl: NotificationHelper): ReminderNotifier
}
