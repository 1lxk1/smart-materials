-- ============================================================================
--  初始化数据（可重复执行）
--  · 用户由 Java 启动器 DataSeeder 写入（密码需要 BCrypt，不适合写死在 SQL）
--  · 本文件只写类目 / 材料 / 批次，且全部 ON CONFLICT DO NOTHING
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 类目：大类 → 子类 → 品牌 → 型号（四级）
-- ---------------------------------------------------------------------------
INSERT INTO material_category (parent_id, code, name, level, unit, safe_stock, sort_no)
SELECT NULL, 'CHEM', '化学试剂', 1, '瓶', 50, 10
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_category (parent_id, code, name, level, unit, safe_stock, sort_no)
SELECT c.id, 'CHEM_ACID', '酸碱类', 2, '瓶', 20, 11
FROM material_category c WHERE c.code = 'CHEM'
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_category (parent_id, code, name, level, unit, safe_stock, sort_no)
SELECT c.id, 'CHEM_SOLV', '有机溶剂', 2, '瓶', 15, 12
FROM material_category c WHERE c.code = 'CHEM'
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_category (parent_id, code, name, level, unit, safe_stock, sort_no)
SELECT c.id, 'CHEM_ACID_SINOPHARM', '国药集团', 3, '瓶', 10, 111
FROM material_category c WHERE c.code = 'CHEM_ACID'
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_category (parent_id, code, name, level, unit, safe_stock, sort_no)
SELECT c.id, 'CHEM_ACID_SINOPHARM_HCL', '盐酸 AR / 500mL', 4, '瓶', 8, 1111
FROM material_category c WHERE c.code = 'CHEM_ACID_SINOPHARM'
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_category (parent_id, code, name, level, unit, safe_stock, sort_no)
SELECT NULL, 'CONS', '实验耗材', 1, '盒', 100, 20
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_category (parent_id, code, name, level, unit, safe_stock, sort_no)
SELECT c.id, 'CONS_TIP', '移液枪头', 2, '盒', 40, 21
FROM material_category c WHERE c.code = 'CONS'
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_category (parent_id, code, name, level, unit, safe_stock, sort_no)
SELECT c.id, 'CONS_DISH', '培养皿', 2, '包', 30, 22
FROM material_category c WHERE c.code = 'CONS'
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_category (parent_id, code, name, level, unit, safe_stock, sort_no)
SELECT NULL, 'ELEC', '电子元器件', 1, '个', 200, 30
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_category (parent_id, code, name, level, unit, safe_stock, sort_no)
SELECT c.id, 'ELEC_SENSOR', '传感器', 2, '个', 50, 31
FROM material_category c WHERE c.code = 'ELEC'
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 材料台账
-- ---------------------------------------------------------------------------
INSERT INTO material_item (category_id, code, brand, model, spec, unit_price, currency, unit, safe_stock, stock_qty, specs)
SELECT c.id, 'HCL-AR', '国药集团', 'HCl-AR', '500mL / AR', 28.50, 'CNY', '瓶', 8, 0,
       '{"浓度": "AR", "包装": "500mL", "CAS": "7647-01-0"}'::jsonb
FROM material_category c WHERE c.code = 'CHEM_ACID_SINOPHARM_HCL'
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_item (category_id, code, brand, model, spec, unit_price, currency, unit, safe_stock, stock_qty, specs)
SELECT c.id, 'ETOH-99', '阿拉丁', 'EtOH-99.7', '500mL / 无水乙醇', 36.00, 'CNY', '瓶', 12, 0,
       '{"纯度": "99.7%", "包装": "500mL"}'::jsonb
FROM material_category c WHERE c.code = 'CHEM_SOLV'
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_item (category_id, code, brand, model, spec, unit_price, currency, unit, safe_stock, stock_qty, specs)
SELECT c.id, 'TIP-200', 'Kirgen', 'KG-200', '200μL / 96支', 45.00, 'CNY', '盒', 40, 0,
       '{"量程": "200μL", "规格": "96支/盒"}'::jsonb
FROM material_category c WHERE c.code = 'CONS_TIP'
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_item (category_id, code, brand, model, spec, unit_price, currency, unit, safe_stock, stock_qty, specs)
SELECT c.id, 'DISH-90', 'Nest', 'PD-90', '90mm / 10个', 18.00, 'CNY', '包', 30, 0,
       '{"直径": "90mm", "包装": "10个/包"}'::jsonb
FROM material_category c WHERE c.code = 'CONS_DISH'
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_item (category_id, code, brand, model, spec, unit_price, currency, unit, safe_stock, stock_qty, specs)
SELECT c.id, 'SENSOR-SHT40', 'Sensirion', 'SHT40', '温湿度 / I2C', 62.00, 'CNY', '个', 50, 0,
       '{"接口": "I2C", "精度": "±1.8%RH"}'::jsonb
FROM material_category c WHERE c.code = 'ELEC_SENSOR'
ON CONFLICT (code) DO NOTHING;

INSERT INTO material_item (category_id, code, brand, model, spec, unit_price, currency, unit, safe_stock, stock_qty, specs)
SELECT c.id, 'GLV-M', '国产', 'GLV-M', '丁腈手套 M', 32.00, 'CNY', '盒', 25, 0,
       '{"尺码": "M", "材质": "丁腈"}'::jsonb
FROM material_category c WHERE c.code = 'CONS'
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 库存批次（条码 = 二维码标签的备用编码）
-- ---------------------------------------------------------------------------
INSERT INTO stock_batch (item_id, batch_code, barcode, quantity, initial_qty, location, expire_date, supplier, status)
SELECT i.id, 'IN20250301001', 'MAT-HCL-001', 5, 5, 'A-01-03', DATE '2027-02-01', '国药集团', 'IN_STOCK'
FROM material_item i WHERE i.code = 'HCL-AR'
ON CONFLICT (batch_code) DO NOTHING;

INSERT INTO stock_batch (item_id, batch_code, barcode, quantity, initial_qty, location, expire_date, supplier, status)
SELECT i.id, 'IN20250305002', 'MAT-ETOH-002', 18, 18, 'A-02-01', DATE '2026-12-31', '阿拉丁', 'IN_STOCK'
FROM material_item i WHERE i.code = 'ETOH-99'
ON CONFLICT (batch_code) DO NOTHING;

INSERT INTO stock_batch (item_id, batch_code, barcode, quantity, initial_qty, location, expire_date, supplier, status)
SELECT i.id, 'IN20250310003', 'MAT-TIP-003', 22, 22, 'B-01-05', DATE '2028-01-01', 'Kirgen', 'IN_STOCK'
FROM material_item i WHERE i.code = 'TIP-200'
ON CONFLICT (batch_code) DO NOTHING;

INSERT INTO stock_batch (item_id, batch_code, barcode, quantity, initial_qty, location, expire_date, supplier, status)
SELECT i.id, 'IN20250220004', 'MAT-GLV-004', 9, 9, 'B-03-02', DATE '2027-06-01', '国产', 'IN_STOCK'
FROM material_item i WHERE i.code = 'GLV-M'
ON CONFLICT (batch_code) DO NOTHING;

INSERT INTO stock_batch (item_id, batch_code, barcode, quantity, initial_qty, location, expire_date, supplier, status)
SELECT i.id, 'IN20250315005', 'MAT-DISH-005', 55, 55, 'C-01-01', DATE '2027-12-31', 'Nest', 'IN_STOCK'
FROM material_item i WHERE i.code = 'DISH-90'
ON CONFLICT (batch_code) DO NOTHING;

INSERT INTO stock_batch (item_id, batch_code, barcode, quantity, initial_qty, location, expire_date, supplier, status)
SELECT i.id, 'IN20250318006', 'MAT-SENSOR-006', 48, 48, 'C-02-02', DATE '2030-01-01', 'Sensirion', 'IN_STOCK'
FROM material_item i WHERE i.code = 'SENSOR-SHT40'
ON CONFLICT (batch_code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 台账库存与批次汇总对齐（幂等，可自愈）
-- ---------------------------------------------------------------------------
UPDATE material_item mi
SET stock_qty = COALESCE((SELECT SUM(b.quantity) FROM stock_batch b WHERE b.item_id = mi.id AND b.deleted = 0), 0),
    update_time = now()
WHERE mi.deleted = 0;
