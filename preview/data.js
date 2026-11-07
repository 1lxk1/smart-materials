window.CZ = window.CZ || {};

CZ.ROLE_LABEL = {
  guest: '游客',
  student: '学生',
  lab: '实验员',
  teacher: '教师/PI',
  buyer: '采购员',
  admin: '系统管理员',
};

CZ.users = [
  { id: 'u1', ldapUid: 'admin', name: '系统管理员', role: 'admin', email: 'admin@univ.edu.cn', phone: '13800000001', dept: '实验中心' },
  { id: 'u2', ldapUid: 'lab01', name: '李实验', role: 'lab', email: 'lab@univ.edu.cn', phone: '13800000002', dept: '材料实验室' },
  { id: 'u3', ldapUid: 'stu2024001', name: '王同学', role: 'student', email: 'wang@stu.edu.cn', phone: '13800000003', dept: '材料学院', monthlyLimit: 20 },
  { id: 'u4', ldapUid: 'teacher01', name: '张老师', role: 'teacher', email: 'zhang@univ.edu.cn', phone: '13800000004', dept: '材料学院' },
  { id: 'u5', ldapUid: 'buyer01', name: '赵采购', role: 'buyer', email: 'zhao@univ.edu.cn', phone: '13800000005', dept: '采购办' },
];

CZ.categories = [
  { id: 'c1', name: '化学试剂', unit: '瓶', safeStock: 50, children: [
    { id: 'c11', name: '酸碱类', unit: '瓶', safeStock: 20, children: [
      { id: 'c111', name: '国药集团', unit: '瓶', safeStock: 10, children: [
        { id: 'c1111', name: '盐酸 AR / 500mL', unit: '瓶', safeStock: 8 },
      ]},
    ]},
    { id: 'c12', name: '有机溶剂', unit: '瓶', safeStock: 15 },
  ]},
  { id: 'c2', name: '实验耗材', unit: '盒', safeStock: 100, children: [
    { id: 'c21', name: '移液枪头', unit: '盒', safeStock: 40 },
    { id: 'c22', name: '培养皿', unit: '包', safeStock: 30 },
  ]},
  { id: 'c3', name: '电子元器件', unit: '个', safeStock: 200, children: [
    { id: 'c31', name: '传感器', unit: '个', safeStock: 50 },
  ]},
];

CZ.materials = [
  { id: 'm1', path: '化学试剂 / 酸碱类 / 国药集团', brand: '国药集团', model: 'HCl-AR', spec: '500mL / AR', price: 28.5, unit: '瓶', safe: 8, qty: 5 },
  { id: 'm2', path: '化学试剂 / 有机溶剂', brand: '阿拉丁', model: 'EtOH-99.7', spec: '500mL / 无水乙醇', price: 36, unit: '瓶', safe: 12, qty: 18 },
  { id: 'm3', path: '实验耗材 / 移液枪头', brand: 'Kirgen', model: 'KG-200', spec: '200μL / 96支', price: 45, unit: '盒', safe: 40, qty: 22 },
  { id: 'm4', path: '实验耗材 / 培养皿', brand: 'Nest', model: 'PD-90', spec: '90mm / 10个', price: 18, unit: '包', safe: 30, qty: 55 },
  { id: 'm5', path: '电子元器件 / 传感器', brand: 'Sensirion', model: 'SHT40', spec: '温湿度 / I2C', price: 62, unit: '个', safe: 50, qty: 48 },
  { id: 'm6', path: '实验耗材', brand: '国产', model: 'GLV-M', spec: '丁腈手套 M', price: 32, unit: '盒', safe: 25, qty: 9 },
];

CZ.batches = [
  { id: 'b1', itemId: 'm1', name: '盐酸 AR 500mL', batchCode: 'IN20250301001', qty: 5, expire: '2027-02-01', barcode: 'MAT-HCL-001', loc: 'A-01-03' },
  { id: 'b2', itemId: 'm2', name: '无水乙醇 500mL', batchCode: 'IN20250305002', qty: 18, expire: '2026-12-31', barcode: 'MAT-ETOH-002', loc: 'A-02-01' },
  { id: 'b3', itemId: 'm3', name: '移液枪头 200μL', batchCode: 'IN20250310003', qty: 22, expire: '2028-01-01', barcode: 'MAT-TIP-003', loc: 'B-01-05' },
  { id: 'b4', itemId: 'm6', name: '丁腈手套 M', batchCode: 'IN20250220004', qty: 9, expire: '2027-06-01', barcode: 'MAT-GLV-004', loc: 'B-03-02' },
];

CZ.txns = [
  { id: 't1', name: '盐酸 AR 500mL', type: 'IN', qty: 10, user: '李实验', project: '-', usage: '学期初补货', time: '2025-03-01 09:20:00' },
  { id: 't2', name: '盐酸 AR 500mL', type: 'OUT', qty: 2, user: '王同学', project: 'PRJ-2025-012', usage: '分析化学实验', time: '2025-03-12 14:05:00' },
  { id: 't3', name: '移液枪头 200μL', type: 'OUT', qty: 4, user: '王同学', project: 'PRJ-2025-018', usage: '分子生物学实验', time: '2025-03-15 10:32:00' },
  { id: 't4', name: '无水乙醇 500mL', type: 'RETURN', qty: 1, user: '王同学', project: 'PRJ-2025-012', usage: '未开封归还', time: '2025-03-16 16:10:00' },
  { id: 't5', name: '丁腈手套 M', type: 'SCRAP', qty: 2, user: '李实验', project: '-', usage: '包装破损报废', time: '2025-03-18 11:00:00' },
];

CZ.alerts = [
  { name: '盐酸 AR 500mL', qty: 5, safe: 8, level: 'danger', at: '2025-03-18 08:00:00' },
  { name: '丁腈手套 M', qty: 9, safe: 25, level: 'danger', at: '2025-03-18 08:00:00' },
  { name: '移液枪头 200μL', qty: 22, safe: 40, level: 'warning', at: '2025-03-17 08:00:00' },
];

CZ.top10 = [
  ['移液枪头', 126, 5670],
  ['丁腈手套', 98, 3136],
  ['无水乙醇', 74, 2664],
  ['培养皿', 61, 1098],
  ['盐酸 AR', 45, 1282],
  ['温湿度传感器', 32, 1984],
  ['离心管', 28, 840],
  ['滤纸', 22, 440],
  ['烧杯', 18, 540],
  ['pH试纸', 15, 225],
];

CZ.TYPE_LABEL = { IN: '入库', OUT: '领用', RETURN: '归还', SCRAP: '报废' };
