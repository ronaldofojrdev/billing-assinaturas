CREATE TABLE plans (
    id               UUID PRIMARY KEY,
    name             VARCHAR(100)   NOT NULL UNIQUE,
    price            NUMERIC(19, 2) NOT NULL CHECK (price > 0),
    billing_interval VARCHAR(10)    NOT NULL,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ    NOT NULL
);

CREATE TABLE customers (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    email       VARCHAR(160) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE subscriptions (
    id                 UUID PRIMARY KEY,
    customer_id        UUID         NOT NULL REFERENCES customers (id),
    plan_id            UUID         NOT NULL REFERENCES plans (id),
    status             VARCHAR(20)  NOT NULL,
    payment_token      VARCHAR(100) NOT NULL,
    started_at         DATE         NOT NULL,
    next_billing_date  DATE         NOT NULL,
    canceled_at        TIMESTAMPTZ,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_subscriptions_due ON subscriptions (status, next_billing_date);

CREATE TABLE invoices (
    id               UUID PRIMARY KEY,
    subscription_id  UUID           NOT NULL REFERENCES subscriptions (id),
    period_start     DATE           NOT NULL,
    period_end       DATE           NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    attempts         INT            NOT NULL DEFAULT 0,
    next_retry_at    TIMESTAMPTZ,
    failure_reason   VARCHAR(255),
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL,
    paid_at          TIMESTAMPTZ,
    -- rede de seguranca: duas execucoes do job nunca geram a fatura do mesmo periodo duas vezes
    CONSTRAINT uk_invoice_subscription_period UNIQUE (subscription_id, period_start)
);

CREATE INDEX idx_invoices_retry ON invoices (status, next_retry_at);

CREATE TABLE payment_attempts (
    id                 UUID PRIMARY KEY,
    invoice_id         UUID         NOT NULL REFERENCES invoices (id),
    attempt_number     INT          NOT NULL,
    gateway_charge_id  VARCHAR(100) UNIQUE,
    status             VARCHAR(15)  NOT NULL,
    failure_reason     VARCHAR(255),
    created_at         TIMESTAMPTZ  NOT NULL,
    settled_at         TIMESTAMPTZ,
    CONSTRAINT uk_attempt_invoice_number UNIQUE (invoice_id, attempt_number)
);

CREATE INDEX idx_attempts_pending ON payment_attempts (status, created_at);

-- eventos de webhook ja recebidos (o gateway reenvia o mesmo evento se nao receber 2xx)
CREATE TABLE webhook_events (
    event_id     VARCHAR(100) PRIMARY KEY,
    type         VARCHAR(50)  NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL
);
