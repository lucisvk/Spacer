package com.example.spacer.events

import com.example.spacer.network.SupabaseManager
import com.example.spacer.profile.EventRow
import com.example.spacer.profile.ProfileRepository
import com.example.spacer.profile.ProfileRow
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
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
    val location: String? = null,
    val visibility: String = "public",
    val category: String? = null
)

@Serializable
private data class CancelHostedEventRpc(
    @SerialName("p_event_id") val pEventId: String
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
private data class PublicEventInviteRow(
    val id: String? = null,
    @SerialName("event_id") val eventId: String
)

@Serializable
private data class PublicListingInsert(
    @SerialName("event_id") val eventId: String
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

/** Event the current user hosts or has accepted an invite to; shown on the Events hub. */
data class MyEventHubItem(
    val event: EventRow,
    val isHosting: Boolean
)

/** [invitesSent] may be lower than requested when some IDs are not real auth users. */
data class CreateEventOutcome(
    val eventId: String,
    val invitesRequested: Int,
    val invitesSent: Int
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
        inviteeIds: List<String>,
        visibility: String = "public",
        category: String? = null
    ): Result<CreateEventOutcome> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val vis = visibility.trim().lowercase().let { v ->
                if (v == "invite_only") "invite_only" else "public"
            }
            val eventId = UUID.randomUUID().toString()
            supabase.from("app_events").insert(
                AppEventInsert(
                    id = eventId,
                    title = title.trim(),
                    description = description?.trim()?.ifBlank { null },
                    hostId = user.id,
                    startsAt = startsAtIso,
                    endsAt = endsAtIso?.ifBlank { null },
                    location = locationLabel?.ifBlank { null },
                    visibility = vis,
                    category = category?.trim()?.ifBlank { null }
                )
            )
            // event_invites.invitee_id → auth.users(id). Offline demo profiles, typos, or stale
            // search hits are not valid; insert each invite separately so one bad row does not fail the event.
            val distinct = inviteeIds.distinct()
                .filter { it != user.id }
                .mapNotNull { id -> runCatching { UUID.fromString(id).toString() }.getOrNull() }
            var invitesSent = 0
            distinct.forEach { inviteeId ->
                if (ProfileRepository.isOfflineDemoProfile(inviteeId)) {
                    // Demo users are local-only; count as sent so demo flows feel real.
                    invitesSent++
                    return@forEach
                }
                val ok = runCatching {
                    supabase.from("event_invites").insert(
                        EventInviteInsert(
                            id = UUID.randomUUID().toString(),
                            eventId = eventId,
                            inviteeId = inviteeId,
                            status = "pending"
                        )
                    )
                }.isSuccess
                if (ok) invitesSent++
            }
            if (vis == "public") {
                registerPublicEventListing(eventId)
            }
            Result.success(
                CreateEventOutcome(
                    eventId = eventId,
                    invitesRequested = distinct.size,
                    invitesSent = invitesSent
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** New events are discoverable in “Public events” once [public.public_event_invites] exists (see database SQL). */
    private suspend fun registerPublicEventListing(eventId: String) {
        runCatching {
            supabase.from("public_event_invites").insert(PublicListingInsert(eventId = eventId))
        }
    }

    /**
     * Cancels a hosted event, notifies invitees (via [user_notifications] when DB migration is applied),
     * and deletes the event row (cascades invites).
     */
    suspend fun cancelHostedEvent(eventId: String): Result<Unit> {
        return try {
            supabase.postgrest.rpc("cancel_hosted_event", CancelHostedEventRpc(pEventId = eventId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Upcoming discoverable events for Home (public visibility only, soonest first). */
    suspend fun listUpcomingDiscoverableEvents(limit: Int = 24): Result<List<EventRow>> {
        return listPublicDiscoverableEvents().map { list ->
            val now = OffsetDateTime.now()
            list
                .filter { e ->
                    val d = parseDate(e.startsAt)
                    d != null && d.isAfter(now)
                }
                .take(limit)
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
            Result.success(out.sortedByDescending { parseDate(it.startsAt) ?: OffsetDateTime.MIN })
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

    /** All events you host plus accepted invites (excluding host duplicates), newest first. */
    suspend fun listMyHostingAndAttendingEvents(): Result<List<MyEventHubItem>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))

            val hosted = supabase.from("app_events")
                .select {
                    filter { eq("host_id", user.id) }
                    order(column = "starts_at", order = Order.DESCENDING)
                }
                .decodeList<EventRow>()

            val acceptedInvites = supabase.from("event_invites")
                .select {
                    filter {
                        eq("invitee_id", user.id)
                        eq("status", "accepted")
                    }
                }
                .decodeList<EventInviteRow>()

            val hostedIds = hosted.map { it.id }.toSet()
            val attendingOnly = acceptedInvites.mapNotNull { inv ->
                runCatching {
                    supabase.from("app_events")
                        .select {
                            filter { eq("id", inv.eventId) }
                            limit(1)
                        }
                        .decodeSingle<EventRow>()
                }.getOrNull()
            }.filter { it.id !in hostedIds }

            val items = hosted.map { MyEventHubItem(event = it, isHosting = true) } +
                attendingOnly.map { MyEventHubItem(event = it, isHosting = false) }
            val sorted = items.sortedByDescending { parseDate(it.event.startsAt) ?: OffsetDateTime.MIN }
            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Events listed in [public_event_invites], excluding ones you host (you already see those). */
    suspend fun listPublicDiscoverableEvents(): Result<List<EventRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val rows = supabase.from("public_event_invites")
                .select { }
                .decodeList<PublicEventInviteRow>()
            val events = rows.mapNotNull { row ->
                runCatching {
                    supabase.from("app_events")
                        .select {
                            filter { eq("id", row.eventId) }
                            limit(1)
                        }
                        .decodeSingle<EventRow>()
                }.getOrNull()
            }
                .filter { it.hostId != user.id }
                .filter { it.visibility != "invite_only" }
                .distinctBy { it.id }
                .sortedByDescending { parseDate(it.startsAt) ?: OffsetDateTime.MIN }
            if (events.isNotEmpty()) Result.success(events)
            else Result.success(fallbackPublicEvents(excludeHostId = user.id))
        } catch (_: Exception) {
            val userId = supabase.auth.currentUserOrNull()?.id
            Result.success(fallbackPublicEvents(excludeHostId = userId))
        }
    }

    /** Home feed: your hosted/attending events + pending invites + public events, newest first. */
    suspend fun listHomeFeedEvents(limit: Int = 24): Result<List<EventRow>> {
        return try {
            val myEvents = listMyHostingAndAttendingEvents().getOrDefault(emptyList()).map { it.event }
            val pendingIds = listPendingInvites().getOrDefault(emptyList()).map { it.eventId }.distinct()
            val pendingEvents = pendingIds.mapNotNull { id -> getEvent(id).getOrNull() }
            val publicEvents = listPublicDiscoverableEvents().getOrDefault(emptyList())

            val merged = (myEvents + pendingEvents + publicEvents)
                .distinctBy { it.id }
                .sortedByDescending { parseDate(it.startsAt) ?: OffsetDateTime.MIN }
                .take(limit)
            Result.success(merged)
        } catch (_: Exception) {
            val userId = supabase.auth.currentUserOrNull()?.id
            Result.success(
                fallbackPublicEvents(excludeHostId = userId)
                    .sortedByDescending { parseDate(it.startsAt) ?: OffsetDateTime.MIN }
                    .take(limit)
            )
        }
    }

    private fun fallbackPublicEvents(excludeHostId: String?): List<EventRow> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val demo = listOf(
            Triple("Sunset Rooftop Mixer", "230 Fifth Rooftop Bar, New York, NY", "Social"),
            Triple("Morning Run + Coffee", "Central Park - Columbus Circle, New York, NY", "Fitness"),
            Triple("Board Game Night", "The Uncommons, Manhattan, NY", "Games"),
            Triple("Food Hall Meetup", "Chelsea Market, New York, NY", "Food"),
            Triple("Live Jazz Hang", "Blue Note Jazz Club, Greenwich Village, NY", "Music"),
            Triple("Gallery Walk", "The Metropolitan Museum of Art, New York, NY", "Arts")
        )
        return demo.mapIndexed { index, (title, location, category) ->
            val day = today.plusDays((index + 1).toLong())
            val hour = if (index % 2 == 0) 19 else 10
            val start = day.atTime(LocalTime.of(hour, 0)).atZone(zone).toOffsetDateTime().toString()
            val end = day.atTime(LocalTime.of(hour + 2, 0)).atZone(zone).toOffsetDateTime().toString()
            EventRow(
                id = "demo-public-event-${index + 1}",
                title = title,
                description = "Open community meetup at a real spot. Come by, meet people, and have fun.",
                hostId = "demo-host-${index + 1}",
                startsAt = start,
                endsAt = end,
                location = location,
                visibility = "public",
                category = category
            )
        }
            .filter { it.hostId != excludeHostId }
            .sortedByDescending { parseDate(it.startsAt) ?: OffsetDateTime.MIN }
    }
}
