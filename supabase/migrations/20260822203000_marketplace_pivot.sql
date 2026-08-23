-- Converte o Speed Desk para o modelo de marketplace com dois perfis.

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS telefone VARCHAR(20);

UPDATE public.users
   SET role = 'TECNICO'
 WHERE role = 'GERENTE';

ALTER TABLE public.users
    DROP CONSTRAINT IF EXISTS users_role_check1;

ALTER TABLE public.users
    ADD CONSTRAINT chk_users_role
    CHECK (role IN ('CLIENTE', 'TECNICO'));

ALTER TABLE public.users
    ADD CONSTRAINT chk_users_telefone
    CHECK (
        telefone IS NULL
        OR telefone ~ '^[1-9][0-9]{9,14}$'
    );

ALTER TABLE public.tickets
    ADD COLUMN IF NOT EXISTS valor_final NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS pagamento_realizado BOOLEAN NOT NULL DEFAULT FALSE;

-- Chamados concluídos antes do pivot permanecem encerráveis e não geram dívida.
UPDATE public.tickets
   SET pagamento_realizado = TRUE
 WHERE status IN ('RESOLVIDO', 'FECHADO');

ALTER TABLE public.tickets
    DROP CONSTRAINT IF EXISTS tickets_status_check1;

ALTER TABLE public.tickets
    ADD CONSTRAINT chk_tickets_status
    CHECK (status IN (
        'RECEBIDO',
        'EM_TRIAGEM',
        'EM_ATENDIMENTO',
        'AGUARDANDO_CLIENTE',
        'AGUARDANDO_PECA',
        'AGUARDANDO_PAGAMENTO',
        'RESOLVIDO',
        'FECHADO'
    ));

ALTER TABLE public.tickets
    ADD CONSTRAINT chk_tickets_valor_final
    CHECK (valor_final IS NULL OR valor_final > 0);

CREATE INDEX IF NOT EXISTS idx_tickets_cliente_pagamento_pendente
    ON public.tickets (cliente_id)
    WHERE status = 'AGUARDANDO_PAGAMENTO'
       OR (valor_final IS NOT NULL AND pagamento_realizado = FALSE);
