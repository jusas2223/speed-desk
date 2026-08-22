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
    serial_tag VARCHAR(255) NOT NULL,
    modelo VARCHAR(255) NOT NULL,
    fabricante VARCHAR(255),
    tipo VARCHAR(50) NOT NULL
        CHECK (tipo IN (
            'NOTEBOOK',
            'DESKTOP',
            'MONITOR',
            'IMPRESSORA',
            'SERVIDOR',
            'EQUIPAMENTO_REDE',
            'PERIFERICO',
            'OUTRO'
        )),
    status VARCHAR(50) NOT NULL DEFAULT 'ATIVO'
        CHECK (status IN ('ATIVO', 'EM_MANUTENCAO', 'INATIVO', 'DESCARTADO')),
    data_compra DATE,
    garantia_fim DATE,
    fornecedor_garantia VARCHAR(255),
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
        CHECK (version >= 0),
    CONSTRAINT chk_assets_serial_tag
        CHECK (CHAR_LENGTH(BTRIM(serial_tag)) BETWEEN 1 AND 255),
    CONSTRAINT chk_assets_modelo
        CHECK (CHAR_LENGTH(BTRIM(modelo)) BETWEEN 1 AND 255),
    CONSTRAINT chk_assets_garantia_datas
        CHECK (
            data_compra IS NULL
            OR garantia_fim IS NULL
            OR garantia_fim >= data_compra
        ),
    CONSTRAINT fk_assets_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_assets_serial_tag_ci
    ON assets (LOWER(serial_tag));

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

CREATE TABLE hardware_ticket_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL UNIQUE,
    eligibility_status VARCHAR(30) NOT NULL DEFAULT 'PENDENTE'
        CHECK (eligibility_status IN ('PENDENTE', 'ELEGIVEL', 'NAO_ELEGIVEL')),
    warranty_coverage VARCHAR(30) NOT NULL DEFAULT 'NAO_AVALIADA'
        CHECK (warranty_coverage IN ('NAO_AVALIADA', 'COBERTA', 'NAO_COBERTA')),
    eligibility_notes TEXT,
    maintenance_stage VARCHAR(30) NOT NULL DEFAULT 'RECEBIDO'
        CHECK (maintenance_stage IN (
            'RECEBIDO',
            'EM_ANALISE',
            'EM_REPARO',
            'EM_TESTE',
            'CONCLUIDO'
        )),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
        CHECK (version >= 0),
    CONSTRAINT chk_hardware_ticket_details_notes
        CHECK (
            eligibility_notes IS NULL
            OR CHAR_LENGTH(eligibility_notes) <= 2000
        ),
    CONSTRAINT chk_hardware_ticket_details_timestamps
        CHECK (updated_at >= created_at),
    CONSTRAINT fk_hardware_ticket_details_ticket
        FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE
);

CREATE TABLE hardware_maintenance_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    entry_type VARCHAR(30) NOT NULL
        CHECK (entry_type IN ('ETAPA', 'MANUTENCAO', 'CHECKLIST')),
    maintenance_stage VARCHAR(30) NOT NULL
        CHECK (maintenance_stage IN (
            'RECEBIDO',
            'EM_ANALISE',
            'EM_REPARO',
            'EM_TESTE',
            'CONCLUIDO'
        )),
    description TEXT NOT NULL,
    performed_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_hardware_maintenance_history_description
        CHECK (CHAR_LENGTH(BTRIM(description)) BETWEEN 1 AND 4000),
    CONSTRAINT fk_hardware_maintenance_history_ticket
        FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_hardware_maintenance_history_performed_by
        FOREIGN KEY (performed_by) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE hardware_post_repair_checklists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL UNIQUE,
    equipment_turns_on BOOLEAN NOT NULL DEFAULT FALSE,
    functionality_validated BOOLEAN NOT NULL DEFAULT FALSE,
    connectivity_validated BOOLEAN NOT NULL DEFAULT FALSE,
    cleaning_completed BOOLEAN NOT NULL DEFAULT FALSE,
    client_data_preserved BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    completed_at TIMESTAMP WITH TIME ZONE,
    completed_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
        CHECK (version >= 0),
    CONSTRAINT chk_hardware_post_repair_checklists_notes
        CHECK (notes IS NULL OR CHAR_LENGTH(notes) <= 2000),
    CONSTRAINT chk_hardware_post_repair_checklists_completion
        CHECK (
            (completed_at IS NULL AND completed_by IS NULL)
            OR (completed_at IS NOT NULL AND completed_by IS NOT NULL)
        ),
    CONSTRAINT fk_hardware_post_repair_checklists_ticket
        FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_hardware_post_repair_checklists_completed_by
        FOREIGN KEY (completed_by) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE ticket_software_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL UNIQUE,
    software_version VARCHAR(120) NOT NULL,
    affected_environment VARCHAR(30) NOT NULL
        CHECK (affected_environment IN (
            'PRODUCAO',
            'HOMOLOGACAO',
            'DESENVOLVIMENTO',
            'TESTE',
            'OUTRO'
        )),
    platform VARCHAR(160) NOT NULL,
    operating_system VARCHAR(160) NOT NULL,
    reproduction_steps TEXT NOT NULL,
    expected_result TEXT NOT NULL,
    actual_result TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
        CHECK (version >= 0),
    CONSTRAINT chk_ticket_software_details_version
        CHECK (CHAR_LENGTH(BTRIM(software_version)) BETWEEN 1 AND 120),
    CONSTRAINT chk_ticket_software_details_platform
        CHECK (CHAR_LENGTH(BTRIM(platform)) BETWEEN 1 AND 160),
    CONSTRAINT chk_ticket_software_details_operating_system
        CHECK (CHAR_LENGTH(BTRIM(operating_system)) BETWEEN 1 AND 160),
    CONSTRAINT chk_ticket_software_details_reproduction_steps
        CHECK (CHAR_LENGTH(BTRIM(reproduction_steps)) BETWEEN 1 AND 10000),
    CONSTRAINT chk_ticket_software_details_expected_result
        CHECK (CHAR_LENGTH(BTRIM(expected_result)) BETWEEN 1 AND 10000),
    CONSTRAINT chk_ticket_software_details_actual_result
        CHECK (CHAR_LENGTH(BTRIM(actual_result)) BETWEEN 1 AND 10000),
    CONSTRAINT chk_ticket_software_details_timestamps
        CHECK (updated_at >= created_at),
    CONSTRAINT fk_ticket_software_details_ticket
        FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE
);

CREATE TABLE ticket_software_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    level VARCHAR(20) NOT NULL
        CHECK (level IN ('DEBUG', 'INFO', 'WARN', 'ERROR')),
    source VARCHAR(120) NOT NULL,
    message TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ticket_software_logs_source
        CHECK (CHAR_LENGTH(BTRIM(source)) BETWEEN 1 AND 120),
    CONSTRAINT chk_ticket_software_logs_message
        CHECK (CHAR_LENGTH(BTRIM(message)) BETWEEN 1 AND 10000),
    CONSTRAINT fk_ticket_software_logs_ticket
        FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE
);

CREATE INDEX idx_users_organization_id
    ON users (organization_id);

CREATE INDEX idx_password_reset_tokens_user_id
    ON password_reset_tokens (user_id);

CREATE INDEX idx_assets_user_created_at
    ON assets (user_id, created_at DESC);

CREATE INDEX idx_assets_created_at
    ON assets (created_at DESC);

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

CREATE INDEX idx_hardware_maintenance_history_ticket_created_at
    ON hardware_maintenance_history (ticket_id, created_at DESC, id DESC);

CREATE INDEX idx_hardware_maintenance_history_performed_by
    ON hardware_maintenance_history (performed_by);

CREATE INDEX idx_hardware_post_repair_checklists_completed_by
    ON hardware_post_repair_checklists (completed_by)
    WHERE completed_by IS NOT NULL;

CREATE INDEX idx_ticket_software_logs_ticket_occurred_at
    ON ticket_software_logs (ticket_id, occurred_at DESC, id DESC);

ALTER TABLE organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE sla_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_sla_pauses ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE hardware_ticket_details ENABLE ROW LEVEL SECURITY;
ALTER TABLE hardware_maintenance_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE hardware_post_repair_checklists ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_software_details ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_software_logs ENABLE ROW LEVEL SECURITY;

-- O frontend acessa os dados somente pela API Spring. Os grants e a policy
-- exclusiva do role JDBC speeddesk_app ficam em supabase-access.sql para que
-- nenhuma senha faça parte deste schema. anon e authenticated permanecem sem
-- policy; hashes de recuperação, notas internas, registros operacionais de
-- SLA, manutenção e logs técnicos não são expostos pela Data API.
