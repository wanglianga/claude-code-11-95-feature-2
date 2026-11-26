-- N-5 船员名册替换与投影确认：人员主档 + 名册修订版本链 + 投影回执 + 闸口凭证 + 离港窗 + 扫码留痕

-- 船只增加船型，用于船员船型权限核验
ALTER TABLE vessels ADD COLUMN vessel_type VARCHAR(32) NOT NULL DEFAULT 'TRAWLER';

-- 人员主档：证书有效期、船型权限、专项资质（如 REEFER 冷藏设备操作）、休息达标时刻
CREATE TABLE crew_person_master (
    id                  BIGSERIAL PRIMARY KEY,
    person_no           VARCHAR(32)  NOT NULL UNIQUE,
    name                VARCHAR(64)  NOT NULL,
    position_code       VARCHAR(32)  NOT NULL,
    certificate_no      VARCHAR(64)  NOT NULL,
    cert_valid_to       TIMESTAMP    NOT NULL,
    vessel_type_grants  VARCHAR(255) NOT NULL,
    qualification_codes VARCHAR(255),
    rest_ready_at       TIMESTAMP    NOT NULL,
    emergency_phone     VARCHAR(32),
    active              BOOLEAN      NOT NULL DEFAULT TRUE
);

-- 名册修订（只追加版本链）
CREATE TABLE crew_roster_revisions (
    id               BIGSERIAL PRIMARY KEY,
    revision_no      VARCHAR(32) NOT NULL UNIQUE,
    voyage_id        BIGINT      NOT NULL REFERENCES voyages(id),
    revision_seq     INT         NOT NULL,
    supersedes_id    BIGINT REFERENCES crew_roster_revisions(id),
    change_kind      VARCHAR(16) NOT NULL, -- INITIAL / REST / SUBSTITUTE / CORRECTION
    status           VARCHAR(24) NOT NULL, -- DRAFT / PENDING_RELEASE / RELEASED / SUPERSEDED
    change_reason    VARCHAR(512),
    owner_decision_id BIGINT,
    required_qualifications VARCHAR(255),
    signed_at        TIMESTAMP,
    released_at      TIMESTAMP,
    created_at       TIMESTAMP   NOT NULL DEFAULT now(),
    version          BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (voyage_id, revision_seq)
);

-- 名册行：逐人逐版记录岗位覆盖与任职资格
CREATE TABLE crew_members (
    id                  BIGSERIAL PRIMARY KEY,
    revision_id         BIGINT      NOT NULL REFERENCES crew_roster_revisions(id),
    person_no           VARCHAR(32) NOT NULL,
    name                VARCHAR(64) NOT NULL,
    position_code       VARCHAR(32) NOT NULL,
    voyage_duty         VARCHAR(64) NOT NULL,
    certificate_no      VARCHAR(64) NOT NULL,
    cert_valid_to       TIMESTAMP   NOT NULL,
    vessel_type_grants  VARCHAR(255) NOT NULL,
    qualification_codes VARCHAR(255),
    rest_ready_at       TIMESTAMP   NOT NULL,
    emergency_phone     VARCHAR(32),
    created_at          TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_crew_members_revision ON crew_members (revision_id);

-- 船东决定（两种选择与更正均保留决定 + 通知回执）
CREATE TABLE owner_decisions (
    id                BIGSERIAL PRIMARY KEY,
    decision_no       VARCHAR(32) NOT NULL UNIQUE,
    voyage_id         BIGINT      NOT NULL REFERENCES voyages(id),
    decision_type     VARCHAR(24) NOT NULL, -- KEEP_CAPTAIN_REST / USE_SUBSTITUTE / CORRECT_ROSTER
    decided_by        VARCHAR(64) NOT NULL,
    decided_at        TIMESTAMP   NOT NULL DEFAULT now(),
    reason            VARCHAR(512),
    revision_id       BIGINT,
    notify_receipt_no VARCHAR(64),
    notified_at       TIMESTAMP
);

-- 接收方字段投影确认（投影模型：投影内容+协议版本逐项一致才可沿用旧确认）
CREATE TABLE roster_acknowledgements (
    id                 BIGSERIAL PRIMARY KEY,
    ack_no             VARCHAR(32) NOT NULL UNIQUE,
    revision_id        BIGINT      NOT NULL REFERENCES crew_roster_revisions(id),
    voyage_id          BIGINT      NOT NULL REFERENCES voyages(id),
    party              VARCHAR(16) NOT NULL, -- PORT / INSURER / EMERGENCY
    projection_version VARCHAR(16) NOT NULL,
    projection_hash    VARCHAR(128) NOT NULL,
    projection_fields  VARCHAR(512) NOT NULL,
    status             VARCHAR(16) NOT NULL, -- PENDING / CONFIRMED / INVALIDATED
    carried_from_ack_id BIGINT,
    confirmed_by       VARCHAR(64),
    confirmed_at       TIMESTAMP,
    invalidated_reason VARCHAR(512),
    created_at         TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_roster_ack_revision ON roster_acknowledgements (revision_id, party);

-- 闸口二维码凭证：重签放行时旧凭证同时撤销
CREATE TABLE gate_credentials (
    id            BIGSERIAL PRIMARY KEY,
    qr_code       VARCHAR(128) NOT NULL UNIQUE,
    voyage_id     BIGINT       NOT NULL REFERENCES voyages(id),
    revision_id   BIGINT       NOT NULL REFERENCES crew_roster_revisions(id),
    status        VARCHAR(16)  NOT NULL, -- ACTIVE / REVOKED
    issued_at     TIMESTAMP    NOT NULL DEFAULT now(),
    revoked_at    TIMESTAMP,
    revoke_reason VARCHAR(512)
);

-- 离港许可窗：窗口关闭后不得再显示为可用
CREATE TABLE departure_windows (
    id          BIGSERIAL PRIMARY KEY,
    window_no   VARCHAR(32) NOT NULL UNIQUE,
    voyage_id   BIGINT      NOT NULL REFERENCES voyages(id),
    port_code   VARCHAR(16) NOT NULL,
    window_kind VARCHAR(16) NOT NULL, -- CURRENT / ALTERNATIVE
    window_from TIMESTAMP   NOT NULL,
    window_to   TIMESTAMP   NOT NULL,
    cancelled   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP   NOT NULL DEFAULT now()
);

-- 闸口扫码留痕：离港后迟到签到仅留作核查，不回滚新名册
CREATE TABLE gate_scan_logs (
    id             BIGSERIAL PRIMARY KEY,
    scan_no        VARCHAR(32) NOT NULL UNIQUE,
    voyage_id      BIGINT,
    qr_code        VARCHAR(128) NOT NULL,
    verdict        VARCHAR(16)  NOT NULL, -- PASS / REJECT
    reason_code    VARCHAR(64),
    detail         VARCHAR(512),
    after_departure BOOLEAN     NOT NULL DEFAULT FALSE,
    audit_status   VARCHAR(32), -- RECORDED_FOR_AUDIT
    scanned_at     TIMESTAMP    NOT NULL DEFAULT now()
);

-- 人员主档种子由 CrewPersonSeeder（JPA）写入：
-- 全局 hibernate.jdbc.time_zone=Asia/Shanghai 会平移时间，SQL 字面量读回会偏移，
-- 经 JPA 写入可保证 LocalDateTime 往返一致（见 service/CrewPersonSeeder.java）。

