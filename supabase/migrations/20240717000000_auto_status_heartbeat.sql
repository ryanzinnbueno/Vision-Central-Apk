-- Migration: Automatic Online/Offline status logic
-- Created at: 2026-07-17

-- 1. Enable pg_cron extension if not already enabled
create extension if not exists pg_cron;

-- 2. Create function to update TV statuses
create or replace function update_tvs_online_status()
returns void as $$
begin
    -- Mark TVs as Offline if last connection was more than 30 seconds ago
    update public.tvs
    set status = 'Offline'
    where ultima_conexao < (now() - interval '30 seconds')
      and status = 'Online';

    -- Mark TVs as Online if last connection was within the last 30 seconds
    -- (The app already sends 'Online' in the heartbeat, but this ensures consistency)
    update public.tvs
    set status = 'Online'
    where ultima_conexao >= (now() - interval '30 seconds')
      and status = 'Offline';
end;
$$ language plpgsql;

-- 3. Schedule the job to run every minute
-- Note: pg_cron minimum interval is 1 minute.
select cron.schedule(
    'update-tv-status-heartbeat',
    '* * * * *', -- Every minute
    'select update_tvs_online_status();'
);

-- 4. Grant access to the function if needed
grant execute on function update_tvs_online_status() to postgres, service_role;
