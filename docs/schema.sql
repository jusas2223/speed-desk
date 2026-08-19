-- Schema PostgreSQL de referência para o estado atual do Speed Desk.

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
        CHECK (role IN ('CLIENTE', 'TECNICO', 'GERENTE')),
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
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
    cliente_id UUID NOT NULL,
    tecnico_id UUID,
    asset_id UUID,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_vencimento TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_tickets_cliente
        FOREIGN KEY (cliente_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_tecnico
        FOREIGN KEY (tecnico_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_asset
        FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE RESTRICT
);

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

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;

-- Nenhuma policy é criada neste momento. O frontend acessa os dados somente
-- pela API Spring, e o acesso pela Data API do Supabase deve permanecer
-- bloqueado. Se o acesso direto pelo frontend for aprovado futuramente, as
-- policies deverão ser definidas antes que esse acesso seja habilitado.
