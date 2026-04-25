-- Supabase/Postgres only. Apply in the Supabase SQL editor or CLI.
-- Seeds real users/profiles so "Find People" returns non-dummy rows.
-- Idempotent: safe to run multiple times.

do $$
declare
  alex_id uuid := '7f7d6a91-0c6a-4b33-a6ef-6f778cb97801';
  sam_id uuid := 'f56d6d4d-208a-4cf8-976d-5f90a5f94652';
  river_id uuid := '11b9fbad-77e8-4b5a-9a95-26faa4ce6ec0';
begin
  -- Align auth signup trigger with current profiles schema (`name` over `full_name`).
  create or replace function public.handle_new_user()
  returns trigger
  language plpgsql
  security definer
  set search_path = public
  as $fn$
  begin
    insert into public.profiles (id, email, username, name)
    values (
      new.id,
      new.email,
      nullif(new.raw_user_meta_data ->> 'username', ''),
      coalesce(
        nullif(new.raw_user_meta_data ->> 'name', ''),
        nullif(new.raw_user_meta_data ->> 'full_name', ''),
        nullif(new.email, '')
      )
    )
    on conflict (id) do nothing;
    return new;
  end;
  $fn$;

  -- Seed auth users first (profiles.id has FK to auth.users.id).
  insert into auth.users (
    id,
    aud,
    role,
    email,
    encrypted_password,
    email_confirmed_at,
    raw_app_meta_data,
    raw_user_meta_data,
    created_at,
    updated_at
  ) values
    (
      alex_id,
      'authenticated',
      'authenticated',
      'alex.seed@spacer.app',
      crypt('SpacerSeed123!', gen_salt('bf')),
      now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"name":"Alex Seed","username":"alex_seed"}'::jsonb,
      now(),
      now()
    ),
    (
      sam_id,
      'authenticated',
      'authenticated',
      'sam.seed@spacer.app',
      crypt('SpacerSeed123!', gen_salt('bf')),
      now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"name":"Sam Seed","username":"sam_seed"}'::jsonb,
      now(),
      now()
    ),
    (
      river_id,
      'authenticated',
      'authenticated',
      'river.seed@spacer.app',
      crypt('SpacerSeed123!', gen_salt('bf')),
      now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"name":"River Seed","username":"river_seed"}'::jsonb,
      now(),
      now()
    )
  on conflict (id) do update
    set
      email = excluded.email,
      raw_user_meta_data = excluded.raw_user_meta_data,
      updated_at = now();

  -- Upsert profile details used by app search/public profile screens.
  insert into public.profiles (
    id,
    email,
    username,
    name,
    about_me,
    avatar_url
  ) values
    (
      alex_id,
      'alex.seed@spacer.app',
      'alex_seed',
      'Alex Seed',
      'Always up for coffee chats and city walks.',
      null
    ),
    (
      sam_id,
      'sam.seed@spacer.app',
      'sam_seed',
      'Sam Seed',
      'Planning game nights and casual weekend hangs.',
      null
    ),
    (
      river_id,
      'river.seed@spacer.app',
      'river_seed',
      'River Seed',
      'Looking for live music and art meetups.',
      null
    )
  on conflict (id) do update
    set
      email = excluded.email,
      username = excluded.username,
      name = excluded.name,
      about_me = excluded.about_me,
      avatar_url = excluded.avatar_url;
end $$;

