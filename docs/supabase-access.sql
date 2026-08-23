-- Execute somente depois de criar o role LOGIN speeddesk_app com uma senha
-- forte fora do Git. Este script não contém nem altera credenciais.

GRANT CONNECT ON DATABASE postgres TO speeddesk_app;
GRANT USAGE ON SCHEMA public TO speeddesk_app;

REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public
    FROM anon, authenticated, speeddesk_app;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public
    FROM anon, authenticated, speeddesk_app;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public
    FROM anon, authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE ALL ON TABLES FROM anon, authenticated, speeddesk_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE ALL ON SEQUENCES FROM anon, authenticated, speeddesk_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE EXECUTE ON FUNCTIONS FROM anon, authenticated;

GRANT SELECT ON TABLE
    public.organizations,
    public.ticket_categories,
    public.sla_policies
TO speeddesk_app;
GRANT SELECT, UPDATE ON TABLE public.users TO speeddesk_app;
GRANT SELECT, INSERT, UPDATE ON TABLE
    public.assets,
    public.tickets,
    public.ticket_sla_pauses,
    public.hardware_ticket_details,
    public.hardware_post_repair_checklists,
    public.ticket_software_details,
    public.incidents,
    public.notifications,
    public.password_reset_tokens
TO speeddesk_app;
GRANT SELECT, INSERT ON TABLE
    public.ticket_comments,
    public.hardware_maintenance_history,
    public.ticket_software_logs
TO speeddesk_app;
GRANT SELECT, INSERT, DELETE ON TABLE public.incident_tickets TO speeddesk_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.idempotency_records
    TO speeddesk_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO speeddesk_app;

DO $speeddesk_policies$
DECLARE
    app_table RECORD;
BEGIN
    FOR app_table IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_policies
            WHERE schemaname = 'public'
              AND tablename = app_table.tablename
              AND policyname = 'speeddesk_backend_access'
        ) THEN
            EXECUTE format(
                'CREATE POLICY speeddesk_backend_access ON public.%I TO speeddesk_app USING (true) WITH CHECK (true)',
                app_table.tablename
            );
        END IF;
    END LOOP;
END
$speeddesk_policies$;
