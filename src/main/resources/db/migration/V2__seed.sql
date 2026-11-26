-- 参考数据与样例数据
INSERT INTO vessels (vessel_no, name, owner_name, owner_phone) VALUES
 ('V001', '远洋一号', '张三', '13800000001'),
 ('V002', '远洋二号', '李四', '13800000002'),
 ('V003', '远洋三号', '王五', '13800000003');

INSERT INTO fishing_areas (code, name, active) VALUES
 ('A1', '北海一区', TRUE),
 ('A2', '南海二区', TRUE);

INSERT INTO species (code, name, active) VALUES
 ('COD', '鳕鱼', TRUE),
 ('FLOUNDER', '比目鱼', TRUE),
 ('HAIRTAIL', '带鱼', TRUE);

-- 伏季休渔：A1 的鳕鱼/比目鱼 05-01 ~ 08-31 禁捕；A2 带鱼 06-01 ~ 09-15 禁捕
INSERT INTO season_rules (area_code, species_code, closed_from, closed_to, note) VALUES
 ('A1', 'COD', '05-01', '08-31', '伏季休渔'),
 ('A1', 'FLOUNDER', '05-01', '08-31', '伏季休渔'),
 ('A2', 'HAIRTAIL', '06-01', '09-15', '伏季休渔');

-- A1 海区禁用拖网
INSERT INTO gear_restrictions (area_code, gear_type, allowed, note) VALUES
 ('A1', 'TRAWL', FALSE, 'A1 海区禁用拖网'),
 ('A1', 'GILLNET', TRUE, '允许刺网'),
 ('A1', 'LONGLINE', TRUE, '允许延绳钓'),
 ('A2', 'TRAWL', TRUE, '允许拖网'),
 ('A2', 'GILLNET', TRUE, '允许刺网'),
 ('A2', 'LONGLINE', TRUE, '允许延绳钓');

-- 2026 年度物种额度账（kg），严格按海区+物种分立
INSERT INTO quota_accounts (area_code, species_code, season_year, total_kg) VALUES
 ('A1', 'COD', 2026, 10000),
 ('A1', 'FLOUNDER', 2026, 8000),
 ('A1', 'HAIRTAIL', 2026, 6000),
 ('A2', 'COD', 2026, 5000),
 ('A2', 'HAIRTAIL', 2026, 12000);

-- 样例港口：一条卸货线 + 一台地磅 + 有限冷库，作业间清场 5 分钟
INSERT INTO port_resources (code, name, open_time, close_time, cleanup_min, cold_capacity_kg) VALUES
 ('PORT1', '样例港口', '06:00', '20:00', 5, 5000);
