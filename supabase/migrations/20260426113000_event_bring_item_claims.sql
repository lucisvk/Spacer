/*
  Migration purpose:
  Add structured claim tracking for per-item "what to bring" commitments.
*/

/*
  Table intent:
  event_bring_item_claims stores one active claim per event item key.
*/
create table if not exists public.event_bring_item_claims (
  event_id uuid not null references public.app_events(id) on delete cascade,
  item_key text not null,
  item_label text not null,
  claimed_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint event_bring_item_claims_pk primary key (event_id, item_key),
  constraint event_bring_item_claims_item_key_chk check (length(trim(item_key)) > 0),
  constraint event_bring_item_claims_item_label_chk check (length(trim(item_label)) > 0)
);

/*
  Query intent:
  Optimize event-level claim reads.
*/
create index if not exists idx_event_bring_item_claims_event
  on public.event_bring_item_claims (event_id);

/*
  Query intent:
  Optimize user-level claim ownership lookups.
*/
create index if not exists idx_event_bring_item_claims_claimed_by
  on public.event_bring_item_claims (claimed_by);

/*
  RLS section intent:
  Restrict claim visibility and mutation to event participants/hosts.
*/
alter table if exists public.event_bring_item_claims enable row level security;

drop policy if exists event_bring_item_claims_select_visible on public.event_bring_item_claims;
create policy event_bring_item_claims_select_visible
on public.event_bring_item_claims
for select
to authenticated
using (
  public.is_event_member_active(event_id, auth.uid())
  or public.is_event_host_or_cohost(event_id, auth.uid())
);

drop policy if exists event_bring_item_claims_insert_member on public.event_bring_item_claims;
create policy event_bring_item_claims_insert_member
on public.event_bring_item_claims
for insert
to authenticated
with check (
  claimed_by = auth.uid()
  and (
    public.is_event_member_active(event_id, auth.uid())
    or public.is_event_host_or_cohost(event_id, auth.uid())
  )
);

drop policy if exists event_bring_item_claims_delete_owner_or_host on public.event_bring_item_claims;
create policy event_bring_item_claims_delete_owner_or_host
on public.event_bring_item_claims
for delete
to authenticated
using (
  claimed_by = auth.uid()
  or public.is_event_host_or_cohost(event_id, auth.uid())
);
