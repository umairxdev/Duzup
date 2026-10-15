package com.duzup.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzup.app.core.preferences.PreferencesRepository
import com.duzup.app.core.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** App-level state: theme, motion/haptics and whether onboarding is done. Null until DataStore loads. */
@HiltViewModel
class MainViewModel @Inject constructor(
    preferences: PreferencesRepository,
) : ViewModel() {
    val prefs: StateFlow<UserPreferences?> = preferences.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
