/*
  Migration purpose:
  Add calendar-overlap signal to event availability records so hosts can distinguish
  schedule conflicts from generic declines.

  Execution context:
  Run in Supabase/Postgres SQL editor or migration CLI.
*/

/*
  Query intent:
  Extend event_availability with a non-nullable flag that defaults to false so
  existing rows remain valid and app logic can rely on a deterministic value.
*/
alter table if exists public.event_availability
  add column if not exists calendar_busy_overlaps_event boolean not null default false;
