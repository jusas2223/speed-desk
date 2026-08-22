-- Schema PostgreSQL de referência para o estado atual do Speed Desk.

CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_organizations_nome_ci
    ON organizations (LOWER(nome));

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    senha VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
        CHECK (role IN ('CLIENTE', 'TECNICO', 'GERENTE')),
    organization_id UUID,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_users_email_ci
    ON users (LOWER(email));

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_password_reset_tokens_hash_length
        CHECK (CHAR_LENGTH(token_hash) = 64),
    CONSTRAINT chk_password_reset_tokens_expiration
        CHECK (expires_at > created_at),
    CONSTRAINT chk_password_reset_tokens_usage
        CHECK (used_at IS NULL OR used_at >= created_at),
    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    serial_tag VARCHAR(255) NOT NULL UNIQUE,
    modelo VARCHAR(255) NOT NULL,
    tipo VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT fk_assets_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE ticket_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(255) NOT NULL,
    tipo_chamado VARCHAR(50) NOT NULL
        CHECK (tipo_chamado IN ('GERAL', 'HARDWARE', 'SOFTWARE')),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_ticket_categories_tipo_nome_ci
    ON ticket_categories (tipo_chamado, LOWER(nome));

CREATE TABLE sla_policies (
    prioridade VARCHAR(20) PRIMARY KEY
        CHECK (prioridade IN ('BAIXA', 'NORMAL', 'ALTA', 'CRITICA')),
    duracao_minutos INTEGER NOT NULL
        CHECK (duracao_minutos BETWEEN 1 AND 43200),
    alerta_minutos INTEGER NOT NULL
        CHECK (alerta_minutos BETWEEN 0 AND 10080),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
        CHECK (version >= 0),
    CONSTRAINT chk_sla_policies_alerta_menor_duracao
        CHECK (alerta_minutos < duracao_minutos)
);

-- Valores iniciais idempotentes. ON CONFLICT preserva alterações feitas pelo gerente.
INSERT INTO sla_policies (
    prioridade,
    duracao_minutos,
    alerta_minutos
) VALUES
    ('CRITICA', 240, 60),
    ('ALTA', 1440, 240),
    ('NORMAL', 2880, 480),
    ('BAIXA', 4320, 720)
ON CONFLICT (prioridade) DO NOTHING;

CREATE TABLE tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    titulo VARCHAR(255) NOT NULL,
    descricao TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'RECEBIDO'
        CHECK (status IN (
            'RECEBIDO',
            'EM_TRIAGEM',
            'EM_ATENDIMENTO',
            'AGUARDANDO_CLIENTE',
            'AGUARDANDO_PECA',
            'RESOLVIDO',
            'FECHADO'
        )),
    prioridade VARCHAR(50) NOT NULL
        CHECK (prioridade IN ('BAIXA', 'NORMAL', 'ALTA', 'CRITICA')),
    tipo_chamado VARCHAR(50) NOT NULL DEFAULT 'GERAL'
        CHECK (tipo_chamado IN ('GERAL', 'HARDWARE', 'SOFTWARE')),
    cliente_id UUID NOT NULL,
    tecnico_id UUID,
    asset_id UUID,
    category_id UUID,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_vencimento TIMESTAMP WITH TIME ZONE,
    sla_duracao_minutos INTEGER
        CHECK (sla_duracao_minutos IS NULL OR sla_duracao_minutos > 0),
    sla_alerta_minutos INTEGER
        CHECK (sla_alerta_minutos IS NULL OR sla_alerta_minutos >= 0),
    sla_pausado BOOLEAN NOT NULL DEFAULT FALSE,
    sla_pausado_em TIMESTAMP WITH TIME ZONE,
    resolvido_em TIMESTAMP WITH TIME ZONE,
    fechado_em TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
        CHECK (version >= 0),
    CONSTRAINT chk_tickets_sla_alerta_menor_duracao
        CHECK (
            sla_duracao_minutos IS NULL
            OR sla_alerta_minutos IS NULL
            OR sla_alerta_minutos < sla_duracao_minutos
        ),
    CONSTRAINT chk_tickets_sla_pause_consistente
        CHECK (
            (sla_pausado = TRUE AND sla_pausado_em IS NOT NULL)
            OR (sla_pausado = FALSE AND sla_pausado_em IS NULL)
        ),
    CONSTRAINT fk_tickets_cliente
        FOREIGN KEY (cliente_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_tecnico
        FOREIGN KEY (tecnico_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_asset
        FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_category
        FOREIGN KEY (category_id)
        REFERENCES ticket_categories (id) ON DELETE RESTRICT
);

CREATE TABLE ticket_sla_pauses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    pausado_por UUID NOT NULL,
    retomado_por UUID,
    reason VARCHAR(500) NOT NULL,
    pausado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    retomado_em TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
        CHECK (version >= 0),
    CONSTRAINT chk_ticket_sla_pauses_reason
        CHECK (CHAR_LENGTH(BTRIM(reason)) BETWEEN 1 AND 500),
    CONSTRAINT chk_ticket_sla_pauses_retomada_consistente
        CHECK (
            (retomado_em IS NULL AND retomado_por IS NULL)
            OR (
                retomado_em IS NOT NULL
                AND retomado_por IS NOT NULL
                AND retomado_em >= pausado_em
            )
        ),
    CONSTRAINT fk_ticket_sla_pauses_ticket
        FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_sla_pauses_pausado_por
        FOREIGN KEY (pausado_por) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_sla_pauses_retomado_por
        FOREIGN KEY (retomado_por) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE ticket_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    author_id UUID NOT NULL,
    content TEXT NOT NULL,
    internal_note BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ticket_comments_content
        CHECK (CHAR_LENGTH(BTRIM(content)) BETWEEN 1 AND 4000),
    CONSTRAINT fk_ticket_comments_ticket
        FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_comments_author
        FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_users_organization_id
    ON users (organization_id);

CREATE INDEX idx_password_reset_tokens_user_id
    ON password_reset_tokens (user_id);

CREATE INDEX idx_assets_user_id
    ON assets (user_id);

CREATE INDEX idx_tickets_cliente_data_criacao
    ON tickets (cliente_id, data_criacao DESC);

CREATE INDEX idx_tickets_data_criacao
    ON tickets (data_criacao DESC);

CREATE INDEX idx_tickets_tecnico_id
    ON tickets (tecnico_id);

CREATE INDEX idx_tickets_asset_id
    ON tickets (asset_id);

CREATE INDEX idx_tickets_category_id
    ON tickets (category_id);

CREATE INDEX idx_ticket_sla_pauses_ticket_paused_at
    ON ticket_sla_pauses (ticket_id, pausado_em DESC);

CREATE UNIQUE INDEX uq_ticket_sla_pauses_active
    ON ticket_sla_pauses (ticket_id)
    WHERE retomado_em IS NULL;

CREATE INDEX idx_ticket_sla_pauses_pausado_por
    ON ticket_sla_pauses (pausado_por);

CREATE INDEX idx_ticket_sla_pauses_retomado_por
    ON ticket_sla_pauses (retomado_por)
    WHERE retomado_por IS NOT NULL;

CREATE INDEX idx_ticket_comments_ticket_created_at
    ON ticket_comments (ticket_id, created_at, id);

CREATE INDEX idx_ticket_comments_author_id
    ON ticket_comments (author_id);

ALTER TABLE organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE sla_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_sla_pauses ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_comments ENABLE ROW LEVEL SECURITY;

-- Nenhuma policy é criada neste momento. O frontend acessa os dados somente
-- pela API Spring, e o acesso pela Data API do Supabase deve permanecer
-- bloqueado. Em especial, hashes de recuperação, notas internas e registros
-- operacionais de pausa nunca devem ser expostos ao navegador pela Data API.
-- Se o acesso direto pelo frontend for aprovado futuramente, as policies e os
-- grants mínimos deverão ser definidos antes da habilitação.
