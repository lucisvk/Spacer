/*
  Migration purpose:
  Introduce the chat and co-host data model:
  - event membership roles
  - event chat rooms/messages
  - direct message conversations/messages
  - helper functions and RLS policies for access control
*/

/*
  Query intent:
  Extend app_events with structured bring-items text used by host workflows.
*/
alter table if exists public.app_events
  add column if not exists bring_items text null;

/*
  Table intent:
  event_members defines host/cohost/attendee role membership per event.
*/
create table if not exists public.event_members (
  event_id uuid not null references public.app_events(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'attendee',
  status text not null default 'active',
  added_by uuid null references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint event_members_pk primary key (event_id, user_id),
  constraint event_members_role_chk check (role in ('host', 'cohost', 'attendee')),
  constraint event_members_status_chk check (status in ('active', 'removed'))
);

/*
  Query intent:
  Optimize user-centric role/status lookups for event membership.
*/
create index if not exists idx_event_members_user_role_status
  on public.event_members (user_id, role, status);

/*
  Query intent:
  Optimize event-centric active-role lookups used by permission checks.
*/
create index if not exists idx_event_members_event_role_active
  on public.event_members (event_id, role)
  where status = 'active';

/*
  Query intent:
  Backfill host rows so existing events are immediately permission-compatible.
*/
insert into public.event_members (event_id, user_id, role, status, added_by)
select e.id, e.host_id, 'host', 'active', e.host_id
from public.app_events e
on conflict (event_id, user_id) do update
set role = excluded.role,
    status = excluded.status,
    added_by = excluded.added_by;

/*
  Query intent:
  Backfill accepted invitees as active attendees for existing events.
*/
insert into public.event_members (event_id, user_id, role, status, added_by)
select i.event_id, i.invitee_id, 'attendee', 'active', e.host_id
from public.event_invites i
join public.app_events e on e.id = i.event_id
where i.status = 'accepted'
on conflict (event_id, user_id) do update
set role = excluded.role,
    status = excluded.status;

/*
  Table intent:
  event_chat_rooms stores one chat-room configuration per event.
*/
create table if not exists public.event_chat_rooms (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null unique references public.app_events(id) on delete cascade,
  chat_mode text not null default 'all_members',
  created_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint event_chat_rooms_mode_chk check (chat_mode in ('all_members', 'host_cohosts_only', 'disabled'))
);

/*
  Table intent:
  event_chat_messages stores event room messages with soft-delete support.
*/
create table if not exists public.event_chat_messages (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.event_chat_rooms(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null,
  created_at timestamptz not null default now(),
  deleted_at timestamptz null
);

/*
  Query intent:
  Optimize event message timeline reads by room and created time.
*/
create index if not exists idx_event_chat_messages_room_created
  on public.event_chat_messages (room_id, created_at desc);

/*
  Table intent:
  dm_conversations stores 1:1 participant pairing metadata.
*/
create table if not exists public.dm_conversations (
  id uuid primary key default gen_random_uuid(),
  user_a uuid not null references auth.users(id) on delete cascade,
  user_b uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  last_message_at timestamptz null,
  constraint dm_conversations_no_self_chk check (user_a <> user_b)
);

/*
  Query intent:
  Enforce one conversation per normalized user pair.
*/
create unique index if not exists dm_conversations_user_pair_uniq
  on public.dm_conversations (least(user_a, user_b), greatest(user_a, user_b));

/*
  Table intent:
  dm_messages stores per-conversation direct messages.
*/
create table if not exists public.dm_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.dm_conversations(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null,
  created_at timestamptz not null default now(),
  deleted_at timestamptz null
);

/*
  Query intent:
  Optimize DM timeline reads by conversation and created time.
*/
create index if not exists idx_dm_messages_conversation_created
  on public.dm_messages (conversation_id, created_at desc);

/*
  Function section intent:
  Reusable permission helpers for host/cohost/member/chat checks.
*/
create or replace function public.is_event_host_or_cohost(p_event_id uuid, p_user_id uuid)
returns boolean
language sql
stable
as $$
  select exists (
    select 1
    from public.event_members m
    where m.event_id = p_event_id
      and m.user_id = p_user_id
      and m.status = 'active'
      and m.role in ('host', 'cohost')
  );
$$;

create or replace function public.is_event_member_active(p_event_id uuid, p_user_id uuid)
returns boolean
language sql
stable
as $$
  select exists (
    select 1
    from public.event_members m
    where m.event_id = p_event_id
      and m.user_id = p_user_id
      and m.status = 'active'
  );
$$;

create or replace function public.can_access_event_chat(p_room_id uuid, p_user_id uuid)
returns boolean
language sql
stable
as $$
  select exists (
    select 1
    from public.event_chat_rooms r
    join public.app_events e on e.id = r.event_id
    where r.id = p_room_id
      and (
        e.host_id = p_user_id
        or public.is_event_member_active(r.event_id, p_user_id)
      )
      and not public.is_blocked_between(p_user_id, e.host_id)
  );
$$;

create or replace function public.can_post_event_chat(p_room_id uuid, p_user_id uuid)
returns boolean
language sql
stable
as $$
  select exists (
    select 1
    from public.event_chat_rooms r
    join public.app_events e on e.id = r.event_id
    where r.id = p_room_id
      and not public.is_blocked_between(p_user_id, e.host_id)
      and (
        (r.chat_mode = 'all_members' and (e.host_id = p_user_id or public.is_event_member_active(r.event_id, p_user_id)))
        or (r.chat_mode = 'host_cohosts_only' and public.is_event_host_or_cohost(r.event_id, p_user_id))
      )
  );
$$;

create or replace function public.is_dm_participant(p_conversation_id uuid, p_user_id uuid)
returns boolean
language sql
stable
as $$
  select exists (
    select 1
    from public.dm_conversations c
    where c.id = p_conversation_id
      and p_user_id in (c.user_a, c.user_b)
  );
$$;

create or replace function public.can_create_dm_between(p_user_a uuid, p_user_b uuid)
returns boolean
language sql
stable
as $$
  select
    p_user_a <> p_user_b
    and not public.is_blocked_between(p_user_a, p_user_b)
    and (
      exists (
        select 1
        from public.friend_requests f
        where f.status = 'accepted'
          and (
            (f.sender_id = p_user_a and f.receiver_id = p_user_b)
            or (f.sender_id = p_user_b and f.receiver_id = p_user_a)
          )
      )
      or exists (
        select 1
        from public.event_members ma
        join public.event_members mb on mb.event_id = ma.event_id
        where ma.user_id = p_user_a
          and mb.user_id = p_user_b
          and ma.status = 'active'
          and mb.status = 'active'
      )
    );
$$;

grant execute on function public.is_event_host_or_cohost(uuid, uuid) to authenticated;
grant execute on function public.is_event_member_active(uuid, uuid) to authenticated;
grant execute on function public.can_access_event_chat(uuid, uuid) to authenticated;
grant execute on function public.can_post_event_chat(uuid, uuid) to authenticated;
grant execute on function public.is_dm_participant(uuid, uuid) to authenticated;
grant execute on function public.can_create_dm_between(uuid, uuid) to authenticated;

/*
  RLS section intent:
  Enable RLS and define least-privilege access for event and DM chat tables.
*/
alter table if exists public.event_members enable row level security;
alter table if exists public.event_chat_rooms enable row level security;
alter table if exists public.event_chat_messages enable row level security;
alter table if exists public.dm_conversations enable row level security;
alter table if exists public.dm_messages enable row level security;

drop policy if exists event_members_select_visible on public.event_members;
create policy event_members_select_visible
on public.event_members
for select
to authenticated
using (
  auth.uid() = user_id
  or public.is_event_host_or_cohost(event_id, auth.uid())
);

drop policy if exists event_members_manage_host_cohost on public.event_members;
create policy event_members_manage_host_cohost
on public.event_members
for all
to authenticated
using (public.is_event_host_or_cohost(event_id, auth.uid()))
with check (public.is_event_host_or_cohost(event_id, auth.uid()));

drop policy if exists event_chat_rooms_select_visible on public.event_chat_rooms;
create policy event_chat_rooms_select_visible
on public.event_chat_rooms
for select
to authenticated
using (public.can_access_event_chat(id, auth.uid()));

drop policy if exists event_chat_rooms_insert_host_only on public.event_chat_rooms;
create policy event_chat_rooms_insert_host_only
on public.event_chat_rooms
for insert
to authenticated
with check (public.is_event_host_or_cohost(event_id, auth.uid()) and created_by = auth.uid());

drop policy if exists event_chat_rooms_update_host_cohost on public.event_chat_rooms;
create policy event_chat_rooms_update_host_cohost
on public.event_chat_rooms
for update
to authenticated
using (public.is_event_host_or_cohost(event_id, auth.uid()))
with check (public.is_event_host_or_cohost(event_id, auth.uid()));

drop policy if exists event_chat_messages_select_visible on public.event_chat_messages;
create policy event_chat_messages_select_visible
on public.event_chat_messages
for select
to authenticated
using (public.can_access_event_chat(room_id, auth.uid()));

drop policy if exists event_chat_messages_insert_allowed on public.event_chat_messages;
create policy event_chat_messages_insert_allowed
on public.event_chat_messages
for insert
to authenticated
with check (
  sender_id = auth.uid()
  and public.can_post_event_chat(room_id, auth.uid())
);

drop policy if exists event_chat_messages_delete_owner_or_host on public.event_chat_messages;
create policy event_chat_messages_delete_owner_or_host
on public.event_chat_messages
for delete
to authenticated
using (
  sender_id = auth.uid()
  or exists (
    select 1
    from public.event_chat_rooms r
    where r.id = room_id
      and public.is_event_host_or_cohost(r.event_id, auth.uid())
  )
);

drop policy if exists dm_conversations_select_participants on public.dm_conversations;
create policy dm_conversations_select_participants
on public.dm_conversations
for select
to authenticated
using (auth.uid() in (user_a, user_b));

drop policy if exists dm_conversations_insert_allowed on public.dm_conversations;
create policy dm_conversations_insert_allowed
on public.dm_conversations
for insert
to authenticated
with check (
  auth.uid() in (user_a, user_b)
  and public.can_create_dm_between(user_a, user_b)
);

drop policy if exists dm_conversations_update_participants on public.dm_conversations;
create policy dm_conversations_update_participants
on public.dm_conversations
for update
to authenticated
using (auth.uid() in (user_a, user_b))
with check (auth.uid() in (user_a, user_b));

drop policy if exists dm_messages_select_participants on public.dm_messages;
create policy dm_messages_select_participants
on public.dm_messages
for select
to authenticated
using (public.is_dm_participant(conversation_id, auth.uid()));

drop policy if exists dm_messages_insert_participants on public.dm_messages;
create policy dm_messages_insert_participants
on public.dm_messages
for insert
to authenticated
with check (
  sender_id = auth.uid()
  and public.is_dm_participant(conversation_id, auth.uid())
);

drop policy if exists dm_messages_delete_sender on public.dm_messages;
create policy dm_messages_delete_sender
on public.dm_messages
for delete
to authenticated
using (sender_id = auth.uid());
