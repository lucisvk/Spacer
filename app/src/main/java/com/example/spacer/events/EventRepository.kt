package com.example.spacer.events

import com.example.spacer.network.SupabaseManager
import com.example.spacer.profile.EventRow
import com.example.spacer.profile.FriendListItem
import com.example.spacer.profile.ProfileRepository
import com.example.spacer.profile.ProfileRow
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    val location: String? = null,
    val visibility: String = "public",
    val category: String? = null,
    @SerialName("bring_items") val bringItems: String? = null
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
private data class UserBlockRow(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String
)

@Serializable
private data class EventAvailabilityUpsert(
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("preset_slots") val presetSlots: String,
    val notes: String? = null
)

@Serializable
private data class EventAvailabilityUpsertWithCalendar(
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("preset_slots") val presetSlots: String,
    val notes: String? = null,
    @SerialName("calendar_busy_overlaps_event") val calendarBusyOverlapsEvent: Boolean
)

@Serializable
private data class AvailabilityRow(
    @SerialName("user_id") val userId: String,
    @SerialName("preset_slots") val presetSlots: String,
    val notes: String? = null,
    @SerialName("calendar_busy_overlaps_event") val calendarBusyOverlapsEvent: Boolean? = null
)

data class PendingInviteUi(
    val inviteId: String,
    val eventId: String,
    val title: String,
    val startsAt: String,
    val endsAt: String?,
    val location: String?,
    val hostDisplayName: String
)

data class AvailabilityEntryUi(
    val userId: String,
    val displayName: String,
    val presetSlots: String,
    val notes: String?,
    val calendarBusyOverlapsEvent: Boolean = false
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

@Serializable
private data class EventMemberRow(
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    val role: String = "attendee",
    val status: String = "active"
)

@Serializable
private data class EventMemberInsert(
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    val status: String = "active",
    @SerialName("added_by") val addedBy: String? = null
)

@Serializable
private data class EventChatRoomRow(
    val id: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("chat_mode") val chatMode: String = "all_members"
)

@Serializable
private data class EventChatRoomInsert(
    @SerialName("event_id") val eventId: String,
    @SerialName("chat_mode") val chatMode: String = "all_members",
    @SerialName("created_by") val createdBy: String
)

@Serializable
private data class EventChatMessageRow(
    val id: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class EventChatMessageInsert(
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String
)

data class EventChatMessageUi(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderRole: String,
    val body: String,
    val createdAt: String
)

@Serializable
private data class DmConversationRow(
    val id: String,
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String,
    @SerialName("last_message_at") val lastMessageAt: String? = null
)

@Serializable
private data class DmConversationInsert(
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String
)

@Serializable
private data class DmMessageRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class DmMessageInsert(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String
)

@Serializable
private data class RealtimeChatBroadcastPayload(
    val eventName: String? = null
)

data class DmThreadUi(
    val conversationId: String,
    val peerId: String,
    val peerName: String,
    val peerAvatarUrl: String?,
    val lastMessageAt: String?,
    val lastMessagePreview: String? = null,
    val lastMessageSenderName: String? = null
)

data class DmMessageUi(
    val id: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val createdAt: String
)

data class ChatPresenceUi(
    val userId: String,
    val displayName: String,
    val role: String
)

@Serializable
private data class EventBringItemClaimRow(
    @SerialName("event_id") val eventId: String,
    @SerialName("item_key") val itemKey: String,
    @SerialName("item_label") val itemLabel: String,
    @SerialName("claimed_by") val claimedBy: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class EventBringItemClaimInsert(
    @SerialName("event_id") val eventId: String,
    @SerialName("item_key") val itemKey: String,
    @SerialName("item_label") val itemLabel: String,
    @SerialName("claimed_by") val claimedBy: String
)

data class BringItemClaimUi(
    val itemKey: String,
    val itemLabel: String,
    val claimedByUserId: String,
    val claimedByName: String
)

private object LocalDemoChatStore {
    private const val DEMO_EVENT_ID = "demo-local-event-1"
    private const val DEMO_HOST_ID = "demo-host-local"
    private var startedAt: String = OffsetDateTime.now().plusHours(2).withNano(0).toString()
    private var endedAt: String = OffsetDateTime.now().plusHours(4).withNano(0).toString()
    private var bringItems: String = "Water bottle, notebook"

    fun demoEvent(): EventRow = EventRow(
        id = DEMO_EVENT_ID,
        title = "Demo Event Chat Sandbox",
        description = "Local fallback event to test chat, co-host tools, and presence when API is unavailable.",
        hostId = DEMO_HOST_ID,
        startsAt = startedAt,
        endsAt = endedAt,
        location = "Times Square, New York, NY 10036",
        bringItems = bringItems,
        visibility = "public",
        category = "Social"
    )

    private val eventMessagesByEventId = mutableMapOf(
        DEMO_EVENT_ID to mutableListOf(
            EventChatMessageUi(
                id = "demo-msg-1",
                senderId = DEMO_HOST_ID,
                senderName = "Demo Host",
                senderRole = "host",
                body = "Welcome! This is the offline demo event chat.",
                createdAt = OffsetDateTime.now().minusMinutes(5).withNano(0).toString()
            )
        )
    )
    private val eventChatModeByEventId = mutableMapOf(DEMO_EVENT_ID to "all_members")
    private val eventPresenceByEventId = mutableMapOf(
        DEMO_EVENT_ID to mutableSetOf(DEMO_HOST_ID, "demo-cohost-local")
    )
    private val cohostsByEventId = mutableMapOf(
        DEMO_EVENT_ID to mutableListOf(
            FriendListItem(
                id = "demo-cohost-local",
                fullName = "Demo Co-host",
                username = "demo_cohost",
                avatarUrl = null,
                presenceStatus = "online"
            )
        )
    )
    private val dmMessagesByConversationId = mutableMapOf<String, MutableList<DmMessageUi>>()
    private val bringClaimsByEventId = mutableMapOf(
        DEMO_EVENT_ID to mutableMapOf("water bottle" to "demo-cohost-local")
    )

    fun isDemoEvent(eventId: String): Boolean = eventId == DEMO_EVENT_ID

    fun updateDemoEventCoreDetails(startsAtIso: String, endsAtIso: String?, bringItemsValue: String?) {
        startedAt = startsAtIso
        endedAt = endsAtIso ?: OffsetDateTime.now().plusHours(4).withNano(0).toString()
        bringItems = bringItemsValue?.trim()?.ifBlank { "Water bottle, notebook" } ?: "Water bottle, notebook"
    }

    fun chatMode(eventId: String): String = eventChatModeByEventId[eventId] ?: "all_members"

    fun setChatMode(eventId: String, mode: String) {
        eventChatModeByEventId[eventId] = mode
    }

    fun listEventMessages(eventId: String): List<EventChatMessageUi> =
        eventMessagesByEventId[eventId]?.toList() ?: emptyList()

    fun appendEventMessage(eventId: String, message: EventChatMessageUi) {
        val list = eventMessagesByEventId.getOrPut(eventId) { mutableListOf() }
        list.add(message)
    }

    fun markPresent(eventId: String, userId: String) {
        eventPresenceByEventId.getOrPut(eventId) { mutableSetOf() }.add(userId)
    }

    fun listPresence(eventId: String): Set<String> =
        eventPresenceByEventId[eventId]?.toSet() ?: emptySet()

    fun listCohosts(eventId: String): List<FriendListItem> =
        cohostsByEventId[eventId]?.toList() ?: emptyList()

    fun addCohost(eventId: String, item: FriendListItem) {
        cohostsByEventId.getOrPut(eventId) { mutableListOf() }.add(item)
    }

    fun removeCohost(eventId: String, userId: String) {
        cohostsByEventId[eventId]?.removeAll { it.id == userId }
    }

    fun dmConversationIdForPair(a: String, b: String): String {
        val first = minOf(a, b)
        val second = maxOf(a, b)
        return "demo-dm-$first-$second"
    }

    fun listDmMessages(conversationId: String): List<DmMessageUi> =
        dmMessagesByConversationId[conversationId]?.toList() ?: emptyList()

    fun appendDmMessage(conversationId: String, message: DmMessageUi) {
        val list = dmMessagesByConversationId.getOrPut(conversationId) { mutableListOf() }
        list.add(message)
    }

    fun bringClaims(eventId: String): Map<String, String> =
        bringClaimsByEventId[eventId]?.toMap() ?: emptyMap()

    fun setBringClaim(eventId: String, itemKey: String, userId: String?) {
        val map = bringClaimsByEventId.getOrPut(eventId) { mutableMapOf() }
        if (userId == null) map.remove(itemKey) else map[itemKey] = userId
    }
}

class EventRepository {
    private val supabase = SupabaseManager.client
    private val notificationsRepo = NotificationsRepository()

    private fun parseDate(value: String): OffsetDateTime? = try {
        OffsetDateTime.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun displayName(profile: ProfileRow?, fallback: String = "User"): String {
        if (profile == null) return fallback
        return profile.fullName?.ifBlank { profile.username ?: fallback } ?: (profile.username ?: fallback)
    }

    private suspend fun currentUserIdOrDemo(): String {
        return supabase.auth.currentUserOrNull()?.id ?: "demo-me-local"
    }

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value); true }.getOrDefault(false)

    private fun normalizeBringItemKey(raw: String): String {
        return raw.trim().lowercase()
    }

    fun parseBringItems(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw
            .split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    fun encodeBringItems(items: List<String>): String? {
        val cleaned = items.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        return cleaned.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private suspend fun blockedUserIdsForCurrentUser(currentUserId: String): Set<String> {
        val blockedByMe = runCatching {
            supabase.from("user_blocks")
                .select {
                    filter { eq("blocker_id", currentUserId) }
                }
                .decodeList<UserBlockRow>()
                .map { it.blockedId }
        }.getOrDefault(emptyList())
        val blockedMe = runCatching {
            supabase.from("user_blocks")
                .select {
                    filter { eq("blocked_id", currentUserId) }
                }
                .decodeList<UserBlockRow>()
                .map { it.blockerId }
        }.getOrDefault(emptyList())
        return (blockedByMe + blockedMe).toSet()
    }

    suspend fun createEventWithInvites(
        title: String,
        description: String?,
        startsAtIso: String,
        endsAtIso: String?,
        locationLabel: String?,
        inviteeIds: List<String>,
        visibility: String = "public",
        category: String? = null,
        bringItems: String? = null,
        chatMode: String = "all_members"
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
                    category = category?.trim()?.ifBlank { null },
                    bringItems = bringItems?.trim()?.ifBlank { null }
                )
            )
            supabase.from("event_members").insert(
                EventMemberInsert(
                    eventId = eventId,
                    userId = user.id,
                    role = "host",
                    status = "active",
                    addedBy = user.id
                )
            )
            val normalizedChatMode = when (chatMode.trim().lowercase()) {
                "host_cohosts_only" -> "host_cohosts_only"
                "disabled" -> "disabled"
                else -> "all_members"
            }
            supabase.from("event_chat_rooms").insert(
                EventChatRoomInsert(
                    eventId = eventId,
                    chatMode = normalizedChatMode,
                    createdBy = user.id
                )
            )
            // Insert invites one-by-one so one invalid recipient does not fail event creation.
            val distinct = inviteeIds.distinct()
                .filter { it != user.id }
                .mapNotNull { id -> runCatching { UUID.fromString(id).toString() }.getOrNull() }
            var invitesSent = 0
            distinct.forEach { inviteeId ->
                if (ProfileRepository.isOfflineDemoProfile(inviteeId)) {
                    // Local demo users are not in auth.users; keep UX consistent in demo mode.
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
                if (ok) {
                    invitesSent++
                    runCatching {
                        supabase.from("event_members").insert(
                            EventMemberInsert(
                                eventId = eventId,
                                userId = inviteeId,
                                role = "attendee",
                                status = "active",
                                addedBy = user.id
                            )
                        )
                    }
                    runCatching {
                        notificationsRepo.createForUser(
                            userId = inviteeId,
                            title = "New event invite",
                            body = "You were invited to \"$title\".",
                            deepLink = NotificationsRepository.DeepLinks.eventInvite(eventId)
                        )
                    }
                }
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
            val event = runCatching { getEvent(eventId).getOrThrow() }.getOrNull()
            val invitees = runCatching {
                supabase.from("event_invites")
                    .select {
                        filter { eq("event_id", eventId) }
                    }
                    .decodeList<EventInviteRow>()
                    .map { it.inviteeId }
                    .distinct()
            }.getOrDefault(emptyList())
            invitees.forEach { inviteeId ->
                runCatching {
                    notificationsRepo.createForUser(
                        userId = inviteeId,
                        title = "Event canceled",
                        body = "The event \"${event?.title ?: "an event"}\" was canceled by the host.",
                        deepLink = NotificationsRepository.DeepLinks.eventsHub()
                    )
                }
            }
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
            val blockedIds = blockedUserIdsForCurrentUser(user.id)
            val out = invites.mapNotNull { inv ->
                val event = runCatching {
                    supabase.from("app_events")
                        .select {
                            filter { eq("id", inv.eventId) }
                            limit(1)
                        }
                        .decodeSingle<EventRow>()
                }.getOrNull() ?: return@mapNotNull null
                if (event.hostId in blockedIds) return@mapNotNull null
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
                    endsAt = event.endsAt,
                    location = event.location,
                    hostDisplayName = hostName
                )
            }
            Result.success(out.sortedByDescending { parseDate(it.startsAt) ?: OffsetDateTime.MIN })
        } catch (_: Exception) {
            // Keep the hub usable when invites cannot be fetched.
            Result.success(emptyList())
        }
    }

    suspend fun getEvent(eventId: String): Result<EventRow> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                return Result.success(LocalDemoChatStore.demoEvent())
            }
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val row = supabase.from("app_events")
                .select {
                    filter { eq("id", eventId) }
                    limit(1)
                }
                .decodeSingle<EventRow>()
            if (row.hostId != user.id) {
                val blockedIds = blockedUserIdsForCurrentUser(user.id)
                if (row.hostId in blockedIds) {
                    return Result.failure(IllegalStateException("Event unavailable."))
                }
            }
            Result.success(row)
        } catch (e: Exception) {
            if (LocalDemoChatStore.isDemoEvent(eventId)) Result.success(LocalDemoChatStore.demoEvent()) else Result.failure(e)
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
        notes: String?,
        calendarBusyOverlapsEvent: Boolean = false
    ): Result<Unit> {
        runCatching {
            submitAvailabilityWithCalendarColumn(eventId, presetKeys, notes, calendarBusyOverlapsEvent)
        }.onSuccess {
            return Result.success(Unit)
        }
        return submitAvailabilityLegacy(eventId, presetKeys, notes)
    }

    private suspend fun submitAvailabilityWithCalendarColumn(
        eventId: String,
        presetKeys: Set<String>,
        notes: String?,
        calendarBusyOverlapsEvent: Boolean
    ) {
        val user = supabase.auth.currentUserOrNull()
            ?: throw IllegalStateException("Not logged in")
        val preset = presetKeys.joinToString(",")
        val trimmedNotes = notes?.trim()?.ifBlank { null }
        val payload = EventAvailabilityUpsertWithCalendar(
            eventId = eventId,
            userId = user.id,
            presetSlots = preset,
            notes = trimmedNotes,
            calendarBusyOverlapsEvent = calendarBusyOverlapsEvent
        )
        try {
            supabase.from("event_availability").insert(payload)
        } catch (_: Exception) {
            supabase.from("event_availability").update(
                {
                    set("preset_slots", preset)
                    set("notes", trimmedNotes)
                    set("calendar_busy_overlaps_event", calendarBusyOverlapsEvent)
                }
            ) {
                filter {
                    eq("event_id", eventId)
                    eq("user_id", user.id)
                }
            }
        }
    }

    private suspend fun submitAvailabilityLegacy(
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
                    notes = r.notes,
                    calendarBusyOverlapsEvent = r.calendarBusyOverlapsEvent == true
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateEventCoreDetails(
        eventId: String,
        startsAtIso: String,
        endsAtIso: String?,
        bringItems: String?
    ): Result<Unit> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                LocalDemoChatStore.updateDemoEventCoreDetails(startsAtIso, endsAtIso, bringItems)
                return Result.success(Unit)
            }
            val actorId = supabase.auth.currentUserOrNull()?.id
            supabase.from("app_events").update(
                {
                    set("starts_at", startsAtIso)
                    set("ends_at", endsAtIso?.ifBlank { null })
                    set("bring_items", bringItems?.trim()?.ifBlank { null })
                }
            ) {
                filter { eq("id", eventId) }
            }
            runCatching {
                val event = supabase.from("app_events")
                    .select {
                        filter { eq("id", eventId) }
                        limit(1)
                    }
                    .decodeSingle<EventRow>()
                val members = supabase.from("event_members")
                    .select {
                        filter {
                            eq("event_id", eventId)
                            eq("status", "active")
                        }
                    }
                    .decodeList<EventMemberRow>()
                    .map { it.userId }
                    .distinct()
                    .filterNot { it == actorId }
                members.forEach { uid ->
                    notificationsRepo.createForUser(
                        userId = uid,
                        title = "Event details updated",
                        body = "\"${event.title}\" was updated. Check time/bring items.",
                        deepLink = NotificationsRepository.DeepLinks.eventInvite(eventId)
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listCohosts(eventId: String): Result<List<FriendListItem>> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                return Result.success(LocalDemoChatStore.listCohosts(eventId))
            }
            val members = supabase.from("event_members")
                .select {
                    filter {
                        eq("event_id", eventId)
                        eq("role", "cohost")
                        eq("status", "active")
                    }
                }
                .decodeList<EventMemberRow>()
            val rows = members.mapNotNull { member ->
                runCatching {
                    val p = supabase.from("profiles")
                        .select {
                            filter { eq("id", member.userId) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                    FriendListItem(
                        id = p.id,
                        fullName = displayName(p),
                        username = p.username ?: "user",
                        avatarUrl = p.avatarUrl,
                        presenceStatus = p.presenceStatus
                    )
                }.getOrNull()
            }
            Result.success(rows.sortedBy { it.fullName.lowercase() })
        } catch (e: Exception) {
            if (LocalDemoChatStore.isDemoEvent(eventId)) Result.success(LocalDemoChatStore.listCohosts(eventId)) else Result.failure(e)
        }
    }

    suspend fun addCohost(eventId: String, userId: String): Result<Unit> {
        return try {
            if (!LocalDemoChatStore.isDemoEvent(eventId) && !isUuid(userId)) {
                return Result.failure(IllegalArgumentException("Invalid user id for co-host"))
            }
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                LocalDemoChatStore.addCohost(
                    eventId,
                    FriendListItem(
                        id = userId,
                        fullName = "Demo Friend",
                        username = "demo_friend",
                        avatarUrl = null,
                        presenceStatus = "online"
                    )
                )
                return Result.success(Unit)
            }
            val actor = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            // Promote an existing member row before attempting insert.
            runCatching {
                supabase.from("event_members").update(
                    {
                        set("role", "cohost")
                        set("status", "active")
                        set("added_by", actor.id)
                    }
                ) {
                    filter {
                        eq("event_id", eventId)
                        eq("user_id", userId)
                    }
                }
            }
            runCatching {
                supabase.from("event_members").insert(
                    EventMemberInsert(
                        eventId = eventId,
                        userId = userId,
                        role = "cohost",
                        status = "active",
                        addedBy = actor.id
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeCohost(eventId: String, userId: String): Result<Unit> {
        return try {
            if (!LocalDemoChatStore.isDemoEvent(eventId) && !isUuid(userId)) {
                return Result.failure(IllegalArgumentException("Invalid user id for co-host"))
            }
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                LocalDemoChatStore.removeCohost(eventId, userId)
                return Result.success(Unit)
            }
            supabase.from("event_members").update(
                {
                    set("status", "removed")
                }
            ) {
                filter {
                    eq("event_id", eventId)
                    eq("user_id", userId)
                    eq("role", "cohost")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrCreateEventChatRoom(eventId: String): Result<String> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                return Result.success("demo-room-$eventId")
            }
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val existing = supabase.from("event_chat_rooms")
                .select {
                    filter { eq("event_id", eventId) }
                    limit(1)
                }
                .decodeList<EventChatRoomRow>()
                .firstOrNull()
            if (existing != null) return Result.success(existing.id)
            supabase.from("event_chat_rooms").insert(
                EventChatRoomInsert(eventId = eventId, createdBy = user.id)
            )
            val created = supabase.from("event_chat_rooms")
                .select {
                    filter { eq("event_id", eventId) }
                    limit(1)
                }
                .decodeSingle<EventChatRoomRow>()
            Result.success(created.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEventChatMode(eventId: String): Result<String> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                return Result.success(LocalDemoChatStore.chatMode(eventId))
            }
            val room = supabase.from("event_chat_rooms")
                .select {
                    filter { eq("event_id", eventId) }
                    limit(1)
                }
                .decodeList<EventChatRoomRow>()
                .firstOrNull()
            Result.success(room?.chatMode ?: "all_members")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setEventChatMode(eventId: String, mode: String): Result<Unit> {
        return try {
            val normalized = when (mode.trim().lowercase()) {
                "host_cohosts_only" -> "host_cohosts_only"
                "disabled" -> "disabled"
                else -> "all_members"
            }
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                LocalDemoChatStore.setChatMode(eventId, normalized)
                return Result.success(Unit)
            }
            supabase.from("event_chat_rooms").update(
                {
                    set("chat_mode", normalized)
                }
            ) {
                filter { eq("event_id", eventId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listEventChatMessages(eventId: String, limit: Int = 100): Result<List<EventChatMessageUi>> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                return Result.success(LocalDemoChatStore.listEventMessages(eventId).takeLast(limit))
            }
            val roomId = getOrCreateEventChatRoom(eventId).getOrThrow()
            val memberRows = runCatching {
                supabase.from("event_members")
                    .select {
                        filter { eq("event_id", eventId) }
                    }
                    .decodeList<EventMemberRow>()
            }.getOrDefault(emptyList())
            val roleByUser = memberRows.associateBy { it.userId }
            val rows = supabase.from("event_chat_messages")
                .select {
                    filter { eq("room_id", roomId) }
                    order(column = "created_at", order = Order.ASCENDING)
                    limit(limit.toLong())
                }
                .decodeList<EventChatMessageRow>()
            val out = rows.map { row ->
                val profile = runCatching {
                    supabase.from("profiles")
                        .select {
                            filter { eq("id", row.senderId) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                }.getOrNull()
                val name = displayName(profile)
                val role = roleByUser[row.senderId]?.role ?: "attendee"
                EventChatMessageUi(
                    id = row.id,
                    senderId = row.senderId,
                    senderName = name,
                    senderRole = role,
                    body = row.body,
                    createdAt = row.createdAt
                )
            }
            Result.success(out)
        } catch (e: Exception) {
            if (LocalDemoChatStore.isDemoEvent(eventId)) Result.success(LocalDemoChatStore.listEventMessages(eventId)) else Result.failure(e)
        }
    }

    fun subscribeEventChatMessages(eventId: String): Flow<Result<List<EventChatMessageUi>>> = flow {
        if (LocalDemoChatStore.isDemoEvent(eventId)) {
            while (true) {
                emit(Result.success(LocalDemoChatStore.listEventMessages(eventId)))
                delay(1200L)
            }
        }
        val roomId = getOrCreateEventChatRoom(eventId).getOrElse {
            emit(Result.failure(it))
            return@flow
        }
        emit(listEventChatMessages(eventId))
        val topic = "event_chat:$roomId"
        val channel = supabase.channel(topic)
        runCatching { channel.subscribe(blockUntilSubscribed = true) }
            .onFailure {
                emit(Result.failure(it))
                // Realtime fallback for clients that cannot subscribe successfully.
                while (true) {
                    emit(listEventChatMessages(eventId))
                    delay(2000L)
                }
            }
        channel.broadcastFlow<RealtimeChatBroadcastPayload>(event = "INSERT").collect {
            emit(listEventChatMessages(eventId))
        }
    }

    suspend fun sendEventChatMessage(eventId: String, body: String): Result<Unit> {
        return try {
            val userId = currentUserIdOrDemo()
            val message = body.trim()
            if (message.isBlank()) return Result.failure(IllegalArgumentException("Message cannot be empty"))
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                LocalDemoChatStore.appendEventMessage(
                    eventId,
                    EventChatMessageUi(
                        id = "demo-msg-${System.currentTimeMillis()}",
                        senderId = userId,
                        senderName = "You",
                        senderRole = "attendee",
                        body = message,
                        createdAt = OffsetDateTime.now().toString()
                    )
                )
                LocalDemoChatStore.markPresent(eventId, userId)
                return Result.success(Unit)
            }
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val roomId = getOrCreateEventChatRoom(eventId).getOrThrow()
            supabase.from("event_chat_messages").insert(
                EventChatMessageInsert(
                    roomId = roomId,
                    senderId = user.id,
                    body = message
                )
            )
            runCatching {
                val event = supabase.from("app_events")
                    .select {
                        filter { eq("id", eventId) }
                        limit(1)
                    }
                    .decodeSingle<EventRow>()
                val members = supabase.from("event_members")
                    .select {
                        filter {
                            eq("event_id", eventId)
                            eq("status", "active")
                        }
                    }
                    .decodeList<EventMemberRow>()
                    .map { it.userId }
                    .distinct()
                    .filterNot { it == user.id }
                members.forEach { uid ->
                    notificationsRepo.createForUser(
                        userId = uid,
                        title = "New event message",
                        body = "New message in \"${event.title}\".",
                        deepLink = NotificationsRepository.DeepLinks.eventChat(eventId)
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrCreateDmConversation(otherUserId: String): Result<String> {
        return try {
            val userId = currentUserIdOrDemo()
            if (userId == otherUserId) return Result.failure(IllegalArgumentException("Invalid peer"))
            if (userId.startsWith("demo-") || otherUserId.startsWith("demo-")) {
                return Result.success(LocalDemoChatStore.dmConversationIdForPair(userId, otherUserId))
            }
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            if (user.id == otherUserId) return Result.failure(IllegalArgumentException("Invalid peer"))
            val first = minOf(user.id, otherUserId)
            val second = maxOf(user.id, otherUserId)
            val existing = supabase.from("dm_conversations")
                .select {
                    filter {
                        eq("user_a", first)
                        eq("user_b", second)
                    }
                    limit(1)
                }
                .decodeList<DmConversationRow>()
                .firstOrNull()
            if (existing != null) return Result.success(existing.id)
            supabase.from("dm_conversations").insert(
                DmConversationInsert(
                    userA = first,
                    userB = second
                )
            )
            val created = supabase.from("dm_conversations")
                .select {
                    filter {
                        eq("user_a", first)
                        eq("user_b", second)
                    }
                    limit(1)
                }
                .decodeSingle<DmConversationRow>()
            Result.success(created.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listDmThreads(): Result<List<DmThreadUi>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val rows = supabase.from("dm_conversations")
                .select { }
                .decodeList<DmConversationRow>()
                .filter { user.id == it.userA || user.id == it.userB }
            val threads = rows.mapNotNull { row ->
                val peerId = if (row.userA == user.id) row.userB else row.userA
                val p = runCatching {
                    supabase.from("profiles")
                        .select {
                            filter { eq("id", peerId) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                }.getOrNull() ?: return@mapNotNull null
                val latestMessage = runCatching {
                    supabase.from("dm_messages")
                        .select {
                            filter { eq("conversation_id", row.id) }
                            order(column = "created_at", order = Order.DESCENDING)
                            limit(1)
                        }
                        .decodeList<DmMessageRow>()
                        .firstOrNull()
                }.getOrNull()
                val latestSenderName = latestMessage?.let { latest ->
                    runCatching {
                        supabase.from("profiles")
                            .select {
                                filter { eq("id", latest.senderId) }
                                limit(1)
                            }
                            .decodeSingle<ProfileRow>()
                    }.getOrNull()?.let { sender ->
                        displayName(sender)
                    }
                }
                DmThreadUi(
                    conversationId = row.id,
                    peerId = peerId,
                    peerName = displayName(p),
                    peerAvatarUrl = p.avatarUrl,
                    lastMessageAt = row.lastMessageAt,
                    lastMessagePreview = latestMessage?.body,
                    lastMessageSenderName = latestSenderName
                )
            }.sortedByDescending { it.lastMessageAt ?: "" }
            Result.success(threads)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listDmMessages(conversationId: String, limit: Int = 200): Result<List<DmMessageUi>> {
        return try {
            if (conversationId.startsWith("demo-dm-")) {
                return Result.success(LocalDemoChatStore.listDmMessages(conversationId).takeLast(limit))
            }
            val rows = supabase.from("dm_messages")
                .select {
                    filter { eq("conversation_id", conversationId) }
                    order(column = "created_at", order = Order.ASCENDING)
                    limit(limit.toLong())
                }
                .decodeList<DmMessageRow>()
            val out = rows.map { row ->
                val p = runCatching {
                    supabase.from("profiles")
                        .select {
                            filter { eq("id", row.senderId) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                }.getOrNull()
                DmMessageUi(
                    id = row.id,
                    senderId = row.senderId,
                    senderName = displayName(p),
                    body = row.body,
                    createdAt = row.createdAt
                )
            }
            Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun subscribeDmMessages(conversationId: String): Flow<Result<List<DmMessageUi>>> = flow {
        if (conversationId.startsWith("demo-dm-")) {
            while (true) {
                emit(Result.success(LocalDemoChatStore.listDmMessages(conversationId)))
                delay(1200L)
            }
        }
        emit(listDmMessages(conversationId))
        val topic = "dm_chat:$conversationId"
        val channel = supabase.channel(topic)
        runCatching { channel.subscribe(blockUntilSubscribed = true) }
            .onFailure {
                emit(Result.failure(it))
                // Realtime fallback for clients that cannot subscribe successfully.
                while (true) {
                    emit(listDmMessages(conversationId))
                    delay(2000L)
                }
            }
        channel.broadcastFlow<RealtimeChatBroadcastPayload>(event = "INSERT").collect {
            emit(listDmMessages(conversationId))
        }
    }

    suspend fun sendDmMessage(conversationId: String, body: String): Result<Unit> {
        return try {
            val userId = currentUserIdOrDemo()
            val msg = body.trim()
            if (msg.isBlank()) return Result.failure(IllegalArgumentException("Message cannot be empty"))
            if (conversationId.startsWith("demo-dm-")) {
                LocalDemoChatStore.appendDmMessage(
                    conversationId,
                    DmMessageUi(
                        id = "demo-dm-msg-${System.currentTimeMillis()}",
                        senderId = userId,
                        senderName = "You",
                        body = msg,
                        createdAt = OffsetDateTime.now().toString()
                    )
                )
                return Result.success(Unit)
            }
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            supabase.from("dm_messages").insert(
                DmMessageInsert(
                    conversationId = conversationId,
                    senderId = user.id,
                    body = msg
                )
            )
            supabase.from("dm_conversations").update(
                {
                    set("last_message_at", OffsetDateTime.now().toString())
                }
            ) {
                filter { eq("id", conversationId) }
            }
            runCatching {
                val senderProfile = supabase.from("profiles")
                    .select {
                        filter { eq("id", user.id) }
                        limit(1)
                    }
                    .decodeSingle<ProfileRow>()
                val senderName = displayName(senderProfile, fallback = "Someone")
                val convo = supabase.from("dm_conversations")
                    .select {
                        filter { eq("id", conversationId) }
                        limit(1)
                    }
                    .decodeSingle<DmConversationRow>()
                val otherUserId = if (convo.userA == user.id) convo.userB else convo.userA
                notificationsRepo.createForUser(
                    userId = otherUserId,
                    title = "New message",
                    body = "$senderName sent you a message.",
                    deepLink = NotificationsRepository.DeepLinks.dmChat(user.id)
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun subscribeEventChatPresence(eventId: String): Flow<Result<List<ChatPresenceUi>>> = flow {
        while (true) {
            emit(listEventChatPresence(eventId))
            delay(1500L)
        }
    }

    suspend fun listEventChatPresence(eventId: String): Result<List<ChatPresenceUi>> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                val out = LocalDemoChatStore.listPresence(eventId).map { id ->
                    val role = when (id) {
                        "demo-host-local" -> "host"
                        "demo-cohost-local" -> "cohost"
                        else -> "attendee"
                    }
                    val label = when (id) {
                        "demo-host-local" -> "Demo Host"
                        "demo-cohost-local" -> "Demo Co-host"
                        else -> "You"
                    }
                    ChatPresenceUi(userId = id, displayName = label, role = role)
                }
                return Result.success(out)
            }
            val members = supabase.from("event_members")
                .select {
                    filter {
                        eq("event_id", eventId)
                        eq("status", "active")
                    }
                }
                .decodeList<EventMemberRow>()
            val out = members.mapNotNull { m ->
                val p = runCatching {
                    supabase.from("profiles")
                        .select {
                            filter { eq("id", m.userId) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                }.getOrNull() ?: return@mapNotNull null
                ChatPresenceUi(
                    userId = m.userId,
                    displayName = displayName(p),
                    role = m.role
                )
            }
            Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listBringItemClaims(eventId: String): Result<List<BringItemClaimUi>> {
        return try {
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                val out = LocalDemoChatStore.bringClaims(eventId).map { (itemKey, userId) ->
                    val label = parseBringItems(LocalDemoChatStore.demoEvent().bringItems).firstOrNull {
                        normalizeBringItemKey(it) == itemKey
                    } ?: itemKey
                    val userLabel = when (userId) {
                        "demo-host-local" -> "Demo Host"
                        "demo-cohost-local" -> "Demo Co-host"
                        "demo-me-local" -> "You"
                        else -> "User"
                    }
                    BringItemClaimUi(
                        itemKey = itemKey,
                        itemLabel = label,
                        claimedByUserId = userId,
                        claimedByName = userLabel
                    )
                }
                return Result.success(out)
            }
            val rows = supabase.from("event_bring_item_claims")
                .select {
                    filter { eq("event_id", eventId) }
                }
                .decodeList<EventBringItemClaimRow>()
            val out = rows.map { row ->
                val profile = runCatching {
                    supabase.from("profiles")
                        .select {
                            filter { eq("id", row.claimedBy) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                }.getOrNull()
                BringItemClaimUi(
                    itemKey = row.itemKey,
                    itemLabel = row.itemLabel,
                    claimedByUserId = row.claimedBy,
                    claimedByName = displayName(profile)
                )
            }
            Result.success(out)
        } catch (_: Exception) {
            Result.success(emptyList())
        }
    }

    suspend fun setBringItemClaim(eventId: String, itemLabel: String, claim: Boolean): Result<Unit> {
        return try {
            val userId = currentUserIdOrDemo()
            val key = normalizeBringItemKey(itemLabel)
            if (key.isBlank()) return Result.failure(IllegalArgumentException("Invalid item"))
            if (LocalDemoChatStore.isDemoEvent(eventId)) {
                LocalDemoChatStore.setBringClaim(eventId, key, if (claim) userId else null)
                return Result.success(Unit)
            }
            if (claim) {
                runCatching {
                    supabase.from("event_bring_item_claims").delete {
                        filter {
                            eq("event_id", eventId)
                            eq("item_key", key)
                        }
                    }
                }
                supabase.from("event_bring_item_claims").insert(
                    EventBringItemClaimInsert(
                        eventId = eventId,
                        itemKey = key,
                        itemLabel = itemLabel.trim(),
                        claimedBy = userId
                    )
                )
            } else {
                supabase.from("event_bring_item_claims").delete {
                    filter {
                        eq("event_id", eventId)
                        eq("item_key", key)
                        eq("claimed_by", userId)
                    }
                }
            }
            Result.success(Unit)
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
            val blockedIds = blockedUserIdsForCurrentUser(user.id)
            val visibleHosted = hosted.filterNot { it.hostId in blockedIds }

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
                .filterNot { it.hostId in blockedIds }

            val items = visibleHosted.map { MyEventHubItem(event = it, isHosting = true) } +
                attendingOnly.map { MyEventHubItem(event = it, isHosting = false) }
            val sorted = items.sortedByDescending { parseDate(it.event.startsAt) ?: OffsetDateTime.MIN }
            Result.success(sorted)
        } catch (e: Exception) {
            Result.success(listOf(MyEventHubItem(event = LocalDemoChatStore.demoEvent(), isHosting = true)))
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
            val blockedIds = blockedUserIdsForCurrentUser(user.id)
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
                .filterNot { it.hostId in blockedIds }
                .filter { it.visibility != "invite_only" }
                .filter { parseDate(it.startsAt)?.isAfter(OffsetDateTime.now()) == true }
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
        val demo = LocalDemoChatStore.demoEvent()
        if (demo.hostId == excludeHostId) return emptyList()
        return listOf(demo)
    }
}
