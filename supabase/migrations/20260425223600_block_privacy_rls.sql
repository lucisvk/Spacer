/*
  Migration purpose:
  Enforce block privacy in RLS so blocked users cannot view each other's profiles,
  events, invites, or social-request records.
*/

create or replace function public.is_blocked_between(user_a uuid, user_b uuid)
returns boolean
language sql
stable
as $$
  select exists (
    select 1
    from public.user_blocks ub
    where
      (ub.blocker_id = user_a and ub.blocked_id = user_b)
      or
      (ub.blocker_id = user_b and ub.blocked_id = user_a)
  );
$$;

grant execute on function public.is_blocked_between(uuid, uuid) to authenticated;

create or replace function public.event_host_id(p_event_id uuid)
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select e.host_id
  from public.app_events e
  where e.id = p_event_id
  limit 1
$$;

grant execute on function public.event_host_id(uuid) to authenticated;

create or replace function public.is_event_invitee(p_event_id uuid, p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.event_invites ei
    where ei.event_id = p_event_id
      and ei.invitee_id = p_user_id
  )
$$;

grant execute on function public.is_event_invitee(uuid, uuid) to authenticated;

alter table if exists public.profiles enable row level security;
alter table if exists public.app_events enable row level security;
alter table if exists public.event_invites enable row level security;
alter table if exists public.friend_requests enable row level security;
alter table if exists public.user_blocks enable row level security;

/*
  Policy section:
  profiles visibility and self-update rules.
*/
drop policy if exists profiles_select_visible_non_blocked on public.profiles;
create policy profiles_select_visible_non_blocked
on public.profiles
for select
to authenticated
using (
  auth.uid() is not null
  and not public.is_blocked_between(auth.uid(), id)
);

drop policy if exists profiles_update_own_row on public.profiles;
create policy profiles_update_own_row
on public.profiles
for update
to authenticated
using (auth.uid() = id)
with check (auth.uid() = id);

/*
  Policy section:
  app_events visibility and host-only mutation rules.
*/
drop policy if exists app_events_select_non_blocked_access on public.app_events;
create policy app_events_select_non_blocked_access
on public.app_events
for select
to authenticated
using (
  auth.uid() is not null
  and not public.is_blocked_between(auth.uid(), host_id)
  and (
    host_id = auth.uid()
    or visibility = 'public'
    or public.is_event_invitee(app_events.id, auth.uid())
  )
);

drop policy if exists app_events_insert_host_only on public.app_events;
create policy app_events_insert_host_only
on public.app_events
for insert
to authenticated
with check (
  auth.uid() = host_id
);

drop policy if exists app_events_update_host_only on public.app_events;
create policy app_events_update_host_only
on public.app_events
for update
to authenticated
using (auth.uid() = host_id)
with check (auth.uid() = host_id);

drop policy if exists app_events_delete_host_only on public.app_events;
create policy app_events_delete_host_only
on public.app_events
for delete
to authenticated
using (auth.uid() = host_id);

/*
  Policy section:
  event_invites visibility and host/invitee mutation rules.
*/
drop policy if exists event_invites_select_host_or_invitee_non_blocked on public.event_invites;
create policy event_invites_select_host_or_invitee_non_blocked
on public.event_invites
for select
to authenticated
using (
  auth.uid() is not null
  and (
    invitee_id = auth.uid()
    or public.event_host_id(event_invites.event_id) = auth.uid()
  )
  and not public.is_blocked_between(public.event_host_id(event_invites.event_id), invitee_id)
);

drop policy if exists event_invites_insert_host_only_non_blocked on public.event_invites;
create policy event_invites_insert_host_only_non_blocked
on public.event_invites
for insert
to authenticated
with check (
  invitee_id <> auth.uid()
  and public.event_host_id(event_invites.event_id) = auth.uid()
  and not public.is_blocked_between(auth.uid(), event_invites.invitee_id)
);

drop policy if exists event_invites_update_invitee_only_non_blocked on public.event_invites;
create policy event_invites_update_invitee_only_non_blocked
on public.event_invites
for update
to authenticated
using (
  invitee_id = auth.uid()
  and not public.is_blocked_between(auth.uid(), public.event_host_id(event_invites.event_id))
)
with check (
  invitee_id = auth.uid()
  and not public.is_blocked_between(auth.uid(), public.event_host_id(event_invites.event_id))
);

drop policy if exists event_invites_delete_host_or_invitee on public.event_invites;
create policy event_invites_delete_host_or_invitee
on public.event_invites
for delete
to authenticated
using (
  invitee_id = auth.uid()
  or public.event_host_id(event_invites.event_id) = auth.uid()
);

/*
  Policy section:
  friend_requests visibility and participant mutation rules.
*/
drop policy if exists friend_requests_select_non_blocked_participants on public.friend_requests;
create policy friend_requests_select_non_blocked_participants
on public.friend_requests
for select
to authenticated
using (
  auth.uid() in (sender_id, receiver_id)
  and not public.is_blocked_between(sender_id, receiver_id)
);

drop policy if exists friend_requests_insert_sender_non_blocked on public.friend_requests;
create policy friend_requests_insert_sender_non_blocked
on public.friend_requests
for insert
to authenticated
with check (
  sender_id = auth.uid()
  and sender_id <> receiver_id
  and not public.is_blocked_between(sender_id, receiver_id)
);

drop policy if exists friend_requests_update_receiver_non_blocked on public.friend_requests;
create policy friend_requests_update_receiver_non_blocked
on public.friend_requests
for update
to authenticated
using (
  receiver_id = auth.uid()
  and not public.is_blocked_between(sender_id, receiver_id)
)
with check (
  receiver_id = auth.uid()
  and not public.is_blocked_between(sender_id, receiver_id)
);

drop policy if exists friend_requests_delete_participants_non_blocked on public.friend_requests;
create policy friend_requests_delete_participants_non_blocked
on public.friend_requests
for delete
to authenticated
using (
  auth.uid() in (sender_id, receiver_id)
  and not public.is_blocked_between(sender_id, receiver_id)
);

/*
  Policy section:
  user_blocks self-management rules.
*/
drop policy if exists user_blocks_select_own_blocks on public.user_blocks;
create policy user_blocks_select_own_blocks
on public.user_blocks
for select
to authenticated
using (blocker_id = auth.uid());

drop policy if exists user_blocks_insert_own_blocks on public.user_blocks;
create policy user_blocks_insert_own_blocks
on public.user_blocks
for insert
to authenticated
with check (
  blocker_id = auth.uid()
  and blocker_id <> blocked_id
);

drop policy if exists user_blocks_delete_own_blocks on public.user_blocks;
create policy user_blocks_delete_own_blocks
on public.user_blocks
for delete
to authenticated
using (blocker_id = auth.uid());
