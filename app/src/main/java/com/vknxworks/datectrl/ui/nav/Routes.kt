package com.vknxworks.datectrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object MonthRoute
@Serializable data object AgendaRoute
@Serializable data object CalendarsRoute

// Opened from the profile picture: the You page, and Settings from there
@Serializable data object YouRoute
@Serializable data object SettingsRoute

// Pushed screens
/** One event; [begin]/[end] pick the occurrence of a repeating one (0: the first). */
@Serializable data class EventRoute(val id: Long, val begin: Long = 0L, val end: Long = 0L)

/**
 * Adding (id -1) or changing an event. [begin] is the occurrence being changed; [day] (epoch day) the day a new
 * event goes on; [prefill] takes the fields another app filled in.
 */
/** Importing the .ics file at [uri]. */
@Serializable data class ImportRoute(val uri: String)

/** Saving events as an .ics file; [calendarId] is picked first (-1: the first calendar). */
@Serializable data class ExportRoute(val calendarId: Long = -1L)

@Serializable data class EditRoute(val id: Long = -1L, val begin: Long = 0L, val day: Long = Long.MIN_VALUE, val prefill: Boolean = false)
