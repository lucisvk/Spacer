/*
  Migration purpose:
  Add availability and venue-hours primitives used for invite conflict checks:
  - user availability preferences
  - recurring weekly windows
  - specific-date overrides
  - event location open hours
*/

/*
  Table intent:
  user_availability_preferences stores account-level availability/privacy defaults.
*/
create table if not exists public.user_availability_preferences (
  user_id uuid primary key references auth.users(id) on delete cascade,
  calendar_provider text null,
  calendar_connected boolean not null default false,
  show_to_friends_only boolean not null default true,
  auto_decline_conflicts boolean not null default false,
  updated_at timestamptz not null default now()
);

/*
  Table intent:
  user_weekly_availability_windows stores recurring day-of-week free windows.
*/
create table if not exists public.user_weekly_availability_windows (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  day_of_week int not null check (day_of_week between 1 and 7),
  starts_at time not null,
  ends_at time not null,
  created_at timestamptz not null default now()
);

/*
  Query intent:
  Optimize weekly availability lookups by user/day.
*/
create index if not exists idx_user_weekly_windows_user_day
  on public.user_weekly_availability_windows(user_id, day_of_week);

/*
  Table intent:
  user_specific_availability stores one-off date-range overrides.
*/
create table if not exists public.user_specific_availability (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  is_available boolean not null default true,
  note text null,
  created_at timestamptz not null default now()
);

/*
  Query intent:
  Optimize specific-date availability scans by user and start time.
*/
create index if not exists idx_user_specific_availability_user_starts
  on public.user_specific_availability(user_id, starts_at);

/*
  Table intent:
  event_location_open_hours stores venue operating windows for conflict checks.
*/
create table if not exists public.event_location_open_hours (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null references public.app_events(id) on delete cascade,
  day_of_week int not null check (day_of_week between 1 and 7),
  opens_at time not null,
  closes_at time not null,
  is_closed boolean not null default false,
  created_at timestamptz not null default now()
);

/*
  Query intent:
  Optimize venue-hours lookups by event/day.
*/
create index if not exists idx_event_location_open_hours_event_day
  on public.event_location_open_hours(event_id, day_of_week);

/*
  RLS section intent:
  User-owned tables are self-managed; event location hours are host-managed.
*/
alter table public.user_availability_preferences enable row level security;
alter table public.user_weekly_availability_windows enable row level security;
alter table public.user_specific_availability enable row level security;
alter table public.event_location_open_hours enable row level security;

drop policy if exists user_availability_preferences_select_own on public.user_availability_preferences;
create policy user_availability_preferences_select_own
on public.user_availability_preferences
for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists user_availability_preferences_upsert_own on public.user_availability_preferences;
create policy user_availability_preferences_upsert_own
on public.user_availability_preferences
for all
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists user_weekly_windows_select_own on public.user_weekly_availability_windows;
create policy user_weekly_windows_select_own
on public.user_weekly_availability_windows
for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists user_weekly_windows_manage_own on public.user_weekly_availability_windows;
create policy user_weekly_windows_manage_own
on public.user_weekly_availability_windows
for all
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists user_specific_availability_select_own on public.user_specific_availability;
create policy user_specific_availability_select_own
on public.user_specific_availability
for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists user_specific_availability_manage_own on public.user_specific_availability;
create policy user_specific_availability_manage_own
on public.user_specific_availability
for all
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists event_location_open_hours_select_visible on public.event_location_open_hours;
create policy event_location_open_hours_select_visible
on public.event_location_open_hours
for select
to authenticated
using (
  exists (
    select 1
    from public.app_events ev
    where ev.id = event_location_open_hours.event_id
  )
);

drop policy if exists event_location_open_hours_manage_host on public.event_location_open_hours;
create policy event_location_open_hours_manage_host
on public.event_location_open_hours
for all
to authenticated
using (
  exists (
    select 1
    from public.app_events ev
    where ev.id = event_location_open_hours.event_id
      and ev.host_id = auth.uid()
  )
)
with check (
  exists (
    select 1
    from public.app_events ev
    where ev.id = event_location_open_hours.event_id
      and ev.host_id = auth.uid()
  )
);
