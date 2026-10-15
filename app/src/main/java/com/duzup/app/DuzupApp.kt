package com.duzup.app

import android.app.Application
import com.duzup.app.core.notifications.MaintenanceWorker
import com.duzup.app.core.notifications.NotificationHelper
import com.duzup.app.data.repository.BillRepository
import com.duzup.app.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DuzupApp : Application() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var repository: BillRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
        MaintenanceWorker.schedule(this)
        appScope.launch {
            repository.ensureDefaultCategories()
            repository.reconcileAll()
        }
    }
}
