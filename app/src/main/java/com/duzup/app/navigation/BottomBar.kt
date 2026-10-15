package com.duzup.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.cyanEdgeBrush
import com.duzup.app.core.designsystem.navigationBarBottom
import com.duzup.app.core.designsystem.neumorphic
import kotlin.reflect.KClass

enum class TopLevel(val route: Any, val routeClass: KClass<*>, val label: Int, val icon: ImageVector) {
    HOME(HomeRoute, HomeRoute::class, R.string.nav_home, Icons.Rounded.Home),
    CALENDAR(CalendarRoute, CalendarRoute::class, R.string.nav_calendar, Icons.Rounded.CalendarMonth),
    NOTES(NotesRoute, NotesRoute::class, R.string.nav_notes, Icons.Rounded.StickyNote2),
    SETTINGS(SettingsRoute, SettingsRoute::class, R.string.nav_settings, Icons.Rounded.Settings),
}

val BottomBarHeight: Dp = 64.dp
private val AddButtonSize = 54.dp
private val AddButtonLift = 18.dp

/** Total vertical space the bar and its raised Add button take, including the system navigation inset. */
@Composable
fun bottomBarOccupiedHeight(): Dp = BottomBarHeight + navigationBarBottom() + AddButtonLift + 4.dp

@Composable
fun DuzupBottomBar(
    selected: TopLevel?,
    onSelect: (TopLevel) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DuzupTheme.colors
    val density = LocalDensity.current
    // The bar has a fixed height, so its labels scale up to 1.3x; everywhere else text scales fully.
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.3f))) {
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .neumorphic(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), 8.dp)
                .padding(bottom = navigationBarBottom())
                .height(BottomBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarItem(TopLevel.HOME, selected == TopLevel.HOME, onSelect, Modifier.weight(1f))
            BarItem(TopLevel.CALENDAR, selected == TopLevel.CALENDAR, onSelect, Modifier.weight(1f))
            Spacer(Modifier.width(AddButtonSize + 16.dp))
            BarItem(TopLevel.NOTES, selected == TopLevel.NOTES, onSelect, Modifier.weight(1f))
            BarItem(TopLevel.SETTINGS, selected == TopLevel.SETTINGS, onSelect, Modifier.weight(1f))
        }
        val addLabel = stringResource(R.string.add_bill)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = -AddButtonLift)
                .size(AddButtonSize)
                .shadow(10.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowDark)
                .clip(CircleShape)
                .background(colors.buttonPrimary)
                .border(1.5.dp, cyanEdgeBrush(), CircleShape)
                .clickable(role = Role.Button, onClickLabel = addLabel, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = addLabel, tint = colors.onButtonPrimary, modifier = Modifier.size(26.dp))
        }
    }
    }
}

@Composable
private fun BarItem(item: TopLevel, selected: Boolean, onSelect: (TopLevel) -> Unit, modifier: Modifier) {
    val colors = DuzupTheme.colors
    val label = stringResource(item.label)
    val tint = if (selected) colors.textPrimary else colors.textMuted
    Column(
        modifier
            .height(BottomBarHeight)
            .clickable(role = Role.Tab) { onSelect(item) }
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(width = 16.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) colors.accent else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}
