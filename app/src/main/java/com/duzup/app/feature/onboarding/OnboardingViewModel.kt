package com.duzup.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.preferences.PreferencesRepository
import com.duzup.app.core.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
) : ViewModel() {

    val prefs: StateFlow<UserPreferences?> = preferences.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCurrency(code: String) = viewModelScope.launch { preferences.setCurrency(code) }
    fun setReminderTime(time: LocalTime) = viewModelScope.launch { preferences.setReminderTime(time) }

    /** Onboarding sets the default reminder timing for bills; every other kind can be tuned later in Settings. */
    fun toggleOffset(minutes: Int) {
        viewModelScope.launch {
            val current = preferences.current().offsetsFor(ItemKind.BILL)
            preferences.setOffsets(ItemKind.BILL, if (minutes in current) current - minutes else current + minutes)
        }
    }

    fun addOffset(minutes: Int) {
        viewModelScope.launch { preferences.setOffsets(ItemKind.BILL, preferences.current().offsetsFor(ItemKind.BILL) + minutes) }
    }

    fun markPermissionAsked() = viewModelScope.launch { preferences.setNotificationPermissionAsked(true) }

    /** Called for both "Get started" and "Skip". */
    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
            onDone()
        }
    }
}
