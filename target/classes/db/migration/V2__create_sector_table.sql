-- Setor de um evento. 'available' é o campo decrementado atomicamente na reserva
-- (ver ADR-0001 sobre a estratégia de concorrência via UPDATE condicional).
CREATE TABLE sector (
    id          RAW(16)        DEFAULT SYS_GUID() NOT NULL,
    event_id    RAW(16)        NOT NULL,
    name        VARCHAR2(100)  NOT NULL,
    price_cents NUMBER(10)     NOT NULL,
    capacity    NUMBER(10)     NOT NULL,
    available   NUMBER(10)     NOT NULL,
    version     NUMBER(19)     DEFAULT 0 NOT NULL,
    created_at  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT pk_sector PRIMARY KEY (id),
    CONSTRAINT fk_sector_event FOREIGN KEY (event_id) REFERENCES event (id),
    CONSTRAINT ck_sector_capacity_positive CHECK (capacity > 0),
    CONSTRAINT ck_sector_available_range CHECK (available >= 0 AND available <= capacity)
);

CREATE INDEX ix_sector_event_id ON sector (event_id);

COMMENT ON TABLE sector IS 'Setor com estoque próprio dentro de um evento';
COMMENT ON COLUMN sector.available IS 'Unidades ainda disponíveis; decrementado via UPDATE condicional, nunca por leitura+escrita separadas';
COMMENT ON COLUMN sector.version IS 'Reservado para eventual uso com @Version (otimista); não é a estratégia principal da Fase 1, ver ADR-0001';
