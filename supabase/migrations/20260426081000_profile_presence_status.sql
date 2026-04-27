/*
  Migration purpose:
  Add profile presence state fields so clients can show online/busy/inactive/offline
  indicators consistently across app surfaces.
*/

/*
  Query intent:
  Extend profiles with presence value and timestamp while preserving existing rows.
*/
alter table if exists public.profiles
  add column if not exists presence_status text not null default 'offline',
  add column if not exists presence_updated_at timestamptz not null default now();

/*
  Query intent:
  Add allowed-value guard idempotently for existing deployments.
*/
do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'profiles_presence_status_chk'
  ) then
    alter table public.profiles
      add constraint profiles_presence_status_chk
      check (presence_status in ('online', 'busy', 'inactive', 'offline'));
  end if;
end $$;

/*
  Query intent:
  Backfill null presence values for legacy rows.
*/
update public.profiles
set presence_status = 'offline'
where presence_status is null;
