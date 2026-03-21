package com.example.spacer.events

import com.example.spacer.network.SupabaseManager
import com.example.spacer.profile.EventRow
import com.example.spacer.profile.ProfileRow
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID

@Serializable
private data class AppEventInsert(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("host_id") val hostId: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String? = null,
    val location: String? = null
)

@Serializable
private data class EventInviteInsert(
    val id: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("invitee_id") val inviteeId: String,
    val status: String = "pending"
)

@Serializable
private data class EventInviteRow(
    val id: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("invitee_id") val inviteeId: String,
    val status: String
)

@Serializable
private data class EventAvailabilityUpsert(
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("preset_slots") val presetSlots: String,
    val notes: String? = null
)

@Serializable
private data class AvailabilityRow(
    @SerialName("user_id") val userId: String,
    @SerialName("preset_slots") val presetSlots: String,
    val notes: String? = null
)

data class PendingInviteUi(
    val inviteId: String,
    val eventId: String,
    val title: String,
    val startsAt: String,
    val location: String?,
    val hostDisplayName: String
)

data class AvailabilityEntryUi(
    val userId: String,
    val displayName: String,
    val presetSlots: String,
    val notes: String?
)

class EventRepository {
    private val supabase = SupabaseManager.client

    private fun parseDate(value: String): OffsetDateTime? = try {
        OffsetDateTime.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    suspend fun createEventWithInvites(
        title: String,
        description: String?,
        startsAtIso: String,
        endsAtIso: String?,
        locationLabel: String?,
        inviteeIds: List<String>
    ): Result<String> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val eventId = UUID.randomUUID().toString()
            supabase.from("app_events").insert(
                AppEventInsert(
                    id = eventId,
                    title = title.trim(),
                    description = description?.trim()?.ifBlank { null },
                    hostId = user.id,
                    startsAt = startsAtIso,
                    endsAt = endsAtIso?.ifBlank { null },
                    location = locationLabel?.ifBlank { null }
                )
            )
            val distinct = inviteeIds.distinct().filter { it != user.id }
            distinct.forEach { inviteeId ->
                supabase.from("event_invites").insert(
                    EventInviteInsert(
                        id = UUID.randomUUID().toString(),
                        eventId = eventId,
                        inviteeId = inviteeId,
                        status = "pending"
                    )
                )
            }
            Result.success(eventId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listPendingInvites(): Result<List<PendingInviteUi>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val invites = supabase.from("event_invites")
                .select {
                    filter {
                        eq("invitee_id", user.id)
                        eq("status", "pending")
                    }
                }
                .decodeList<EventInviteRow>()
            val out = invites.mapNotNull { inv ->
                val event = runCatching {
                    supabase.from("app_events")
                        .select {
                            filter { eq("id", inv.eventId) }
                            limit(1)
                        }
                        .decodeSingle<EventRow>()
                }.getOrNull() ?: return@mapNotNull null
                val host = runCatching {
                    supabase.from("profiles")
                        .select {
                            filter { eq("id", event.hostId) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                }.getOrNull()
                val hostName = host?.fullName?.ifBlank { null } ?: host?.username ?: "Host"
                PendingInviteUi(
                    inviteId = inv.id,
                    eventId = inv.eventId,
                    title = event.title,
                    startsAt = event.startsAt,
                    location = event.location,
                    hostDisplayName = hostName
                )
            }
            Result.success(out.sortedBy { it.startsAt })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEvent(eventId: String): Result<EventRow> {
        return try {
            val row = supabase.from("app_events")
                .select {
                    filter { eq("id", eventId) }
                    limit(1)
                }
                .decodeSingle<EventRow>()
            Result.success(row)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getInviteStatusForEvent(eventId: String): Result<String?> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val rows = supabase.from("event_invites")
                .select {
                    filter {
                        eq("event_id", eventId)
                        eq("invitee_id", user.id)
                    }
                    limit(1)
                }
                .decodeList<EventInviteRow>()
            Result.success(rows.firstOrNull()?.status)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun respondToInvite(eventId: String, accept: Boolean): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val status = if (accept) "accepted" else "declined"
            supabase.from("event_invites").update(
                {
                    set("status", status)
                }
            ) {
                filter {
                    eq("event_id", eventId)
                    eq("invitee_id", user.id)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitAvailability(
        eventId: String,
        presetKeys: Set<String>,
        notes: String?
    ): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val preset = presetKeys.joinToString(",")
            val payload = EventAvailabilityUpsert(
                eventId = eventId,
                userId = user.id,
                presetSlots = preset,
                notes = notes?.trim()?.ifBlank { null }
            )
            try {
                supabase.from("event_availability").insert(payload)
            } catch (_: Exception) {
                supabase.from("event_availability").update(
                    {
                        set("preset_slots", preset)
                        set("notes", notes?.trim()?.ifBlank { null })
                    }
                ) {
                    filter {
                        eq("event_id", eventId)
                        eq("user_id", user.id)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listAvailabilityForHost(eventId: String): Result<List<AvailabilityEntryUi>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val event = supabase.from("app_events")
                .select {
                    filter { eq("id", eventId) }
                    limit(1)
                }
                .decodeSingle<EventRow>()
            if (event.hostId != user.id) {
                return Result.failure(IllegalStateException("Only the host can view availability"))
            }
            val rows = supabase.from("event_availability")
                .select {
                    filter { eq("event_id", eventId) }
                }
                .decodeList<AvailabilityRow>()
            val list = rows.mapNotNull { r ->
                val p = runCatching {
                    supabase.from("profiles")
                        .select {
                            filter { eq("id", r.userId) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                }.getOrNull()
                val name = p?.fullName?.ifBlank { null } ?: p?.username ?: r.userId.take(8)
                AvailabilityEntryUi(
                    userId = r.userId,
                    displayName = name,
                    presetSlots = r.presetSlots,
                    notes = r.notes
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listUpcomingHostedEvents(): Result<List<EventRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val now = OffsetDateTime.now()
            val events = supabase.from("app_events")
                .select {
                    filter { eq("host_id", user.id) }
                    order(column = "starts_at", order = Order.ASCENDING)
                }
                .decodeList<EventRow>()
                .filter { e ->
                    parseDate(e.startsAt)?.isAfter(now) == true
                }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
