package com.duzup.app.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose routes. */
@Serializable data object OnboardingRoute
@Serializable data object HomeRoute
@Serializable data object CalendarRoute
@Serializable data object NotesRoute
@Serializable data object SettingsRoute
@Serializable data object StatsRoute

/**
 * [kind] is an [com.duzup.app.core.model.ItemKind] name. [quick] is the raw quick-add line ("call mom tomorrow 6pm"),
 * which the form parses to prefill itself; the user always reviews before saving.
 */
@Serializable data class AddEditRoute(
    val itemId: String? = null,
    val kind: String = "BILL",
    val quick: String? = null,
)

@Serializable data class DetailRoute(val itemId: String)
@Serializable data object AllItemsRoute
@Serializable data object RemindersRoute
