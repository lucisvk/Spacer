# Spacer Project Workflows

This document maps core user workflows to the main screens, repository functions, and database tables/functions they use.

Related planning document: `IMPLEMENTATION_ROADMAP.md`.

## 1) Authentication and Session

- **Entry/UI**
  - `MainActivity` -> `SpacerNavHost` routes (`Splash`, `Login`, `CreateAccount`, `App`)
- **Core logic**
  - `AuthRepository.login()`
  - `AuthRepository.signup()`
  - `AuthRepository.signInWithOAuth()`
  - `AuthRepository.ensureProfileAfterOAuthSignIn()`
- **Session and local cache**
  - `SessionPrefs` (theme, profile snapshot, presence, calendar toggles)
- **Backend**
  - Supabase Auth
  - `public.profiles`

## 2) Profile (Own Profile)

- **Screen**
  - `ProfileScreen`
- **Data load/update**
  - `ProfileRepository.load()`
  - `ProfileRepository.updatePresenceStatus()`
- **Navigation actions**
  - Edit profile, settings, hosted/attended/friends, messages

## 3) Public Profile (Other Users)

- **Screen**
  - `PublicProfileScreen`
- **Core logic**
  - `ProfileRepository.getPublicProfile(userId)`
  - `ProfileRepository.getFriendshipStates(ids)`
  - `ProfileRepository.sendFriendRequest(targetUserId)`
  - `ProfileRepository.respondToFriendRequest(otherUserId, accept)`
  - `ProfileRepository.unfriend(otherUserId)`
  - `ProfileRepository.blockUser(otherUserId)`
  - `ProfileRepository.reportUser(otherUserId, reason)`
- **Backend**
  - `public.profiles`
  - `public.friend_requests`
  - `public.user_blocks`
  - `public.user_reports`

## 4) Settings and Availability

- **Screen**
  - `SettingsScreen`
  - `AvailabilityScreen`
- **Core logic**
  - `SessionPrefs.setCalendarAvailabilitySharingEnabled()`
  - `SessionPrefs.setDeviceCalendarReadEnabled()`
  - `SessionPrefs.setCalendarConflictNotificationsEnabled()`
  - `AvailabilityRepository.loadPreferences()`
  - `AvailabilityRepository.savePreferences(...)`
  - `AvailabilityRepository.listWeeklyWindows()`
  - `AvailabilityRepository.addWeeklyWindow(...)`
  - `AvailabilityRepository.removeWeeklyWindow(...)`
  - `ProfileRepository.requestAccountDeletion(reason)`
- **Device integration**
  - `DeviceCalendarBusyChecker`
- **Backend**
  - `public.user_availability_preferences`
  - `public.user_weekly_availability_windows`
  - `public.user_specific_availability`
  - `public.event_location_open_hours`
  - account deletion request table/function (via `ProfileRepository`)

## 5) Events Hub (Invites, Hosting, Public Discovery)

- **Screen**
  - `EventsHubScreen`
- **Core load workflow**
  - `EventRepository.listPendingInvites()`
  - `EventRepository.listMyHostingAndAttendingEvents()`
  - `EventRepository.listPublicDiscoverableEvents()`
  - `EventRepository.getEvent("demo-local-event-1")` (offline demo fallback)
- **Auxiliary**
  - `PlacesRepository.searchText()` for photo enrichment
  - `UserNotificationDispatcher.flushUnreadToPhone()`
- **Backend**
  - `public.app_events`
  - `public.event_invites`
  - `public.public_event_invites`
  - `public.user_blocks` (visibility filtering)

## 6) Event Creation and Hosting Controls

- **Screens**
  - `CreateEventDetailsScreen`
  - `HostEventDetailScreen`
- **Core logic**
  - `EventRepository.createEventWithInvites(...)`
  - `EventRepository.updateEventCoreDetails(...)`
  - `EventRepository.addCohost(eventId, userId)`
  - `EventRepository.removeCohost(eventId, userId)`
  - `EventRepository.listCohosts(eventId)`
  - `EventRepository.getEventChatMode(eventId)`
  - `EventRepository.setEventChatMode(eventId, mode)`
- **Bring list workflow**
  - `EventRepository.parseBringItems(raw)`
  - `EventRepository.encodeBringItems(items)`
  - `EventRepository.listBringItemClaims(eventId)`
  - `EventRepository.setBringItemClaim(eventId, itemLabel, claim)`
- **Backend**
  - `public.app_events`
  - `public.event_members`
  - `public.event_chat_rooms`
  - `public.event_bring_item_claims`

## 7) Invitee Event Detail and RSVP

- **Screen**
  - `InviteEventScreen`
- **Core logic**
  - `EventRepository.getEvent(eventId)`
  - `EventRepository.getInviteStatusForEvent(eventId)`
  - `EventRepository.respondToInvite(eventId, accept)`
  - `EventRepository.submitAvailability(...)`
  - `EventRepository.listBringItemClaims(eventId)`
  - `EventRepository.setBringItemClaim(eventId, itemLabel, claim)`
- **Device integration**
  - Calendar intent (`CalendarContract`)
  - Maps intent (`openGoogleMapsForPlace`)
  - distance estimate (`estimateDistanceFromUser`)
- **Backend**
  - `public.event_invites`
  - `public.event_availability`
  - `public.event_bring_item_claims`

## 8) Social Discovery (Find People)

- **Screen**
  - `FindPeopleScreen`
- **Core logic**
  - `ProfileRepository.searchUsers(query)`
  - `ProfileRepository.getFriendshipStates(ids)`
  - same friend/block actions as Public Profile
- **Backend**
  - `public.profiles`
  - `public.friend_requests`
  - `public.user_blocks`

## 9) Direct Messages (DM)

- **Screens**
  - `DmThreadsScreen`
  - `DmChatScreen`
- **Core logic**
  - `EventRepository.listDmThreads()`
  - `EventRepository.getOrCreateDmConversation(otherUserId)`
  - `EventRepository.subscribeDmMessages(conversationId)`
  - `EventRepository.listDmMessages(conversationId)`
  - `EventRepository.sendDmMessage(conversationId, body)`
- **Realtime behavior**
  - Realtime broadcast subscription first
  - polling fallback when subscription is unavailable
- **Backend**
  - `public.dm_conversations`
  - `public.dm_messages`
  - realtime topic: `dm_chat:<conversation_uuid>`

## 10) Event Chat

- **Screen**
  - `EventChatScreen`
- **Core logic**
  - `EventRepository.getEventChatMode(eventId)`
  - `EventRepository.subscribeEventChatMessages(eventId)`
  - `EventRepository.listEventChatMessages(eventId)`
  - `EventRepository.sendEventChatMessage(eventId, body)`
  - `EventRepository.subscribeEventChatPresence(eventId)`
  - `EventRepository.listEventChatPresence(eventId)`
- **Realtime behavior**
  - Realtime broadcast subscription first
  - polling fallback when subscription is unavailable
- **Backend**
  - `public.event_chat_rooms`
  - `public.event_chat_messages`
  - realtime topic: `event_chat:<room_uuid>`

## 11) In-App Notifications to Phone

- **Dispatcher**
  - `UserNotificationDispatcher.flushUnreadToPhone(context, repository)`
- **Repository**
  - `NotificationsRepository.listUnread()`
  - `NotificationsRepository.markRead(notificationId)`
  - `NotificationsRepository.createForUser(userId, title, body)`
- **Triggered from**
  - friend request lifecycle (`ProfileRepository`)
  - DM send (`EventRepository.sendDmMessage`)
  - event chat send (`EventRepository.sendEventChatMessage`)
  - event detail updates (`EventRepository.updateEventCoreDetails`)
  - periodic flush loop in `MainActivity`
- **Backend**
  - `public.user_notifications`

## 12) Demo/Offline Workflow

- **Local demo source**
  - `LocalDemoChatStore` in `EventRepository`
- **Demo IDs**
  - event: `demo-local-event-1`
- **Purpose**
  - allows testing event/chat/co-host/bring-claims when backend/API is unavailable
- **Behavior**
  - repository methods short-circuit to local data for demo event and demo DM identifiers
