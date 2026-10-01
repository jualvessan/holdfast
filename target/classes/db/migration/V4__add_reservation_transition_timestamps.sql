-- Timestamps que registram quando cada transição de estado ocorreu, além de
-- 'updated_at' (genérico). Isso já viabiliza responder "quando foi confirmado?"
-- sem precisar de uma tabela de pagamento dedicada nesta fase (ver nota abaixo).
ALTER TABLE reservation ADD (
    confirmed_at TIMESTAMP NULL,
    cancelled_at TIMESTAMP NULL
);

COMMENT ON COLUMN reservation.confirmed_at IS 'Preenchido quando o pagamento simulado é aprovado (status vira CONFIRMED)';
COMMENT ON COLUMN reservation.cancelled_at IS 'Preenchido quando a reserva é cancelada ou expira';

-- Nota de design: não criamos uma tabela 'payment' nesta fase de propósito.
-- Na Fase 1, pagamento é simulado como uma transição de estado da própria reserva.
-- Um serviço de Pagamentos com schema próprio só se justifica na Fase 4, quando
-- comunicação entre serviços e consistência eventual entram no escopo (ver roadmap).
-- Criar essa tabela agora seria antecipar uma decisão de particionamento de serviço
-- sem o problema real (comunicação distribuída) ainda existir.
