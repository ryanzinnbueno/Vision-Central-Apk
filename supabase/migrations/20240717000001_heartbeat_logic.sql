-- Migration: Advanced Heartbeat Logic and Monitoring
-- Created at: 2026-07-17

-- 1. Create Heartbeat Audit Table
CREATE TABLE IF NOT EXISTS public.heartbeat_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tv_id UUID REFERENCES public.tvs(id) ON DELETE CASCADE,
    changed_at TIMESTAMPTZ DEFAULT now(),
    old_status TEXT,
    new_status TEXT
);

-- 2. Function to mark timed out TVs as Offline
CREATE OR REPLACE FUNCTION public.handle_tv_timeouts()
RETURNS void AS $$
BEGIN
    -- Insert logs for TVs that are going Offline
    INSERT INTO public.heartbeat_logs (tv_id, old_status, new_status)
    SELECT id, status, 'Offline'
    FROM public.tvs
    WHERE status = 'Online' 
      AND ultima_conexao < (NOW() - INTERVAL '30 seconds');

    -- Update the status
    UPDATE public.tvs
    SET status = 'Offline'
    WHERE status = 'Online'
      AND ultima_conexao < (NOW() - INTERVAL '30 seconds');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. Trigger Function: Automatically set status to Online when heartbeat is received
-- This ensures that even if the app doesn't explicitly send "Online", 
-- updating the timestamp is enough to be marked as active.
CREATE OR REPLACE FUNCTION public.on_heartbeat_received()
RETURNS TRIGGER AS $$
BEGIN
    -- If ultima_conexao was updated, ensure status is Online
    IF NEW.ultima_conexao IS DISTINCT FROM OLD.ultima_conexao THEN
        NEW.status := 'Online';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 4. Create the Trigger
DROP TRIGGER IF EXISTS tr_ensure_online_on_heartbeat ON public.tvs;
CREATE TRIGGER tr_ensure_online_on_heartbeat
BEFORE UPDATE ON public.tvs
FOR EACH ROW
EXECUTE FUNCTION public.on_heartbeat_received();

-- 5. Setup pg_cron (if available)
-- Note: Make sure pg_cron extension is enabled in your Supabase dashboard (Database -> Extensions)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        -- Unschedule if exists to avoid duplicates
        PERFORM cron.unschedule('handle-tv-heartbeat-timeouts');
        
        -- Schedule every minute (pg_cron minimum resolution)
        -- Since the timeout is 30s, running every 60s ensures 
        -- inactive devices are caught quickly.
        PERFORM cron.schedule(
            'handle-tv-heartbeat-timeouts',
            '* * * * *', 
            'SELECT public.handle_tv_timeouts();'
        );
    END IF;
END $$;

-- 6. RPC for Manual Trigger (Optional, for Edge Functions or Admin panel)
CREATE OR REPLACE FUNCTION public.trigger_heartbeat_cleanup()
RETURNS void AS $$
BEGIN
    PERFORM public.handle_tv_timeouts();
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
