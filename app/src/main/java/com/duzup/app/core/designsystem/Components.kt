package com.duzup.app.core.designsystem

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

// ---- Layout scaffolding -----------------------------------------------------------------------

/** Frosted-silver screen background: a very soft vertical gradient, never decorative noise. */
@Composable
fun DuzupBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = DuzupTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.backgroundTop, colors.backgroundBottom))),
        content = content,
    )
}

/** Keeps content readable on tablets and landscape by capping and centering its width. */
@Composable
fun CenteredContent(modifier: Modifier = Modifier, maxWidth: Dp = 640.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize(), content = content)
    }
}

// ---- Surfaces ---------------------------------------------------------------------------------

@Composable
fun RaisedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    shape: Shape = RoundedCornerShape(Radii.card),
    elevation: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.neumorphic(shape, elevation)
    val interactive = if (onClick != null) {
        base.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    } else base
    Column(interactive, content = content)
}

// ---- Buttons ----------------------------------------------------------------------------------

/** Dark navy pill with subtle cyan edge lighting. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = DuzupTheme.colors
    val shape = RoundedCornerShape(Radii.pill)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(6.dp, shape, ambientColor = colors.shadowDark, spotColor = colors.shadowDark)
            .clip(shape)
            .background(colors.buttonPrimary)
            .border(1.dp, cyanEdgeBrush(), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = colors.onButtonPrimary, modifier = Modifier.size(18.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall, color = colors.onButtonPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = DuzupTheme.colors
    val tint = if (destructive) colors.overdue else colors.textPrimary
    // The 48dp touch target stays; the visible pill is 8dp shorter so buttons look lighter.
    Box(
        modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().padding(vertical = 4.dp).neumorphic(RoundedCornerShape(Radii.pill), 3.dp))
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            if (leadingIcon != null) Icon(leadingIcon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Square raised icon button (48dp touch target). */
@Composable
fun NeuIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = DuzupTheme.colors.textPrimary,
    enabled: Boolean = true,
) {
    // [size] is the touch target; the raised tile inside is 8dp smaller.
    Box(
        modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().padding(4.dp).neumorphic(RoundedCornerShape(13.dp), 3.dp))
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Plain icon button without elevation, for back arrows and toolbar actions (48dp target). */
@Composable
fun FlatIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = DuzupTheme.colors.textPrimary,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FlatIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onClick, modifier)
}

/** Selected = ink navy pill with white text; unselected = soft raised pill. */
@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Tab,
    leadingIcon: ImageVector? = null,
) {
    val colors = DuzupTheme.colors
    val shape = RoundedCornerShape(Radii.pill)
    val pillModifier = if (selected) {
        Modifier.clip(shape).background(colors.buttonPrimary).border(1.dp, cyanEdgeBrush(), shape)
    } else {
        Modifier.neumorphic(shape, 3.dp)
    }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = role, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            pillModifier.height(34.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, tint = if (selected) colors.onButtonPrimary else colors.textSecondary, modifier = Modifier.size(14.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onButtonPrimary else colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

// ---- Text -------------------------------------------------------------------------------------

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = DuzupTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = DuzupTheme.colors.textMuted)
        }
    }
}

/** Small upper-case section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = PixelMicroLabel,
        color = DuzupTheme.colors.textMuted,
        modifier = modifier.semantics { heading() },
    )
}

// ---- Inputs -----------------------------------------------------------------------------------

@Composable
fun InsetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    prefix: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 4,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
) {
    val colors = DuzupTheme.colors
    val shape = RoundedCornerShape(Radii.field)
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textPrimary)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .neumorphicInset(shape)
                .then(if (error != null) Modifier.border(1.5.dp, colors.overdue, shape) else Modifier),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (prefix != null) {
                    Text(prefix, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                    Spacer(Modifier.size(6.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted.copy(alpha = 0.8f),
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accentText),
                        singleLine = singleLine,
                        minLines = minLines,
                        maxLines = maxLines,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                        keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = label
                                if (error != null) this.error(error)
                            },
                    )
                }
            }
        }
        if (error != null) FieldError(error)
    }
}

@Composable
fun FieldError(message: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = DuzupTheme.colors.overdue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = DuzupTheme.colors.overdue)
    }
}

/** Inset row that opens a picker (date, repeat, time, currency). */
@Composable
fun SelectorField(
    label: String,
    value: String?,
    placeholder: String,
    leadingIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    val colors = DuzupTheme.colors
    val shape = RoundedCornerShape(Radii.field)
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textPrimary)
        Spacer(Modifier.height(3.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .neumorphicInset(shape)
                .then(if (error != null) Modifier.border(1.5.dp, colors.overdue, shape) else Modifier)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$label, ${value ?: placeholder}"
                    if (error != null) this.error(error)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(leadingIcon, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Text(
                value ?: placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = if (value != null) colors.textPrimary else colors.textMuted.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textMuted, modifier = Modifier.size(22.dp))
        }
        if (error != null) FieldError(error)
    }
}

// ---- Feedback ---------------------------------------------------------------------------------

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelField(columns = 8, rows = 6, unit = 7.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = DuzupTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = DuzupTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(actionLabel, onAction)
        }
    }
}

/** Subtle tactile confirmation, respecting the haptics setting. */
@Composable
fun rememberHaptics(): () -> Unit {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) {
        {
            if (enabled) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                    else HapticFeedbackConstants.VIRTUAL_KEY,
                )
            }
        }
    }
}

// ---- Dialogs & sheets -------------------------------------------------------------------------

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = stringResource(R.string.action_cancel),
) {
    val colors = DuzupTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(confirmLabel, color = if (destructive) colors.overdue else colors.accentText, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(dismissLabel, color = colors.textSecondary, style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuzupSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = DuzupTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = Color.Black.copy(alpha = 0.4f),
    ) {
        Column(Modifier.padding(horizontal = Spacing.screen).padding(bottom = 24.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuzupDatePickerDialog(
    initial: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DuzupTheme.colors
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = colors.surface),
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let {
                        onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text(stringResource(R.string.action_ok), color = colors.accentText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.textSecondary) } },
    ) {
        DatePicker(
            state = state,
            colors = DatePickerDefaults.colors(
                containerColor = colors.surface,
                selectedDayContainerColor = colors.buttonPrimary,
                selectedDayContentColor = colors.onButtonPrimary,
                todayDateBorderColor = colors.accentText,
                todayContentColor = colors.accentText,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuzupTimePickerDialog(
    initial: LocalTime,
    is24Hour: Boolean,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DuzupTheme.colors
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(24.dp),
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialSelectedContentColor = colors.onButtonPrimary,
                    selectorColor = colors.buttonPrimary,
                    timeSelectorSelectedContainerColor = colors.buttonPrimary,
                    timeSelectorSelectedContentColor = colors.onButtonPrimary,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_ok), color = colors.accentText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.textSecondary) } },
    )
}

/** Standard screen padding values. */
val ScreenPadding = PaddingValues(horizontal = Spacing.screen)

/** A labelled switch row (the whole row toggles). */
@Composable
fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DuzupTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Switch) { onChecked(!checked) }
            .semantics(mergeDescendants = true) {
                contentDescription = title
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = null,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = colors.onButtonPrimary,
                checkedTrackColor = colors.buttonPrimary,
                checkedBorderColor = colors.accent,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceInset,
                uncheckedBorderColor = colors.outline,
            ),
        )
    }
}
