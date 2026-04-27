/*
  Migration purpose:
  Guard Realtime topic UUID casts to prevent "invalid input syntax for type uuid"
  when non-chat topics are evaluated under realtime.messages RLS.
*/

/*
  Policy intent:
  Recreate chat-scoped topic policy with UUID-shape guards before casting.
*/
drop policy if exists realtime_messages_select_chat_scoped on realtime.messages;

create policy realtime_messages_select_chat_scoped
on realtime.messages
for select
to authenticated
using (
  (
    split_part(realtime.topic(), ':', 1) = 'event_chat'
    and split_part(realtime.topic(), ':', 2) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
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
    and split_part(realtime.topic(), ':', 2) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    and public.is_dm_participant(split_part(realtime.topic(), ':', 2)::uuid, auth.uid())
  )
);
