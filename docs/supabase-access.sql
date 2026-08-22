-- Execute somente depois de criar o role LOGIN speeddesk_app com uma senha
-- forte fora do Git. Este script não contém nem altera credenciais.

GRANT CONNECT ON DATABASE postgres TO speeddesk_app;
GRANT USAGE ON SCHEMA public TO speeddesk_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public
    TO speeddesk_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO speeddesk_app;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO speeddesk_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO speeddesk_app;

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
