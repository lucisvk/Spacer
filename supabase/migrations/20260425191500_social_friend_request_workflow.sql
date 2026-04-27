/*
  Migration purpose:
  Normalize social relationship primitives used by the app:
  friend requests, user blocks, and user reports.

  Execution context:
  Run in Supabase/Postgres SQL editor or migration CLI.
*/

/*
  Table intent:
  friend_requests stores directional request lifecycle state between two users.
*/
create table if not exists public.friend_requests (
  sender_id uuid not null references auth.users(id) on delete cascade,
  receiver_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'pending',
  created_at timestamptz not null default now(),
  responded_at timestamptz null,
  constraint friend_requests_sender_receiver_pk primary key (sender_id, receiver_id),
  constraint friend_requests_status_chk check (status in ('pending', 'accepted', 'declined'))
);

/*
  Query intent:
  Speed receiver inbox queries by status (pending/accepted/declined).
*/
create index if not exists idx_friend_requests_receiver_status
  on public.friend_requests (receiver_id, status);

/*
  Query intent:
  Speed sender-side request history filtering by status.
*/
create index if not exists idx_friend_requests_sender_status
  on public.friend_requests (sender_id, status);

/*
  Query intent:
  Add no-self-request guard idempotently for existing environments.
*/
do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'friend_requests_no_self_chk'
  ) then
    alter table public.friend_requests
      add constraint friend_requests_no_self_chk check (sender_id <> receiver_id);
  end if;
end $$;

/*
  Table intent:
  user_blocks is an explicit privacy boundary table for mutual visibility checks.
*/
create table if not exists public.user_blocks (
  blocker_id uuid not null references auth.users(id) on delete cascade,
  blocked_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  constraint user_blocks_pk primary key (blocker_id, blocked_id),
  constraint user_blocks_no_self_chk check (blocker_id <> blocked_id)
);

/*
  Query intent:
  Speed reverse block lookups (who blocked this user).
*/
create index if not exists idx_user_blocks_blocked_id
  on public.user_blocks (blocked_id);

/*
  Table intent:
  user_reports records safety reports for moderation workflows.
*/
create table if not exists public.user_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references auth.users(id) on delete cascade,
  reported_id uuid not null references auth.users(id) on delete cascade,
  reason text not null,
  created_at timestamptz not null default now(),
  constraint user_reports_no_self_chk check (reporter_id <> reported_id)
);

/*
  Query intent:
  Speed moderation feeds by reported user and newest-first ordering.
*/
create index if not exists idx_user_reports_reported_id
  on public.user_reports (reported_id, created_at desc);

