package com.duzup.app.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzup.app.core.backup.BackupCodec
import com.duzup.app.core.backup.BackupException
import com.duzup.app.core.backup.BackupManager
import com.duzup.app.core.backup.RestoreMode
import com.duzup.app.core.backup.RestoreResult
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.SnoozeChoice
import com.duzup.app.core.model.ThemeMode
import com.duzup.app.core.preferences.PreferencesRepository
import com.duzup.app.core.preferences.UserPreferences
import com.duzup.app.data.repository.BillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

/** A validated backup waiting for the user to choose replace or merge. Holds counts only for display. */
data class PendingImport(val text: String, val bills: Int, val occurrences: Int, val categories: Int)

sealed interface SettingsMessage {
    data object ExportDone : SettingsMessage
    data object ExportFailed : SettingsMessage
    data class ImportDone(val result: RestoreResult, val mode: RestoreMode) : SettingsMessage
    data class ImportRejected(val reason: BackupException.Reason) : SettingsMessage
    data object ImportFailed : SettingsMessage
    data object ResetDone : SettingsMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository,
    private val repository: BillRepository,
    private val backup: BackupManager,
) : ViewModel() {

    val prefs: StateFlow<UserPreferences?> = preferences.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setTheme(mode: ThemeMode) = launch { preferences.setTheme(mode) }
    fun setCurrency(code: String) = launch { preferences.setCurrency(code) }
    fun setFirstDay(day: DayOfWeek) = launch { preferences.setFirstDayOfWeek(day) }
    fun setHaptics(enabled: Boolean) = launch { preferences.setHaptics(enabled) }
    fun setReduceMotion(enabled: Boolean) = launch { preferences.setReduceMotion(enabled) }
    fun setHideAmounts(enabled: Boolean) = launch { preferences.setHideAmounts(enabled) }

    fun setReminderTime(time: LocalTime) = launch { preferences.setReminderTime(time) }

    fun setOverdueReminders(enabled: Boolean) = launch {
        preferences.setOverdueReminders(enabled)
        repository.reconcileAll() // adds or removes the overdue reminders right away
    }

    fun toggleDefaultOffset(kind: ItemKind, minutes: Int) = launch {
        val current = preferences.current().offsetsFor(kind)
        preferences.setOffsets(kind, if (minutes in current) current - minutes else current + minutes)
    }

    fun addDefaultOffset(kind: ItemKind, minutes: Int) = launch {
        preferences.setOffsets(kind, preferences.current().offsetsFor(kind) + minutes)
    }

    fun setQuietHoursEnabled(enabled: Boolean) = launch {
        preferences.setQuietHoursEnabled(enabled)
        repository.reconcileAll() // re-times the alarms already scheduled
    }

    fun setQuietStart(time: LocalTime) = launch { preferences.setQuietStart(time); repository.reconcileAll() }
    fun setQuietEnd(time: LocalTime) = launch { preferences.setQuietEnd(time); repository.reconcileAll() }
    fun setSnooze(choice: SnoozeChoice) = launch { preferences.setSnooze(choice) }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val ok = runCatching {
                val json = backup.export()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("no stream")
                }
            }.isSuccess
            _busy.value = false
            _messages.send(if (ok) SettingsMessage.ExportDone else SettingsMessage.ExportFailed)
        }
    }

    /** Reads (size-capped) and fully validates the file; nothing is written until the user confirms. */
    fun prepareImport(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val text = withContext(Dispatchers.IO) { readCapped(uri) }
                val document = BackupCodec.decode(text)
                _pendingImport.value = PendingImport(
                    text = text,
                    bills = document.items.size,
                    occurrences = document.occurrences.size,
                    categories = document.categories.size,
                )
            } catch (e: BackupException) {
                _messages.send(SettingsMessage.ImportRejected(e.reason))
            } catch (_: Exception) {
                _messages.send(SettingsMessage.ImportFailed)
            } finally {
                _busy.value = false
            }
        }
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    fun confirmImport(mode: RestoreMode) {
        val pending = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            _busy.value = true
            try {
                val result = backup.restore(pending.text, mode)
                _messages.send(SettingsMessage.ImportDone(result, mode))
            } catch (e: BackupException) {
                _messages.send(SettingsMessage.ImportRejected(e.reason))
            } catch (_: Exception) {
                _messages.send(SettingsMessage.ImportFailed)
            } finally {
                _busy.value = false
            }
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            _busy.value = true
            repository.resetAllData()
            _busy.value = false
            _messages.send(SettingsMessage.ResetDone)
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun readCapped(uri: Uri): String {
        val limit = BackupCodec.MAX_BYTES
        val out = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > limit) {
                    throw BackupException(BackupException.Reason.TOO_LARGE, "Backup is too large")
                }
            }
        } ?: throw BackupException(BackupException.Reason.NOT_JSON, "Cannot open file")
        return out.toString(Charsets.UTF_8.name())
    }
}
