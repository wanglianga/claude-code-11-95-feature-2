-- 每个测试方法前清理业务数据，保留参考数据（海区/物种/季节/渔具/港口/船只/人员主档）
TRUNCATE gate_scan_logs, departure_windows, gate_credentials, roster_acknowledgements,
         owner_decisions, crew_members, crew_roster_revisions,
         voyage_events, quota_ledger, weigh_tickets, unload_records, notifications,
         appointments, disputes, case_notes, regulatory_receipts, voyages,
         permit_items, permits, permit_application_items, permit_applications
         RESTART IDENTITY CASCADE;
UPDATE quota_accounts SET reserved_kg = 0, used_kg = 0;
-- 人员主档为参考数据，测试中请假改动态后复位
UPDATE crew_person_master SET active = TRUE;
