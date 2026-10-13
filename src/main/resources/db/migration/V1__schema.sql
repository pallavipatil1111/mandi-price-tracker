-- Mandi Price Tracker schema. Prices are whole rupees per quintal (100 kg), as Agmarknet publishes them.

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(80)  NOT NULL,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(10)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE markets (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    state    VARCHAR(60)  NOT NULL,
    district VARCHAR(80)  NOT NULL,
    name     VARCHAR(120) NOT NULL,
    CONSTRAINT uq_markets UNIQUE (state, district, name)
);

CREATE TABLE commodities (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(80) NOT NULL,
    category VARCHAR(40) NOT NULL,
    CONSTRAINT uq_commodities_name UNIQUE (name)
);

-- One row per market, commodity, variety and day. Re-importing the same day updates the row in place.
CREATE TABLE prices (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    market_id    BIGINT      NOT NULL,
    commodity_id BIGINT      NOT NULL,
    variety      VARCHAR(80) NOT NULL,
    arrival_date DATE        NOT NULL,
    min_price    INT         NOT NULL,
    max_price    INT         NOT NULL,
    modal_price  INT         NOT NULL,
    source       VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP   NOT NULL,
    updated_at   TIMESTAMP   NOT NULL,
    CONSTRAINT fk_prices_market    FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_prices_commodity FOREIGN KEY (commodity_id) REFERENCES commodities (id),
    CONSTRAINT uq_prices UNIQUE (market_id, commodity_id, variety, arrival_date),
    CONSTRAINT ck_prices_range CHECK (min_price > 0 AND min_price <= modal_price AND modal_price <= max_price)
);
-- Series lookups (one commodity in one market over time) and the market board (everything since a date).
CREATE INDEX ix_prices_series ON prices (commodity_id, market_id, arrival_date);
CREATE INDEX ix_prices_date ON prices (arrival_date);

CREATE TABLE import_runs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    source       VARCHAR(20)  NOT NULL,
    trigger_type VARCHAR(12)  NOT NULL,
    status       VARCHAR(10)  NOT NULL,
    started_at   TIMESTAMP    NOT NULL,
    finished_at  TIMESTAMP    NULL,
    fetched      INT          NOT NULL DEFAULT 0,
    inserted     INT          NOT NULL DEFAULT 0,
    updated      INT          NOT NULL DEFAULT 0,
    unchanged    INT          NOT NULL DEFAULT 0,
    rejected     INT          NOT NULL DEFAULT 0,
    alerts_fired INT          NOT NULL DEFAULT 0,
    first_date   DATE         NULL,
    last_date    DATE         NULL,
    message      VARCHAR(500) NULL
);
CREATE INDEX ix_import_runs_started ON import_runs (started_at);

CREATE TABLE watch_items (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT    NOT NULL,
    commodity_id BIGINT    NOT NULL,
    market_id    BIGINT    NOT NULL,
    created_at   TIMESTAMP NOT NULL,
    CONSTRAINT fk_watch_user      FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_watch_commodity FOREIGN KEY (commodity_id) REFERENCES commodities (id),
    CONSTRAINT fk_watch_market    FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT uq_watch UNIQUE (user_id, commodity_id, market_id)
);

-- Edge-triggered alerts: "armed" alerts fire once when the price crosses the threshold, then wait
-- until the price moves back before they can fire again.
CREATE TABLE alerts (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT     NOT NULL,
    commodity_id      BIGINT     NOT NULL,
    market_id         BIGINT     NOT NULL,
    direction         VARCHAR(5) NOT NULL,
    threshold         INT        NOT NULL,
    active            BOOLEAN    NOT NULL,
    armed             BOOLEAN    NOT NULL,
    last_triggered_at TIMESTAMP  NULL,
    created_at        TIMESTAMP  NOT NULL,
    CONSTRAINT fk_alerts_user      FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_alerts_commodity FOREIGN KEY (commodity_id) REFERENCES commodities (id),
    CONSTRAINT fk_alerts_market    FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT ck_alerts_threshold CHECK (threshold > 0)
);
CREATE INDEX ix_alerts_series ON alerts (commodity_id, market_id, active);

CREATE TABLE notifications (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    alert_id   BIGINT       NULL,
    message    VARCHAR(300) NOT NULL,
    price      INT          NOT NULL,
    price_date DATE         NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    read_at    TIMESTAMP    NULL,
    CONSTRAINT fk_notifications_user  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_alert FOREIGN KEY (alert_id) REFERENCES alerts (id) ON DELETE SET NULL
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at);
