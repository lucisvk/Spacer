package com.example.spacer.profile

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.example.spacer.network.SupabaseManager
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Serializable
data class ProfileRow(
    val id: String,
    val email: String? = null,
    val username: String? = null,
    /** Display name in DB column `name` (legacy docs used `full_name`). */
    @SerialName("name")
    val fullName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("about_me")
    val aboutMe: String? = null
)

@Serializable
data class UserStatsRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("hosted_count")
    val hostedCount: Int = 0,
    @SerialName("attended_count")
    val attendedCount: Int = 0,
    @SerialName("friends_count")
    val friendsCount: Int = 0
)

data class ProfileSnapshot(
    val profile: ProfileRow,
    val stats: UserStatsRow
)

@Serializable
data class SearchUserRow(
    val id: String,
    val username: String? = null,
    val email: String? = null,
    @SerialName("name")
    val fullName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
data class EventRow(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("host_id")
    val hostId: String,
    @SerialName("starts_at")
    val startsAt: String,
    @SerialName("ends_at")
    val endsAt: String? = null,
    val location: String? = null,
    /** [public] listed in discovery; [invite_only] friends / invited only. */
    val visibility: String? = null,
    val category: String? = null
)

@Serializable
private data class EventInviteAcceptedRow(
    @SerialName("event_id")
    val eventId: String,
    val status: String = "accepted"
)

data class FriendListItem(
    val id: String,
    val fullName: String,
    val username: String,
    val avatarUrl: String?
)

data class PublicProfileSnapshot(
    val id: String,
    val fullName: String,
    val username: String,
    val avatarUrl: String?,
    val aboutMe: String,
    val hostedEvents: List<EventRow>,
    val attendedEvents: List<EventRow>,
    val friends: List<FriendListItem>
)

@Serializable
private data class FriendRequestInsert(
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("receiver_id")
    val receiverId: String,
    val status: String = "pending"
)

@Serializable
private data class UserBlockInsert(
    @SerialName("blocker_id")
    val blockerId: String,
    @SerialName("blocked_id")
    val blockedId: String
)

@Serializable
private data class UserReportInsert(
    @SerialName("reporter_id")
    val reporterId: String,
    @SerialName("reported_id")
    val reportedId: String,
    val reason: String
)

@Serializable
private data class AccountDeletionRequestInsert(
    @SerialName("user_id")
    val userId: String,
    val reason: String? = null
)

/** In-app sample people so Find people works before extra Supabase profiles exist. */
private object FindPeopleDemoDirectory {
    private val rows: List<SearchUserRow> = listOf(
        SearchUserRow(
            id = "11111111-1111-4111-8111-111111111101",
            username = "alex_spacer",
            email = "alex.demo@spacer.app",
            fullName = "Alex Demo",
            avatarUrl = null
        ),
        SearchUserRow(
            id = "11111111-1111-4111-8111-111111111102",
            username = "sam_planner",
            email = "sam.demo@spacer.app",
            fullName = "Sam Planner",
            avatarUrl = null
        ),
        SearchUserRow(
            id = "11111111-1111-4111-8111-111111111103",
            username = "river_hosts",
            email = "river.demo@spacer.app",
            fullName = "River Chen",
            avatarUrl = null
        )
    )

    fun matches(safeQuery: String): List<SearchUserRow> {
        if (safeQuery.isBlank()) return emptyList()
        val q = safeQuery.lowercase()
        return rows.filter { r ->
            listOf(r.username, r.fullName, r.email)
                .mapNotNull { it?.lowercase() }
                .any { it.contains(q) }
        }
    }

    fun isDemoId(id: String): Boolean = rows.any { it.id == id }
}

private object DemoProfileDetailsDirectory {
    private data class DemoProfileDetails(
        val id: String,
        val fullName: String,
        val username: String,
        val aboutMe: String,
        val hostedEvents: List<EventRow>,
        val attendedEvents: List<EventRow>,
        val friends: List<FriendListItem>
    )

    private fun eventDate(daysFromNow: Long, hour24: Int): String {
        return OffsetDateTime.now()
            .plusDays(daysFromNow)
            .withHour(hour24)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toString()
    }

    private val alexId = "11111111-1111-4111-8111-111111111101"
    private val samId = "11111111-1111-4111-8111-111111111102"
    private val riverId = "11111111-1111-4111-8111-111111111103"

    private val rows: Map<String, DemoProfileDetails> = listOf(
        DemoProfileDetails(
            id = alexId,
            fullName = "Alex Demo",
            username = "alex_spacer",
            aboutMe = "Coffee runs, rooftop hangs, and finding new spots around the city.",
            hostedEvents = listOf(
                EventRow(
                    id = "demo-alex-host-1",
                    title = "Sunset Rooftop Mixer",
                    description = "Casual rooftop meetup with music and mocktails.",
                    hostId = alexId,
                    startsAt = eventDate(2, 19),
                    endsAt = eventDate(2, 21),
                    location = "230 Fifth Rooftop Bar, New York, NY",
                    visibility = "public",
                    category = "Social"
                ),
                EventRow(
                    id = "demo-alex-host-2",
                    title = "Sunday Brunch Club",
                    description = "Easy brunch and a walk after.",
                    hostId = alexId,
                    startsAt = eventDate(-6, 11),
                    endsAt = eventDate(-6, 13),
                    location = "Buvette, West Village, New York, NY",
                    visibility = "public",
                    category = "Food"
                )
            ),
            attendedEvents = listOf(
                EventRow(
                    id = "demo-alex-attend-1",
                    title = "Board Game Night",
                    hostId = samId,
                    startsAt = eventDate(-3, 18),
                    endsAt = eventDate(-3, 21),
                    location = "The Uncommons, Manhattan, NY",
                    visibility = "public",
                    category = "Games"
                ),
                EventRow(
                    id = "demo-alex-attend-2",
                    title = "Live Jazz Hang",
                    hostId = riverId,
                    startsAt = eventDate(-12, 20),
                    endsAt = eventDate(-12, 22),
                    location = "Blue Note Jazz Club, Greenwich Village, NY",
                    visibility = "public",
                    category = "Music"
                )
            ),
            friends = listOf(
                FriendListItem(id = samId, fullName = "Sam Planner", username = "sam_planner", avatarUrl = null),
                FriendListItem(id = riverId, fullName = "River Chen", username = "river_hosts", avatarUrl = null)
            )
        ),
        DemoProfileDetails(
            id = samId,
            fullName = "Sam Planner",
            username = "sam_planner",
            aboutMe = "I organize chill group plans and game nights.",
            hostedEvents = listOf(
                EventRow(
                    id = "demo-sam-host-1",
                    title = "Board Game Night",
                    description = "Beginner-friendly games and snacks.",
                    hostId = samId,
                    startsAt = eventDate(4, 18),
                    endsAt = eventDate(4, 21),
                    location = "The Uncommons, Manhattan, NY",
                    visibility = "public",
                    category = "Games"
                )
            ),
            attendedEvents = listOf(
                EventRow(
                    id = "demo-sam-attend-1",
                    title = "Morning Run + Coffee",
                    hostId = riverId,
                    startsAt = eventDate(-4, 9),
                    endsAt = eventDate(-4, 11),
                    location = "Central Park - Columbus Circle, New York, NY",
                    visibility = "public",
                    category = "Fitness"
                )
            ),
            friends = listOf(
                FriendListItem(id = alexId, fullName = "Alex Demo", username = "alex_spacer", avatarUrl = null),
                FriendListItem(id = riverId, fullName = "River Chen", username = "river_hosts", avatarUrl = null)
            )
        ),
        DemoProfileDetails(
            id = riverId,
            fullName = "River Chen",
            username = "river_hosts",
            aboutMe = "Always down for live music, art walks, and weekend activities.",
            hostedEvents = listOf(
                EventRow(
                    id = "demo-river-host-1",
                    title = "Live Jazz Hang",
                    description = "Meetup before the show and table together.",
                    hostId = riverId,
                    startsAt = eventDate(3, 20),
                    endsAt = eventDate(3, 22),
                    location = "Blue Note Jazz Club, Greenwich Village, NY",
                    visibility = "public",
                    category = "Music"
                ),
                EventRow(
                    id = "demo-river-host-2",
                    title = "Gallery Walk",
                    description = "Art walk through current exhibits.",
                    hostId = riverId,
                    startsAt = eventDate(7, 14),
                    endsAt = eventDate(7, 17),
                    location = "The Metropolitan Museum of Art, New York, NY",
                    visibility = "public",
                    category = "Arts"
                )
            ),
            attendedEvents = listOf(
                EventRow(
                    id = "demo-river-attend-1",
                    title = "Food Hall Meetup",
                    hostId = alexId,
                    startsAt = eventDate(-8, 13),
                    endsAt = eventDate(-8, 15),
                    location = "Chelsea Market, New York, NY",
                    visibility = "public",
                    category = "Food"
                )
            ),
            friends = listOf(
                FriendListItem(id = alexId, fullName = "Alex Demo", username = "alex_spacer", avatarUrl = null),
                FriendListItem(id = samId, fullName = "Sam Planner", username = "sam_planner", avatarUrl = null)
            )
        )
    ).associateBy { it.id }

    fun isDemoId(id: String): Boolean = rows.containsKey(id)

    fun profileFor(id: String): PublicProfileSnapshot? {
        val row = rows[id] ?: return null
        return PublicProfileSnapshot(
            id = row.id,
            fullName = row.fullName,
            username = row.username,
            avatarUrl = null,
            aboutMe = row.aboutMe,
            hostedEvents = row.hostedEvents.sortedByDescending { it.startsAt },
            attendedEvents = row.attendedEvents.sortedByDescending { it.startsAt },
            friends = row.friends
        )
    }
}

class ProfileRepository {
    private val supabase = SupabaseManager.client

    companion object {
        fun isOfflineDemoProfile(id: String): Boolean =
            FindPeopleDemoDirectory.isDemoId(id) || DemoProfileDetailsDirectory.isDemoId(id)
    }

    private fun parseDateSafely(value: String): OffsetDateTime? {
        return try {
            OffsetDateTime.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private suspend fun pastHostedEventRows(hostUserId: String): List<EventRow> {
        val now = OffsetDateTime.now()
        return supabase.from("app_events")
            .select {
                filter { eq("host_id", hostUserId) }
                order(column = "starts_at", order = Order.DESCENDING)
            }
            .decodeList<EventRow>()
            .filter { event -> parseDateSafely(event.startsAt)?.isBefore(now) == true }
    }

    private suspend fun allHostedEventRows(hostUserId: String): List<EventRow> {
        return supabase.from("app_events")
            .select {
                filter { eq("host_id", hostUserId) }
                order(column = "starts_at", order = Order.DESCENDING)
            }
            .decodeList<EventRow>()
    }

    private suspend fun pastAcceptedAttendedEventRows(inviteeUserId: String): List<EventRow> {
        val now = OffsetDateTime.now()
        val invites = supabase.from("event_invites")
            .select {
                filter {
                    eq("invitee_id", inviteeUserId)
                    eq("status", "accepted")
                }
            }
            .decodeList<EventInviteAcceptedRow>()
        return invites.mapNotNull { inv ->
            runCatching {
                supabase.from("app_events")
                    .select {
                        filter { eq("id", inv.eventId) }
                        limit(1)
                    }
                    .decodeSingle<EventRow>()
            }.getOrNull()
        }
            .filter { event -> parseDateSafely(event.startsAt)?.isBefore(now) == true }
            .sortedByDescending { parseDateSafely(it.startsAt) }
    }

    private suspend fun countAcceptedFriends(userId: String): Int {
        val outgoing = supabase.from("friend_requests")
            .select {
                filter {
                    eq("sender_id", userId)
                    eq("status", "accepted")
                }
            }
            .decodeList<FriendRequestInsert>()
            .map { it.receiverId }
        val incoming = supabase.from("friend_requests")
            .select {
                filter {
                    eq("receiver_id", userId)
                    eq("status", "accepted")
                }
            }
            .decodeList<FriendRequestInsert>()
            .map { it.senderId }
        return (outgoing + incoming).distinct().size
    }

    private suspend fun countAllHostedEvents(hostUserId: String): Int {
        return supabase.from("app_events")
            .select {
                filter { eq("host_id", hostUserId) }
            }
            .decodeList<EventRow>()
            .size
    }

    private suspend fun countAllAcceptedInvites(inviteeUserId: String): Int {
        val invites = supabase.from("event_invites")
            .select {
                filter {
                    eq("invitee_id", inviteeUserId)
                    eq("status", "accepted")
                }
            }
            .decodeList<EventInviteAcceptedRow>()
        return invites.map { it.eventId }.distinct().size
    }

    private fun mergeProfileWithAuthSession(profile: ProfileRow, user: io.github.jan.supabase.auth.user.UserInfo): ProfileRow {
        val metaFull = user.userMetadata
            ?.get("full_name")
            ?.toString()
            ?.trim('"', ' ')
            ?.takeIf { it.isNotBlank() }
        val metaName = user.userMetadata
            ?.get("name")
            ?.toString()
            ?.trim('"', ' ')
            ?.takeIf { it.isNotBlank() }
        val email = profile.email?.takeIf { it.isNotBlank() } ?: user.email
        val username = profile.username?.takeIf { it.isNotBlank() } ?: metaName
        val fullName = profile.fullName?.takeIf { it.isNotBlank() } ?: metaFull
        return profile.copy(email = email, username = username, fullName = fullName)
    }

    suspend fun load(): Result<ProfileSnapshot> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))

            val profileRow = supabase.from("profiles")
                .select {
                    filter { eq("id", user.id) }
                    limit(1)
                }
                .decodeSingle<ProfileRow>()

            val profile = mergeProfileWithAuthSession(profileRow, user)

            val stats = UserStatsRow(
                userId = user.id,
                hostedCount = countAllHostedEvents(user.id),
                attendedCount = countAllAcceptedInvites(user.id),
                friendsCount = countAcceptedFriends(user.id)
            )

            Result.success(ProfileSnapshot(profile = profile, stats = stats))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(fullName: String, aboutMe: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))

            supabase.from("profiles")
                .update(
                    {
                        set("name", fullName)
                        set("about_me", aboutMe)
                    }
                ) {
                    filter { eq("id", user.id) }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchUsers(query: String): Result<List<SearchUserRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val q = query.trim()
            if (q.isEmpty()) return Result.success(emptyList())
            if (q.length < 1) return Result.success(emptyList())

            val safe = q.replace("%", "").replace("_", "").trim()
            if (safe.isEmpty()) return Result.success(emptyList())
            val pattern = "%$safe%"

            // Separate queries avoid PostgREST `or` + `neq` edge cases and work better with RLS.
            val byUsername = supabase.from("profiles")
                .select {
                    filter {
                        ilike("username", pattern)
                        neq("id", user.id)
                    }
                    limit(40)
                }
                .decodeList<SearchUserRow>()

            val byName = supabase.from("profiles")
                .select {
                    filter {
                        ilike("name", pattern)
                        neq("id", user.id)
                    }
                    limit(40)
                }
                .decodeList<SearchUserRow>()

            val byEmail = runCatching {
                supabase.from("profiles")
                    .select {
                        filter {
                            ilike("email", pattern)
                            neq("id", user.id)
                        }
                        limit(40)
                    }
                    .decodeList<SearchUserRow>()
            }.getOrElse { emptyList() }

            var merged = (byUsername + byName + byEmail).distinctBy { it.id }

            if (merged.isEmpty() && safe.isNotEmpty()) {
                val broad = supabase.from("profiles")
                    .select {
                        filter { neq("id", user.id) }
                        limit(200)
                    }
                    .decodeList<SearchUserRow>()
                val needle = safe.lowercase()
                merged = broad.filter { row ->
                    listOf(row.username, row.fullName, row.email)
                        .mapNotNull { it?.lowercase() }
                        .joinToString(" ")
                        .contains(needle)
                }.take(40)
            }

            val demo = FindPeopleDemoDirectory.matches(safe)
            val out = (merged + demo).distinctBy { it.id }.take(40)
            Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendFriendRequest(targetUserId: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            if (targetUserId == user.id) {
                return Result.failure(IllegalArgumentException("You can't add yourself"))
            }
            if (isOfflineDemoProfile(targetUserId)) {
                return Result.success(Unit)
            }

            supabase.from("friend_requests").insert(
                FriendRequestInsert(
                    senderId = user.id,
                    receiverId = targetUserId
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun blockUser(targetUserId: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            if (targetUserId == user.id) {
                return Result.failure(IllegalArgumentException("You can't block yourself"))
            }
            if (isOfflineDemoProfile(targetUserId)) {
                return Result.success(Unit)
            }

            supabase.from("user_blocks").insert(
                UserBlockInsert(
                    blockerId = user.id,
                    blockedId = targetUserId
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportUser(targetUserId: String, reason: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val cleanedReason = reason.trim()
            if (cleanedReason.length < 4) {
                return Result.failure(IllegalArgumentException("Please provide a report reason"))
            }
            if (targetUserId == user.id) {
                return Result.failure(IllegalArgumentException("You can't report yourself"))
            }
            if (isOfflineDemoProfile(targetUserId)) {
                return Result.success(Unit)
            }

            supabase.from("user_reports").insert(
                UserReportInsert(
                    reporterId = user.id,
                    reportedId = targetUserId,
                    reason = cleanedReason
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestAccountDeletion(reason: String?): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            supabase.from("account_deletion_requests").insert(
                AccountDeletionRequestInsert(
                    userId = user.id,
                    reason = reason?.trim()?.ifBlank { null }
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPastHostedEvents(): Result<List<EventRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            Result.success(pastHostedEventRows(user.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Past and upcoming, newest first — used for profile hosted list with cancel on upcoming. */
    suspend fun getAllHostedEvents(): Result<List<EventRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            Result.success(allHostedEventRows(user.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPastAttendedEvents(): Result<List<EventRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            Result.success(pastAcceptedAttendedEventRows(user.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFriends(): Result<List<FriendListItem>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))

            val outgoing = supabase.from("friend_requests")
                .select {
                    filter {
                        eq("sender_id", user.id)
                        eq("status", "accepted")
                    }
                }
                .decodeList<FriendRequestInsert>()
                .map { it.receiverId }

            val incoming = supabase.from("friend_requests")
                .select {
                    filter {
                        eq("receiver_id", user.id)
                        eq("status", "accepted")
                    }
                }
                .decodeList<FriendRequestInsert>()
                .map { it.senderId }

            val friendIds = (outgoing + incoming).distinct()
            val friends = friendIds.mapNotNull { id ->
                runCatching {
                    val p = supabase.from("profiles")
                        .select {
                            filter { eq("id", id) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                    FriendListItem(
                        id = p.id,
                        fullName = p.fullName?.ifBlank { p.username ?: "User" } ?: (p.username ?: "User"),
                        username = p.username ?: "user",
                        avatarUrl = p.avatarUrl
                    )
                }.getOrNull()
            }.sortedBy { it.fullName.lowercase() }

            Result.success(friends)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPublicProfile(userId: String): Result<PublicProfileSnapshot> {
        return try {
            DemoProfileDetailsDirectory.profileFor(userId)?.let { demo ->
                return Result.success(demo)
            }

            val p = supabase.from("profiles")
                .select {
                    filter { eq("id", userId) }
                    limit(1)
                }
                .decodeSingle<ProfileRow>()

            val hosted = supabase.from("app_events")
                .select {
                    filter { eq("host_id", userId) }
                    order(column = "starts_at", order = Order.DESCENDING)
                    limit(8)
                }
                .decodeList<EventRow>()

            val invites = runCatching {
                supabase.from("event_invites")
                    .select {
                        filter {
                            eq("invitee_id", userId)
                            eq("status", "accepted")
                        }
                        limit(20)
                    }
                    .decodeList<EventInviteAcceptedRow>()
            }.getOrDefault(emptyList())

            val attended = invites.mapNotNull { inv ->
                runCatching {
                    supabase.from("app_events")
                        .select {
                            filter { eq("id", inv.eventId) }
                            limit(1)
                        }
                        .decodeSingle<EventRow>()
                }.getOrNull()
            }.sortedByDescending { it.startsAt }

            val outgoing = runCatching {
                supabase.from("friend_requests")
                    .select {
                        filter {
                            eq("sender_id", userId)
                            eq("status", "accepted")
                        }
                    }
                    .decodeList<FriendRequestInsert>()
                    .map { it.receiverId }
            }.getOrDefault(emptyList())
            val incoming = runCatching {
                supabase.from("friend_requests")
                    .select {
                        filter {
                            eq("receiver_id", userId)
                            eq("status", "accepted")
                        }
                    }
                    .decodeList<FriendRequestInsert>()
                    .map { it.senderId }
            }.getOrDefault(emptyList())

            val friends = (outgoing + incoming).distinct().mapNotNull { id ->
                runCatching {
                    val f = supabase.from("profiles")
                        .select {
                            filter { eq("id", id) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                    FriendListItem(
                        id = f.id,
                        fullName = f.fullName?.ifBlank { f.username ?: "User" } ?: (f.username ?: "User"),
                        username = f.username ?: "user",
                        avatarUrl = f.avatarUrl
                    )
                }.getOrNull()
            }.sortedBy { it.fullName.lowercase() }

            Result.success(
                PublicProfileSnapshot(
                    id = p.id,
                    fullName = p.fullName?.ifBlank { p.username ?: "User" } ?: (p.username ?: "User"),
                    username = p.username ?: "user",
                    avatarUrl = p.avatarUrl,
                    aboutMe = p.aboutMe?.ifBlank { "No bio yet." } ?: "No bio yet.",
                    hostedEvents = hosted,
                    attendedEvents = attended,
                    friends = friends
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unfriend(targetUserId: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))

            supabase.from("friend_requests").delete {
                filter {
                    eq("sender_id", user.id)
                    eq("receiver_id", targetUserId)
                    eq("status", "accepted")
                }
            }

            supabase.from("friend_requests").delete {
                filter {
                    eq("sender_id", targetUserId)
                    eq("receiver_id", user.id)
                    eq("status", "accepted")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

