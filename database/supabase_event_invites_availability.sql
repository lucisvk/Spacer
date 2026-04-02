-- Run once in Supabase → SQL Editor. Safe to re-run: drops policies before recreating.
-- Requires: public.app_events(id uuid PK), auth.users.

-- ---------------------------------------------------------------------------
-- public.event_invites  (invitations for events)
-- ---------------------------------------------------------------------------
create table if not exists public.event_invites (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.app_events (id) on delete cascade,
  invitee_id uuid not null references auth.users (id) on delete cascade,
  status text not null default 'pending'
    check (status in ('pending', 'accepted', 'declined', 'maybe')),
  created_at timestamptz not null default now(),
  unique (event_id, invitee_id)
);

create index if not exists event_invites_invitee_idx on public.event_invites (invitee_id);
create index if not exists event_invites_event_idx on public.event_invites (event_id);

alter table public.event_invites enable row level security;

-- Grants (RLS still applies)
grant select, insert, update, delete on table public.event_invites to authenticated;

drop policy if exists "invites_select_invitee" on public.event_invites;
drop policy if exists "invites_select_host" on public.event_invites;
drop policy if exists "invites_insert_host" on public.event_invites;
drop policy if exists "invites_update_invitee" on public.event_invites;

create policy "invites_select_invitee"
  on public.event_invites for select to authenticated
  using (invitee_id = auth.uid());

create policy "invites_select_host"
  on public.event_invites for select to authenticated
  using (
    exists (
      select 1 from public.app_events e
      where e.id = event_invites.event_id and e.host_id = auth.uid()
    )
  );

create policy "invites_insert_host"
  on public.event_invites for insert to authenticated
  with check (
    exists (
      select 1 from public.app_events e
      where e.id = event_invites.event_id and e.host_id = auth.uid()
    )
  );

create policy "invites_update_invitee"
  on public.event_invites for update to authenticated
  using (invitee_id = auth.uid())
  with check (invitee_id = auth.uid());

-- ---------------------------------------------------------------------------
-- public.event_availability
-- ---------------------------------------------------------------------------
create table if not exists public.event_availability (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.app_events (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  preset_slots text not null default '',
  notes text,
  updated_at timestamptz not null default now(),
  unique (event_id, user_id)
);

create index if not exists event_availability_event_idx on public.event_availability (event_id);

alter table public.event_availability enable row level security;

grant select, insert, update, delete on table public.event_availability to authenticated;

drop policy if exists "availability_select_self" on public.event_availability;
drop policy if exists "availability_select_host" on public.event_availability;
drop policy if exists "availability_upsert_self" on public.event_availability;
drop policy if exists "availability_update_self" on public.event_availability;

create policy "availability_select_self"
  on public.event_availability for select to authenticated
  using (user_id = auth.uid());

create policy "availability_select_host"
  on public.event_availability for select to authenticated
  using (
    exists (
      select 1 from public.app_events e
      where e.id = event_availability.event_id and e.host_id = auth.uid()
    )
  );

create policy "availability_upsert_self"
  on public.event_availability for insert to authenticated
  with check (user_id = auth.uid());

create policy "availability_update_self"
  on public.event_availability for update to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- Invitees can read the app_events row they’re invited to
-- ---------------------------------------------------------------------------
drop policy if exists "app_events_select_invited" on public.app_events;
create policy "app_events_select_invited"
  on public.app_events for select to authenticated
  using (
    exists (
      select 1 from public.event_invites i
      where i.event_id = app_events.id and i.invitee_id = auth.uid()
    )
  );
