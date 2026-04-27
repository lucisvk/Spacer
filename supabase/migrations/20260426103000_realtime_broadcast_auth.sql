/*
  Migration purpose:
  Authorize Realtime topic subscription scope and attach DB triggers that broadcast
  event-chat and DM message mutations through Supabase Realtime.
*/

/*
  Query intent:
  Ensure realtime.messages is protected by RLS before policy creation.
*/
alter table if exists realtime.messages enable row level security;

/*
  Policy intent:
  Restrict realtime topic reads to chat participants only.
*/
drop policy if exists realtime_messages_select_chat_scoped on realtime.messages;
create policy realtime_messages_select_chat_scoped
on realtime.messages
for select
to authenticated
using (
  (
    split_part(realtime.topic(), ':', 1) = 'event_chat'
    and exists (
      select 1
      from public.event_chat_rooms r
      where r.id = split_part(realtime.topic(), ':', 2)::uuid
        and public.can_access_event_chat(r.id, auth.uid())
    )
  )
  or
  (
    split_part(realtime.topic(), ':', 1) = 'dm_chat'
    and public.is_dm_participant(split_part(realtime.topic(), ':', 2)::uuid, auth.uid())
  )
);

/*
  Function intent:
  Broadcast event chat row mutations to event_chat:<room_id> topic.
*/
create or replace function public.broadcast_event_chat_message_changes()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  topic text;
begin
  topic := 'event_chat:' || coalesce(new.room_id, old.room_id)::text;
  perform realtime.broadcast_changes(
    topic,
    TG_OP,
    TG_OP,
    TG_TABLE_NAME,
    TG_TABLE_SCHEMA,
    NEW,
    OLD
  );
  return coalesce(NEW, OLD);
end;
$$;

/*
  Query intent:
  Attach trigger to event_chat_messages for insert/update/delete broadcasts.
*/
drop trigger if exists trg_broadcast_event_chat_messages on public.event_chat_messages;
create trigger trg_broadcast_event_chat_messages
after insert or update or delete on public.event_chat_messages
for each row
execute function public.broadcast_event_chat_message_changes();

/*
  Function intent:
  Broadcast DM row mutations to dm_chat:<conversation_id> topic.
*/
create or replace function public.broadcast_dm_message_changes()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  topic text;
begin
  topic := 'dm_chat:' || coalesce(new.conversation_id, old.conversation_id)::text;
  perform realtime.broadcast_changes(
    topic,
    TG_OP,
    TG_OP,
    TG_TABLE_NAME,
    TG_TABLE_SCHEMA,
    NEW,
    OLD
  );
  return coalesce(NEW, OLD);
end;
$$;

/*
  Query intent:
  Attach trigger to dm_messages for insert/update/delete broadcasts.
*/
drop trigger if exists trg_broadcast_dm_messages on public.dm_messages;
create trigger trg_broadcast_dm_messages
after insert or update or delete on public.dm_messages
for each row
execute function public.broadcast_dm_message_changes();
