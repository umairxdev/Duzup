package com.duzup.app.feature.onboarding

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import com.duzup.app.core.designsystem.CenteredContent
import com.duzup.app.core.designsystem.PixelField
import com.duzup.app.core.designsystem.PrimaryButton
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.DuzupTimePickerDialog
import com.duzup.app.core.designsystem.SecondaryButton
import com.duzup.app.core.designsystem.SelectorField
import com.duzup.app.core.designsystem.navigationBarBottom
import com.duzup.app.core.designsystem.statusBarTop
import com.duzup.app.core.notifications.rememberNotificationPermissionRequester
import com.duzup.app.core.notifications.rememberNotificationStatus
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.preferences.UserPreferences
import com.duzup.app.core.time.DateFormats
import com.duzup.app.feature.addedit.CurrencySheet
import com.duzup.app.feature.addedit.CustomOffsetDialog
import com.duzup.app.feature.addedit.ReminderChips
import kotlinx.coroutines.launch
import java.util.Currency

private const val PAGE_COUNT = 3

@Composable
fun OnboardingScreen(
    prefs: UserPreferences?,
    onCurrency: (String) -> Unit,
    onReminderTime: (java.time.LocalTime) -> Unit,
    onToggleOffset: (Int) -> Unit,
    onAddOffset: (Int) -> Unit,
    onPermissionAsked: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = DuzupTheme.colors
    val pager = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGE_COUNT - 1

    CenteredContent(maxWidth = 560.dp) {
        Column(Modifier.fillMaxSize().padding(top = statusBarTop() + 8.dp, bottom = navigationBarBottom() + 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.onboarding_skip), color = colors.textMuted, style = MaterialTheme.typography.labelLarge)
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (page) {
                        0 -> TrackPage()
                        1 -> AlertsPage(prefs, onReminderTime, onToggleOffset, onAddOffset, onPermissionAsked)
                        else -> PrivatePage(prefs, onCurrency)
                    }
                }
            }
            PageDots(pager.currentPage)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = stringResource(if (last) R.string.onboarding_get_started else R.string.onboarding_continue),
                onClick = {
                    if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun PageDots(current: Int) {
    val colors = DuzupTheme.colors
    val label = stringResource(R.string.onboarding_page_of, current + 1, PAGE_COUNT)
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(PAGE_COUNT) { index ->
            Box(
                Modifier
                    .size(if (index == current) 12.dp else 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index == current) colors.accent else colors.outline),
            )
        }
    }
}

@Composable
private fun PageHeader(title: String, body: String) {
    val colors = DuzupTheme.colors
    Text(
        title,
        style = MaterialTheme.typography.displaySmall,
        color = colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
    )
    Spacer(Modifier.height(12.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun TrackPage() {
    Box(contentAlignment = Alignment.TopEnd) {
        Image(
            painter = painterResource(R.drawable.ic_brand_logo),
            contentDescription = null,
            modifier = Modifier.padding(top = 8.dp).size(168.dp),
        )
        PixelField(Modifier.padding(start = 4.dp), columns = 6, rows = 6, unit = 6.dp)
    }
    Spacer(Modifier.height(16.dp))
    PageHeader(stringResource(R.string.onboarding_track_title), stringResource(R.string.onboarding_track_body))
}

@Composable
private fun AlertsPage(
    prefs: UserPreferences?,
    onReminderTime: (java.time.LocalTime) -> Unit,
    onToggleOffset: (Int) -> Unit,
    onAddOffset: (Int) -> Unit,
    onPermissionAsked: () -> Unit,
) {
    val colors = DuzupTheme.colors
    val context = LocalContext.current
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var customOffset by rememberSaveable { mutableStateOf(false) }
    val status by rememberNotificationStatus()
    val requestPermission = rememberNotificationPermissionRequester { onPermissionAsked() }

    PageHeader(stringResource(R.string.onboarding_alerts_title), stringResource(R.string.onboarding_alerts_body))
    if (prefs != null) {
        ReminderChips(ItemKind.BILL, prefs.offsetsFor(ItemKind.BILL).toSet(), onToggleOffset, onCustom = { customOffset = true })
        Spacer(Modifier.height(16.dp))
        SelectorField(
            label = stringResource(R.string.field_reminder_time),
            value = DateFormats.time(prefs.reminderTime),
            placeholder = "",
            leadingIcon = Icons.Rounded.Schedule,
            onClick = { pickingTime = true },
        )
    }
    Spacer(Modifier.height(20.dp))
    if (!status.notificationsAllowed && status.permissionRequestable) {
        Text(
            stringResource(R.string.onboarding_permission_why),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(stringResource(R.string.allow_notifications), requestPermission)
    }
    if (pickingTime && prefs != null) {
        DuzupTimePickerDialog(
            initial = prefs.reminderTime,
            is24Hour = DateFormat.is24HourFormat(context),
            onConfirm = { onReminderTime(it); pickingTime = false },
            onDismiss = { pickingTime = false },
        )
    }
    if (customOffset) CustomOffsetDialog({ onAddOffset(it); customOffset = false }, { customOffset = false })
}

@Composable
private fun PrivatePage(prefs: UserPreferences?, onCurrency: (String) -> Unit) {
    var pickingCurrency by rememberSaveable { mutableStateOf(false) }
    PageHeader(stringResource(R.string.onboarding_private_title), stringResource(R.string.onboarding_private_body))
    if (prefs != null) {
        SelectorField(
            label = stringResource(R.string.field_currency),
            value = "${prefs.currencyCode} · ${runCatching { Currency.getInstance(prefs.currencyCode).displayName }.getOrDefault("")}",
            placeholder = "",
            leadingIcon = Icons.Rounded.Payments,
            onClick = { pickingCurrency = true },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_currency_note),
            style = MaterialTheme.typography.bodySmall,
            color = DuzupTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
    if (pickingCurrency && prefs != null) {
        CurrencySheet(prefs.currencyCode, { onCurrency(it); pickingCurrency = false }, { pickingCurrency = false })
    }
}
