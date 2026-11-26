-- 渔业许可与卸港预约服务 初始 schema
CREATE TABLE vessels (
    id          BIGSERIAL PRIMARY KEY,
    vessel_no   VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    owner_name  VARCHAR(64)  NOT NULL,
    owner_phone VARCHAR(32),
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE fishing_areas (
    code   VARCHAR(16) PRIMARY KEY,
    name   VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE species (
    code   VARCHAR(16) PRIMARY KEY,
    name   VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- 禁渔期：按 月-日 区间（支持跨年区间，如 12-01 ~ 02-28）
CREATE TABLE season_rules (
    id          BIGSERIAL PRIMARY KEY,
    area_code   VARCHAR(16) NOT NULL REFERENCES fishing_areas(code),
    species_code VARCHAR(16) NOT NULL REFERENCES species(code),
    closed_from VARCHAR(5)  NOT NULL, -- MM-dd
    closed_to   VARCHAR(5)  NOT NULL, -- MM-dd
    note        VARCHAR(255)
);

-- 渔具限制：存在记录且 allowed=false 表示该海区禁用该渔具
CREATE TABLE gear_restrictions (
    id         BIGSERIAL PRIMARY KEY,
    area_code  VARCHAR(16) NOT NULL REFERENCES fishing_areas(code),
    gear_type  VARCHAR(32) NOT NULL,
    allowed    BOOLEAN NOT NULL,
    note       VARCHAR(255),
    UNIQUE (area_code, gear_type)
);

-- 物种额度账：严格按 海区+物种+年度 建账，不得跨账合并
CREATE TABLE quota_accounts (
    id          BIGSERIAL PRIMARY KEY,
    area_code   VARCHAR(16) NOT NULL REFERENCES fishing_areas(code),
    species_code VARCHAR(16) NOT NULL REFERENCES species(code),
    season_year INT          NOT NULL,
    total_kg    NUMERIC(14,2) NOT NULL,
    reserved_kg NUMERIC(14,2) NOT NULL DEFAULT 0,
    used_kg     NUMERIC(14,2) NOT NULL DEFAULT 0,
    version     BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (area_code, species_code, season_year)
);

CREATE TABLE permit_applications (
    id             BIGSERIAL PRIMARY KEY,
    application_no VARCHAR(32) NOT NULL UNIQUE,
    vessel_id      BIGINT NOT NULL REFERENCES vessels(id),
    captain_name   VARCHAR(64) NOT NULL,
    crew_count     INT NOT NULL,
    crew_list      TEXT,
    area_code      VARCHAR(16) NOT NULL,
    gear_type      VARCHAR(32) NOT NULL,
    planned_start  TIMESTAMP NOT NULL,
    planned_end    TIMESTAMP NOT NULL,
    status         VARCHAR(16) NOT NULL, -- PENDING / APPROVED / REJECTED
    reject_reasons TEXT,
    created_by     VARCHAR(64),
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    reviewed_at    TIMESTAMP
);

CREATE TABLE permit_application_items (
    id                BIGSERIAL PRIMARY KEY,
    application_id    BIGINT NOT NULL REFERENCES permit_applications(id),
    species_code      VARCHAR(16) NOT NULL,
    declared_limit_kg NUMERIC(14,2) NOT NULL
);

CREATE TABLE permits (
    id             BIGSERIAL PRIMARY KEY,
    permit_no      VARCHAR(32) NOT NULL UNIQUE,
    application_id BIGINT NOT NULL REFERENCES permit_applications(id),
    vessel_id      BIGINT NOT NULL REFERENCES vessels(id),
    area_code      VARCHAR(16) NOT NULL,
    gear_type      VARCHAR(32) NOT NULL,
    valid_from     TIMESTAMP NOT NULL,
    valid_to       TIMESTAMP NOT NULL,
    status         VARCHAR(16) NOT NULL, -- ACTIVE / CLOSED
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE permit_items (
    id         BIGSERIAL PRIMARY KEY,
    permit_id  BIGINT NOT NULL REFERENCES permits(id),
    species_code VARCHAR(16) NOT NULL,
    limit_kg   NUMERIC(14,2) NOT NULL
);

-- 航次状态机：APPROVED -> DEPARTED -> RETURNED -> UNLOADING -> (REWEIGHING) -> CLOSED
CREATE TABLE voyages (
    id          BIGSERIAL PRIMARY KEY,
    voyage_no   VARCHAR(32) NOT NULL UNIQUE,
    permit_id   BIGINT NOT NULL UNIQUE REFERENCES permits(id),
    vessel_id   BIGINT NOT NULL REFERENCES vessels(id),
    state       VARCHAR(16) NOT NULL,
    departed_at TIMESTAMP,
    returned_at TIMESTAMP,
    closed_at   TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 0
);
-- 同一船同时只允许一个未结航次
CREATE UNIQUE INDEX uq_voyage_open_per_vessel ON voyages (vessel_id) WHERE state <> 'CLOSED';

-- 航次事件（回放时间线 + 幂等键）
CREATE TABLE voyage_events (
    id         BIGSERIAL PRIMARY KEY,
    voyage_id  BIGINT REFERENCES voyages(id),
    permit_id  BIGINT REFERENCES permits(id),
    event_type VARCHAR(32) NOT NULL,
    detail     TEXT,
    actor      VARCHAR(64),
    request_key VARCHAR(128) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE unload_records (
    id              BIGSERIAL PRIMARY KEY,
    voyage_id       BIGINT NOT NULL REFERENCES voyages(id),
    species_code    VARCHAR(16) NOT NULL,
    declared_kg     NUMERIC(14,2) NOT NULL,
    weighed_kg      NUMERIC(14,2),
    weigh_ticket_id BIGINT,
    status          VARCHAR(16) NOT NULL, -- DECLARED / WEIGHED / DISPUTED / REWEIGHED / SIGNED
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    signed_at       TIMESTAMP,
    UNIQUE (voyage_id, species_code)
);

-- 地磅票据：ticket_no 唯一，重传返回原处理结果
CREATE TABLE weigh_tickets (
    id               BIGSERIAL PRIMARY KEY,
    ticket_no        VARCHAR(64) NOT NULL UNIQUE,
    voyage_id        BIGINT NOT NULL REFERENCES voyages(id),
    unload_record_id BIGINT NOT NULL REFERENCES unload_records(id),
    species_code     VARCHAR(16) NOT NULL,
    gross_kg         NUMERIC(14,2) NOT NULL,
    tare_kg          NUMERIC(14,2) NOT NULL,
    net_kg           NUMERIC(14,2) NOT NULL,
    source           VARCHAR(32) NOT NULL,
    weighed_at       TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

-- 额度账流水：只追加，结余可解释、可回放
CREATE TABLE quota_ledger (
    id               BIGSERIAL PRIMARY KEY,
    account_id       BIGINT NOT NULL REFERENCES quota_accounts(id),
    voyage_id        BIGINT,
    permit_id        BIGINT,
    unload_record_id BIGINT,
    species_code     VARCHAR(16) NOT NULL,
    entry_type       VARCHAR(16) NOT NULL, -- RESERVE / DEDUCT / RELEASE / ADJUST
    amount_kg        NUMERIC(14,2) NOT NULL,
    reserved_after   NUMERIC(14,2) NOT NULL,
    used_after       NUMERIC(14,2) NOT NULL,
    available_after  NUMERIC(14,2) NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL UNIQUE,
    remark           VARCHAR(255),
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE port_resources (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(16) NOT NULL UNIQUE,
    name             VARCHAR(128) NOT NULL,
    open_time        VARCHAR(5) NOT NULL,  -- HH:mm
    close_time       VARCHAR(5) NOT NULL,  -- HH:mm
    cleanup_min      INT NOT NULL DEFAULT 5,
    cold_capacity_kg NUMERIC(14,2) NOT NULL
);

-- 卸港预约：mode FIXED（固定时段）/ ROLLING（滚动叫号）
CREATE TABLE appointments (
    id                 BIGSERIAL PRIMARY KEY,
    appointment_no     VARCHAR(32) NOT NULL UNIQUE,
    voyage_id          BIGINT NOT NULL REFERENCES voyages(id),
    port_id            BIGINT NOT NULL REFERENCES port_resources(id),
    service_date       DATE NOT NULL,
    mode               VARCHAR(16) NOT NULL,
    status             VARCHAR(16) NOT NULL, -- BOOKED / SCHEDULED / CHECKED_IN / RESCHEDULED / DONE / CANCELLED
    requested_start    TIMESTAMP,
    planned_arrival    TIMESTAMP,
    actual_arrival     TIMESTAMP,
    slot_start         TIMESTAMP,
    slot_end           TIMESTAMP,
    duration_min       INT NOT NULL,
    cleanup_min        INT NOT NULL DEFAULT 5,
    latest_finish      TIMESTAMP NOT NULL,
    expected_unload_kg NUMERIC(14,2) NOT NULL DEFAULT 0,
    seq                INT,
    deadline_breach    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP NOT NULL DEFAULT now(),
    version            BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_appt_port_date ON appointments (port_id, service_date);

CREATE TABLE notifications (
    id             BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES appointments(id),
    voyage_id      BIGINT NOT NULL REFERENCES voyages(id),
    type           VARCHAR(24) NOT NULL, -- SCHEDULED / RESCHEDULED / QUEUE_CHANGED / CALLED
    message        VARCHAR(512) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);

-- 争议：WEIGHT_DISPUTE / MIXED_LOAD / PERMIT_EXPIRED；OPEN 期间阻断结案
CREATE TABLE disputes (
    id               BIGSERIAL PRIMARY KEY,
    voyage_id        BIGINT NOT NULL REFERENCES voyages(id),
    unload_record_id BIGINT REFERENCES unload_records(id),
    type             VARCHAR(24) NOT NULL,
    status           VARCHAR(16) NOT NULL, -- OPEN / RESOLVED
    description      VARCHAR(512),
    raised_by        VARCHAR(64),
    resolution       VARCHAR(512),
    resolved_by      VARCHAR(64),
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    resolved_at      TIMESTAMP
);

-- 备注：仅执法人员(ENFORCEMENT)的备注才是执法事实；客服备注不算
CREATE TABLE case_notes (
    id                BIGSERIAL PRIMARY KEY,
    voyage_id         BIGINT NOT NULL REFERENCES voyages(id),
    author            VARCHAR(64) NOT NULL,
    author_role       VARCHAR(24) NOT NULL,
    content           VARCHAR(512) NOT NULL,
    enforcement_fact  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);

-- 监管回执（模拟监管部门接收并回执）
CREATE TABLE regulatory_receipts (
    id         BIGSERIAL PRIMARY KEY,
    receipt_no VARCHAR(40) NOT NULL UNIQUE,
    voyage_id  BIGINT,
    permit_id  BIGINT,
    event_type VARCHAR(32) NOT NULL,
    payload    TEXT,
    status     VARCHAR(16) NOT NULL, -- REPORTED / ACKED
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    acked_at   TIMESTAMP
);
