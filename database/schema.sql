-- Core profile row keyed by Supabase auth user id.
create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    email text unique,
    username text,
    full_name text,
    avatar_url text,
    about_me text default '',
    auth_provider text default 'email',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Track the profile counters requested in the app UI.
create table if not exists public.user_stats (
    user_id uuid primary key references public.profiles(id) on delete cascade,
    hosted_count integer not null default 0 check (hosted_count >= 0),
    attended_count integer not null default 0 check (attended_count >= 0),
    friends_count integer not null default 0 check (friends_count >= 0),
    updated_at timestamptz not null default now()
);

-- Keep latest user location label and coordinates for "current location" display.
create table if not exists public.user_locations (
    user_id uuid primary key references public.profiles(id) on delete cascade,
    city text,
    postal_code text,
    latitude double precision,
    longitude double precision,
    formatted_label text,
    updated_at timestamptz not null default now()
);

-- Auto-create profile + zero stats when a new auth user is created.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, email, full_name, auth_provider)
    values (
        new.id,
        new.email,
        coalesce(new.raw_user_meta_data ->> 'name', new.raw_user_meta_data ->> 'full_name', ''),
        coalesce(new.app_metadata ->> 'provider', 'email')
    )
    on conflict (id) do nothing;

    insert into public.user_stats (user_id)
    values (new.id)
    on conflict (user_id) do nothing;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

-- Recommended RLS policies.
alter table public.profiles enable row level security;
alter table public.user_stats enable row level security;
alter table public.user_locations enable row level security;

drop policy if exists "Users can read own profile" on public.profiles;
create policy "Users can read own profile"
on public.profiles for select
using (auth.uid() = id);

drop policy if exists "Users can update own profile" on public.profiles;
create policy "Users can update own profile"
on public.profiles for update
using (auth.uid() = id);

drop policy if exists "Users can read own stats" on public.user_stats;
create policy "Users can read own stats"
on public.user_stats for select
using (auth.uid() = user_id);

drop policy if exists "Users can update own stats" on public.user_stats;
create policy "Users can update own stats"
on public.user_stats for update
using (auth.uid() = user_id);

drop policy if exists "Users can read own location" on public.user_locations;
create policy "Users can read own location"
on public.user_locations for select
using (auth.uid() = user_id);

drop policy if exists "Users can upsert own location" on public.user_locations;
create policy "Users can upsert own location"
on public.user_locations for insert
with check (auth.uid() = user_id);

drop policy if exists "Users can update own location" on public.user_locations;
create policy "Users can update own location"
on public.user_locations for update
using (auth.uid() = user_id);
