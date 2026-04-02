-- Run in Supabase SQL Editor so logged-in users can discover other profiles (Find people / invites).
-- If you already have a permissive SELECT on public.profiles, you can skip this.

-- Example: only add if searches return 0 rows due to RLS.
-- Review existing policies on public.profiles before running.

drop policy if exists "profiles_select_authenticated_discovery" on public.profiles;

create policy "profiles_select_authenticated_discovery"
  on public.profiles
  for select
  to authenticated
  using (true);

-- Optional: help ilike on username / name (uncomment if tables are large)
-- create index if not exists profiles_username_lower_idx on public.profiles (lower(username));
-- create index if not exists profiles_name_lower_idx on public.profiles (lower(name));
