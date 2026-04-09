-- Run in Supabase SQL Editor. Depends on public.app_events(id uuid PK).
-- Lets the app list "public" discoverable events and read those rows for any signed-in user.
--
-- RLS note: INSERT policies that do EXISTS (SELECT … FROM app_events) recurse with
-- app_events policies that reference public_event_invites. Use SECURITY DEFINER helpers
-- so host checks read app_events without re-entering RLS.

-- Host check bypasses RLS (runs as function owner, typically postgres in Supabase).
create or replace function public.is_app_event_host(p_event_id uuid, p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.app_events e
    where e.id = p_event_id and e.host_id = p_user_id
  );
$$;

revoke all on function public.is_app_event_host(uuid, uuid) from public;
grant execute on function public.is_app_event_host(uuid, uuid) to authenticated;

create table if not exists public.public_event_invites (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.app_events (id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (event_id)
);

create index if not exists public_event_invites_event_idx on public.public_event_invites (event_id);

alter table public.public_event_invites enable row level security;

grant select, insert, delete on table public.public_event_invites to authenticated;

drop policy if exists "public_event_invites_select_all" on public.public_event_invites;
create policy "public_event_invites_select_all"
  on public.public_event_invites for select to authenticated
  using (true);

drop policy if exists "public_event_invites_insert_host" on public.public_event_invites;
create policy "public_event_invites_insert_host"
  on public.public_event_invites for insert to authenticated
  with check (public.is_app_event_host(public_event_invites.event_id, auth.uid()));

drop policy if exists "public_event_invites_delete_host" on public.public_event_invites;
create policy "public_event_invites_delete_host"
  on public.public_event_invites for delete to authenticated
  using (public.is_app_event_host(public_event_invites.event_id, auth.uid()));

-- Allow any authenticated user to read app_events rows that are publicly listed.
drop policy if exists "app_events_select_public_listing" on public.app_events;
create policy "app_events_select_public_listing"
  on public.app_events for select to authenticated
  using (
    exists (
      select 1 from public.public_event_invites p
      where p.event_id = app_events.id
    )
  );
