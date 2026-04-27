/*
  Migration purpose:
  Create user_notifications and enforce per-user read/update access.
  This table powers app-side polling and device notification posting.
*/

/*
  Table intent:
  user_notifications stores pending and read notification payloads.
*/
create table if not exists public.user_notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null,
  body text not null,
  created_at timestamptz not null default now(),
  read_at timestamptz null
);

/*
  Query intent:
  Optimize unread feed scans by user and newest-first order.
*/
create index if not exists idx_user_notifications_user_created
  on public.user_notifications (user_id, created_at desc);

/*
  RLS section intent:
  Restrict reads/updates to owner while allowing authenticated inserts.
*/
alter table public.user_notifications enable row level security;

drop policy if exists user_notifications_select_own on public.user_notifications;
create policy user_notifications_select_own
on public.user_notifications
for select
to authenticated
using (auth.uid() = user_id);

drop policy if exists user_notifications_update_own on public.user_notifications;
create policy user_notifications_update_own
on public.user_notifications
for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists user_notifications_insert_authenticated on public.user_notifications;
create policy user_notifications_insert_authenticated
on public.user_notifications
for insert
to authenticated
with check (true);
