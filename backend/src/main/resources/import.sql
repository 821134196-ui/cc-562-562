-- 菜单
INSERT INTO drink (id, name, description) VALUES
  (1, '美式咖啡', '经典黑咖啡，冷热可选'),
  (2, '拿铁', '浓缩咖啡加蒸煮牛奶'),
  (3, '抹茶拿铁', '宇治抹茶与鲜奶'),
  (4, '柠檬气泡水', '清爽柠檬气泡饮');

INSERT INTO drink_size (id, drink_id, size_name, volume_ml, price_cents) VALUES
  (1, 1, '中杯', 470, 1800),
  (2, 1, '大杯', 650, 2200),
  (3, 2, '中杯', 470, 2400),
  (4, 2, '大杯', 650, 2800),
  (5, 3, '中杯', 470, 2600),
  (6, 3, '大杯', 650, 3000),
  (7, 4, '中杯', 470, 1600),
  (8, 4, '大杯', 650, 2000);

-- 演示优惠券：stackable_with_cup_discount 控制能否与自带杯折扣叠加
INSERT INTO coupon (id, code, type, value, min_spend_cents, stackable_with_cup_discount, status, used_by_order_id) VALUES
  (1, 'SAVE5', 'FIXED_AMOUNT', 500, 2000, 1, 'ACTIVE', NULL),
  (2, 'VIP10', 'PERCENT', 10, 0, 0, 'ACTIVE', NULL),
  (3, 'FLAT8', 'FIXED_AMOUNT', 800, 3000, 0, 'ACTIVE', NULL),
  (4, 'STACK3', 'FIXED_AMOUNT', 300, 0, 1, 'ACTIVE', NULL),
  (5, 'SAVE2', 'FIXED_AMOUNT', 200, 0, 1, 'ACTIVE', NULL);
