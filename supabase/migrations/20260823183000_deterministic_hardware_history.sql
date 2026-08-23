-- Mantém a ordem de eventos técnicos determinística quando o relógio empata.

ALTER TABLE public.hardware_maintenance_history
    ADD COLUMN IF NOT EXISTS sequence_number
        BIGINT GENERATED ALWAYS AS IDENTITY;

DROP INDEX IF EXISTS
    public.idx_hardware_maintenance_history_ticket_created_at;

CREATE UNIQUE INDEX IF NOT EXISTS
    uq_hardware_maintenance_history_sequence_number
    ON public.hardware_maintenance_history (sequence_number);

CREATE INDEX IF NOT EXISTS
    idx_hardware_maintenance_history_ticket_created_sequence
    ON public.hardware_maintenance_history (
        ticket_id,
        created_at DESC,
        sequence_number DESC
    );

GRANT USAGE, SELECT ON SEQUENCE
    public.hardware_maintenance_history_sequence_number_seq
TO speeddesk_app;
