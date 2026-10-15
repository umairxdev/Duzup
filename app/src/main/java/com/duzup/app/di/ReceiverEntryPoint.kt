package com.duzup.app.di

import android.content.Context
import com.duzup.app.data.repository.BillRepository
import com.duzup.app.domain.reminders.ReminderEngine
import com.duzup.app.domain.reminders.ReminderNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

/** Lets receivers and workers reach singletons without becoming Hilt-injected Android components. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackgroundEntryPoint {
    fun billRepository(): BillRepository
    fun reminderEngine(): ReminderEngine
    fun reminderNotifier(): ReminderNotifier

    @ApplicationScope
    fun applicationScope(): CoroutineScope

    companion object {
        fun from(context: Context): BackgroundEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, BackgroundEntryPoint::class.java)
    }
}
