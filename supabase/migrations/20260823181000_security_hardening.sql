-- Endurece o isolamento do Data API, os privilégios JDBC e invariantes financeiras.

ALTER TABLE public.ticket_comments
    ADD COLUMN IF NOT EXISTS sequence_number
        BIGINT GENERATED ALWAYS AS IDENTITY;

DROP INDEX IF EXISTS public.idx_ticket_comments_ticket_created_at;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ticket_comments_sequence_number
    ON public.ticket_comments (sequence_number);

CREATE INDEX IF NOT EXISTS idx_ticket_comments_ticket_created_sequence
    ON public.ticket_comments (ticket_id, created_at, sequence_number);

ALTER TABLE public.tickets
    ADD CONSTRAINT chk_tickets_aguardando_pagamento_consistente
    CHECK (
        status <> 'AGUARDANDO_PAGAMENTO'
        OR (
            valor_final IS NOT NULL
            AND pagamento_realizado = FALSE
        )
    );

ALTER TABLE public.tickets
    ADD CONSTRAINT chk_tickets_cobranca_pendente_status
    CHECK (
        valor_final IS NULL
        OR pagamento_realizado = TRUE
        OR status = 'AGUARDANDO_PAGAMENTO'
    );

ALTER TABLE public.tickets
    ADD CONSTRAINT chk_tickets_pagamento_confirmado_status
    CHECK (
        valor_final IS NULL
        OR pagamento_realizado = FALSE
        OR status IN ('RESOLVIDO', 'FECHADO')
    );

-- O navegador não usa a Data API. Falha fechada mesmo se uma policy for criada
-- acidentalmente no futuro.
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public
    FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public
    FROM anon, authenticated;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public
    FROM anon, authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE ALL ON TABLES FROM anon, authenticated, speeddesk_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE ALL ON SEQUENCES FROM anon, authenticated, speeddesk_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE EXECUTE ON FUNCTIONS FROM anon, authenticated;

-- O backend recebe somente as operações usadas pelos serviços atuais.
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM speeddesk_app;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM speeddesk_app;

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

GRANT USAGE, SELECT ON SEQUENCE
    public.ticket_comments_sequence_number_seq
TO speeddesk_app;
