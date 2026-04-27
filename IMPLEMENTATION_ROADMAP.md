# Spacer Implementation Roadmap

This roadmap is organized by delivery phase with concrete scope, technical tasks, Supabase changes, and success metrics.

## Phase 1 (Quick Wins: 1-2 weeks)

## 1) Event deep links + notification routing

- **Goal**
  - Open app directly to event details, event chat, DM thread, or friend request context from push/local notifications and shared links.
- **App work**
  - Add deep-link route parsing in navigation.
  - Extend notification payload handling in `MainActivity` + dispatcher flow.
  - Add fallback route when target item is deleted/unavailable.
- **Backend/Supabase**
  - No schema required.
  - Optional: standardize notification payload shape in `user_notifications`.
- **Definition of done**
  - Tapping a DM notification opens the exact conversation.
  - Tapping event reminder opens the exact event.
  - Shared event link opens event detail screen.
- **Metric**
  - Notification open-to-target success >= 99%.

## 2) RSVP reminder controls

- **Goal**
  - Let users choose reminder timing per event (for example, 1 hour before / 1 day before).
- **App work**
  - Add reminder picker in invite/host detail screens.
  - Schedule local notifications and restore after app restart.
  - Show reminder state in event detail.
- **Backend/Supabase**
  - New table: `event_user_reminders`
    - `id`, `event_id`, `user_id`, `offset_minutes`, `enabled`, timestamps
  - RLS: user can manage only their own reminder rows.
- **Definition of done**
  - Reminder persists and fires at chosen offset.
  - Changing RSVP updates reminder behavior cleanly.
- **Metric**
  - Reminder delivery success on active installs >= 95%.

## 3) Chat safety baseline

- **Goal**
  - Add basic moderation controls to reduce abuse friction.
- **App work**
  - Event chat: mute user locally, host/co-host remove message, host/co-host remove member from chat.
  - DM: report message action from message bubble menu.
  - Add lightweight moderation history panel for hosts.
- **Backend/Supabase**
  - New table: `chat_message_reports`
  - New table: `event_chat_moderation_actions`
  - Optional soft-delete field on `event_chat_messages` (`deleted_at`, `deleted_by`).
  - RLS: host/co-host moderation scope per event.
- **Definition of done**
  - Host can remove abusive message immediately.
  - Message reports are stored with context.
- **Metric**
  - Median time to moderation action < 2 minutes.

---

## Phase 2 (Core Product: 2-4 weeks)

## 4) Discovery ranking (quality + relevance)

- **Goal**
  - Improve event ordering using distance, time recency, category preference, and social signal.
- **App work**
  - Add ranking explanation tags in UI (Nearby, Friends going, Soon).
  - Keep state-based zoning behavior, but improve ordering within allowed results.
- **Backend/Supabase**
  - Add RPC: `rank_discover_events(viewer_id, query, state_code, limit)`
  - Optional materialized view for event candidates.
  - Optional table: `user_category_affinity`.
- **Definition of done**
  - Search and home lists feel consistently relevant.
- **Metric**
  - Search-to-event-open conversion +20%.

## 5) Host toolkit v1 (capacity, waitlist, check-in)

- **Goal**
  - Give hosts practical controls to run real events.
- **App work**
  - Event settings: capacity limit, waitlist toggle.
  - Host detail: waitlist promote/demote actions.
  - Check-in mode (manual tap list first, QR optional later).
- **Backend/Supabase**
  - Add columns on `app_events`: `capacity`, `waitlist_enabled`.
  - New table: `event_waitlist`.
  - New table: `event_checkins`.
  - RLS for host/co-host management.
- **Definition of done**
  - Event enforces capacity and waitlist progression.
  - Host can check in attendees at arrival.
- **Metric**
  - Host repeat event creation rate +15%.

## 6) Offline-first reliability layer

- **Goal**
  - Prevent blank states and reduce perceived latency.
- **App work**
  - Add local cache (Room/DataStore) for:
    - home feed
    - event detail
    - DM and event chat recent messages
  - Use stale-while-refresh strategy with visible sync state.
- **Backend/Supabase**
  - No schema required.
  - Optional server timestamps in payloads for conflict handling.
- **Definition of done**
  - Core screens render from cache instantly when previously visited.
  - Reconnect sync is automatic and stable.
- **Metric**
  - Cold-open to useful content < 1.5s on mid-tier device (cached scenario).

---

## Phase 3 (Scale + Control: 2-3 weeks)

## 7) Analytics and feature flags

- **Goal**
  - Measure funnel behavior and ship safely with controlled rollouts.
- **App work**
  - Track events:
    - search started
    - event opened
    - RSVP action
    - chat opened
    - DM reply sent
  - Add feature-flag checks for risky UI/logic changes.
- **Backend/Supabase**
  - Table: `app_feature_flags` (or remote config provider)
  - Table: `app_analytics_events` (or external analytics sink)
- **Definition of done**
  - New releases can gate features without app update.
  - Funnel dashboard available.
- **Metric**
  - Reduced rollback frequency and faster experiment cycles.

## 8) Design system extraction

- **Goal**
  - Improve consistency and reduce future UI effort.
- **App work**
  - Create reusable UI primitives:
    - cards (feature card/list row/section card)
    - pills/chips/status badges
    - action rows and top headers
  - Move repeated styles to shared theme tokens.
- **Backend/Supabase**
  - None.
- **Definition of done**
  - Most screens use shared components, not bespoke duplicates.
- **Metric**
  - UI change PR size reduced by ~30% for repeated patterns.

---

## Recommended Delivery Order (Exact)

1. Deep links and notification routing
2. RSVP reminders
3. Chat safety baseline
4. Discovery ranking
5. Host toolkit v1
6. Offline-first caching
7. Analytics + flags
8. Design system extraction

---

## Schema Change Checklist (Supabase)

- `event_user_reminders` table + RLS
- `chat_message_reports` table + RLS
- `event_chat_moderation_actions` table + RLS
- `event_chat_messages` moderation fields (optional but recommended)
- `app_events` capacity/waitlist columns
- `event_waitlist` table + RLS
- `event_checkins` table + RLS
- `rank_discover_events(...)` RPC (recommended)
- `app_feature_flags` and analytics storage strategy

---

## QA Strategy Per Phase

- **Functional QA**
  - Route handling, edge cases, and role permissions.
- **Permission QA**
  - Validate RLS using host/co-host/attendee/non-member accounts.
- **Resilience QA**
  - Network drop, reconnect, stale cache, and notification-open scenarios.
- **Regression QA**
  - Events hub, invite flow, chats, profile actions, block/privacy behavior.

---

## Risk Notes

- Deep links and notifications can silently fail without payload contract standardization.
- Chat moderation requires careful RLS to avoid role escalation.
- Ranking changes can unintentionally hide relevant events if weights are too strict.
- Offline cache must avoid stale writes overriding newer server state.
