-- Evento (ex: show, peça de teatro) que possui um ou mais setores com estoque próprio.
CREATE TABLE event (
    id          RAW(16)        DEFAULT SYS_GUID() NOT NULL,
    name        VARCHAR2(150)  NOT NULL,
    venue       VARCHAR2(150)  NOT NULL,
    starts_at   TIMESTAMP      NOT NULL,
    created_at  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT pk_event PRIMARY KEY (id)
);

COMMENT ON TABLE event IS 'Evento com um ou mais setores de estoque limitado';
