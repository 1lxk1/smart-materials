/** 与后端 JSON 一一对应的领域模型（后端见 backend/src/main/java/com/smartmaterials）。 */

export type Role = 'guest' | 'student' | 'lab' | 'teacher' | 'buyer' | 'admin';

export type TxnType = 'IN' | 'OUT' | 'RETURN' | 'SCRAP';

export interface Permissions {
  canWriteStock: boolean;
  canManageDict: boolean;
  canApprove: boolean;
  canExport: boolean;
}

export interface Quota {
  month: string;
  used: number;
  limit: number | null;
  remaining: number | null;
  unlimited: boolean;
  unit?: string;
}

export interface SysUser {
  id: number;
  ldapUid: string;
  name: string;
  role: Role;
  email?: string | null;
  phone?: string | null;
  dept?: string | null;
  monthlyLimit?: number | null;
  status?: string;
  lastLoginTime?: string | null;
}

export interface MaterialCategory {
  id: number;
  parentId: number | null;
  code?: string;
  name: string;
  level?: number;
  unit: string;
  safeStock: number;
  createBy?: string;
  children?: MaterialCategory[];
}

export interface MaterialItem {
  id: number;
  categoryId: number;
  categoryPath?: string;
  code?: string;
  brand: string;
  model: string;
  spec: string;
  unitPrice: number;
  currency: string;
  unit: string;
  safeStock: number;
  stockQty: number;
  specs?: Record<string, unknown>;
}

export interface StockBatch {
  id: number;
  itemId: number;
  itemName?: string;
  brand?: string;
  model?: string;
  batchCode: string;
  barcode: string;
  quantity: number;
  initialQty?: number;
  location: string;
  expireDate: string;
  supplier?: string;
  status?: string;
  unitPrice?: number;
  safeStock?: number;
}

export interface StockTransaction {
  id: number;
  batchId: number;
  itemId?: number;
  itemName: string;
  txnType: TxnType;
  qty: number;
  unitPrice?: number;
  amount?: number;
  userId?: number;
  userName: string;
  projectNo: string;
  usage: string;
  status?: string;
  createTime: string;
  batchCode?: string;
  barcode?: string;
  attachments?: string[];
}

export interface StockAlert {
  id: number;
  itemId?: number;
  itemName: string;
  currentQty: number;
  safeStock: number;
  level: 'warning' | 'danger';
  status?: string;
  notifiedAt?: string | null;
  createTime?: string;
  shortage?: number;
}

/** 登录响应 */
export interface LoginResult {
  token: string;
  tokenType: string;
  expiresInMinutes: number;
  user: SysUser;
  permissions: Permissions;
}

/** GET /api/me */
export interface MeResult {
  user: SysUser;
  permissions: Permissions;
  quota: Quota;
}

/** 出入库操作结果 */
export interface OperationResult {
  transaction: StockTransaction;
  batch: StockBatch;
  batchQuantity: number;
  itemStock: number;
  warning: StockAlert | null;
  monthlyUsed: number | null;
  monthlyLimit: number | null;
}

/** 仪表盘聚合 */
export interface DashboardData {
  stats: {
    skuCount: number;
    monthOut: number;
    monthIn: number;
    monthReturn: number;
    monthScrap: number;
    lowStock: number;
    stockValue: number;
    month: string;
  };
  topConsumables: Array<{ name: string; times: number; qty: number; amount: number }>;
  recentTransactions: StockTransaction[];
  alerts: StockAlert[];
}

/** 导出明细行 */
export interface ReportRow {
  id: number;
  code: string;
  name: string;
  price: number;
  qty: number;
  amount: number;
  user: string;
  projectNo: string;
  usage: string;
  createTime?: string;
}

/** POST /api/batches/import 返回 */
export interface ImportedBatch {
  row: number;
  itemCode: string;
  batchCode: string | null;
  qty: number;
}

export interface ImportRowError {
  row: number;
  message: string;
}

export interface ImportResult {
  total: number;
  imported: number;
  batches: ImportedBatch[];
  errors: ImportRowError[];
}

/** POST /api/attachments 返回 */
export interface Attachment {
  url: string;
  name: string;
  size: number;
}

export const ROLE_LABEL: Record<Role, string> = {
  guest: '游客',
  student: '学生',
  lab: '实验员',
  teacher: '教师/PI',
  buyer: '采购员',
  admin: '系统管理员',
};

export const TXN_LABEL: Record<TxnType, { text: string; className: string }> = {
  IN: { text: '入库', className: 'cz-tag-in' },
  OUT: { text: '领用', className: 'cz-tag-out' },
  RETURN: { text: '归还', className: 'cz-tag-return' },
  SCRAP: { text: '报废', className: 'cz-tag-scrap' },
};
