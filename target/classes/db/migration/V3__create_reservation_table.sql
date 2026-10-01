-- Reserva de um único setor. status modela a máquina de estados descrita no domínio
-- (PENDING -> CONFIRMED | EXPIRED | CANCELLED). idempotency_key + request_hash garantem
-- que retentativas do cliente com a mesma chave nunca criem duas reservas.
CREATE TABLE reservation (
    id               RAW(16)        DEFAULT SYS_GUID() NOT NULL,
    sector_id        RAW(16)        NOT NULL,
    quantity         NUMBER(10)     NOT NULL,
    unit_price_cents NUMBER(10)     NOT NULL,
    status           VARCHAR2(20)   DEFAULT 'PENDING' NOT NULL,
    idempotency_key  VARCHAR2(100)  NOT NULL,
    request_hash     VARCHAR2(64)   NOT NULL,
    expires_at       TIMESTAMP      NOT NULL,
    created_at       TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at       TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT pk_reservation PRIMARY KEY (id),
    CONSTRAINT fk_reservation_sector FOREIGN KEY (sector_id) REFERENCES sector (id),
    CONSTRAINT ck_reservation_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_reservation_status CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT uq_reservation_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX ix_reservation_sector_id ON reservation (sector_id);
CREATE INDEX ix_reservation_status_expires_at ON reservation (status, expires_at);

COMMENT ON TABLE reservation IS 'Reserva temporária (hold) de unidades de um setor';
COMMENT ON COLUMN reservation.idempotency_key IS 'Header Idempotency-Key enviado pelo cliente; único por reserva';
COMMENT ON COLUMN reservation.request_hash IS 'Hash do corpo da requisição; mesma chave com corpo diferente é um conflito (422)';
COMMENT ON COLUMN reservation.expires_at IS 'Momento em que o hold expira se não houver confirmação de pagamento';

-- ix_reservation_status_expires_at suporta o job de expiração de holds (Fase 3):
-- busca por status = 'PENDING' e expires_at vencido, com FOR UPDATE SKIP LOCKED.
