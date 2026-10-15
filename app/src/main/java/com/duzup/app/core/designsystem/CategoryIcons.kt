package com.duzup.app.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/** One consistent icon family (Material Rounded) for every category, built-in or custom. */
object CategoryIcons {
    private val icons: LinkedHashMap<String, ImageVector> = linkedMapOf(
        "bolt" to Icons.Rounded.Bolt,
        "wifi" to Icons.Rounded.Wifi,
        "card" to Icons.Rounded.CreditCard,
        "home" to Icons.Rounded.Home,
        "loan" to Icons.Rounded.AccountBalance,
        "shield" to Icons.Rounded.Shield,
        "school" to Icons.Rounded.School,
        "car" to Icons.Rounded.DirectionsCar,
        "phone" to Icons.Rounded.PhoneAndroid,
        "water" to Icons.Rounded.WaterDrop,
        "fire" to Icons.Rounded.LocalFireDepartment,
        "tv" to Icons.Rounded.Tv,
        "music" to Icons.Rounded.MusicNote,
        "cart" to Icons.Rounded.ShoppingCart,
        "fitness" to Icons.Rounded.FitnessCenter,
        "flight" to Icons.Rounded.Flight,
        "pets" to Icons.Rounded.Pets,
        "gift" to Icons.Rounded.CardGiftcard,
        "build" to Icons.Rounded.Build,
        "cloud" to Icons.Rounded.Cloud,
        "game" to Icons.Rounded.SportsEsports,
        "book" to Icons.Rounded.MenuBook,
        "more" to Icons.Rounded.MoreHoriz,
        // Item kinds (not offered when creating a custom category)
        "event" to Icons.Rounded.Event,
        "task" to Icons.Rounded.TaskAlt,
        "note" to Icons.Rounded.StickyNote2,
        "cake" to Icons.Rounded.Cake,
        "routine" to Icons.Rounded.Autorenew,
    )

    /** Keys offered when creating a custom category. */
    val pickable: List<String> = icons.keys.filter { it !in setOf("more", "event", "task", "note", "cake", "routine") }

    fun get(key: String): ImageVector = icons[key] ?: Icons.Rounded.MoreHoriz
}

/** Accent options for custom categories (ARGB). Chosen to stay distinct from the status colors. */
object CategoryColors {
    val options: List<Long> = listOf(
        0xFF1E6FD9, 0xFF00A3BF, 0xFF6B4FD8, 0xFFB5348F, 0xFF3D8B5A, 0xFF7A5C2E, 0xFF4C6274, 0xFF203345,
    )
}
