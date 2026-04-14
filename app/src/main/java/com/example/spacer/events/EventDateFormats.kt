package com.example.spacer.events

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val dateOnlyFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

fun formatEventDateNoTime(isoStartsAt: String): String {
    return try {
        val odt = OffsetDateTime.parse(isoStartsAt)
        odt.format(dateOnlyFormatter)
    } catch (_: DateTimeParseException) {
        isoStartsAt
    }
}
