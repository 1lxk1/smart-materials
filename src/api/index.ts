import type {
  Attachment,
  DashboardData,
  ImportResult,
  LoginResult,
  MaterialCategory,
  MaterialItem,
  MeResult,
  OperationResult,
  Quota,
  ReportRow,
  StockAlert,
  StockBatch,
  StockTransaction,
  SysUser,
  TxnType,
} from '../types';
import { download, request, upload } from './http';

/** 后端接口封装：所有页面只通过这里访问服务端。 */
export const api = {
  // ---------------------------------------------------------------- 认证
  login(username: string, password: string) {
    return request<LoginResult>('/login', {
      method: 'POST',
      body: { username, password },
      auth: false,
    });
  },

  me() {
    return request<MeResult>('/me');
  },

  quota() {
    return request<Quota>('/stock/quota');
  },

  // ---------------------------------------------------------------- 仪表盘
  dashboard() {
    return request<DashboardData>('/dashboard');
  },

  // ---------------------------------------------------------------- 类目
  categories() {
    return request<MaterialCategory[]>('/categories');
  },

  categoryFlat() {
    return request<MaterialCategory[]>('/categories/flat');
  },

  createCategory(body: {
    parentId?: number | null;
    name: string;
    unit?: string;
    safeStock?: number;
    code?: string;
  }) {
    return request<MaterialCategory>('/categories', { method: 'POST', body });
  },

  // ---------------------------------------------------------------- 材料
  materials(keyword?: string) {
    return request<MaterialItem[]>('/materials', { query: { keyword } });
  },

  createMaterial(body: {
    categoryId: number;
    code: string;
    brand?: string;
    model?: string;
    spec?: string;
    unitPrice?: number;
    unit?: string;
    safeStock?: number;
    specs?: Record<string, unknown>;
  }) {
    return request<MaterialItem>('/materials', { method: 'POST', body });
  },

  // ---------------------------------------------------------------- 批次 / 入库
  batches(params: { keyword?: string; itemId?: number } = {}) {
    return request<StockBatch[]>('/batches', { query: params });
  },

  batchByCode(batchCode: string) {
    return request<StockBatch>(`/batches/${encodeURIComponent(batchCode)}`);
  },

  inbound(body: {
    itemId: number;
    qty: number;
    location?: string;
    expireDate?: string;
    supplier?: string;
    barcode?: string;
    remark?: string;
  }) {
    return request<OperationResult>('/batches', { method: 'POST', body });
  },

  /** Excel 批量入库：整份文件全成功才写库，失败返回逐行错误。 */
  importBatches(file: File) {
    const form = new FormData();
    form.append('file', file);
    return upload<ImportResult>('/batches/import', form);
  },

  /** 附件上传（损耗照片），拿到 url 后随归还/报废一起提交。 */
  uploadAttachment(file: File, category = 'scrap') {
    const form = new FormData();
    form.append('file', file);
    form.append('category', category);
    return upload<Attachment>('/attachments', form);
  },

  // ---------------------------------------------------------------- 领用 / 归还报废
  outbound(body: {
    batchId?: number;
    batchCode?: string;
    qty: number;
    projectNo: string;
    usage: string;
    remark?: string;
  }) {
    return request<OperationResult>('/stock/outbound', { method: 'POST', body });
  },

  returnScrap(body: {
    type: 'RETURN' | 'SCRAP';
    batchId: number;
    qty: number;
    usage: string;
    projectNo?: string;
    remark?: string;
    photos?: string[];
  }) {
    return request<OperationResult>('/stock/return-scrap', { method: 'POST', body });
  },

  // ---------------------------------------------------------------- 流水 / 预警
  transactions(type?: TxnType, limit = 50) {
    return request<StockTransaction[]>('/transactions', { query: { type, limit } });
  },

  alerts() {
    return request<StockAlert[]>('/alerts');
  },

  alertHistory() {
    return request<StockAlert[]>('/alerts/history');
  },

  notifyAlert(id: number) {
    return request<StockAlert>(`/alerts/${id}/notify`, { method: 'POST' });
  },

  // ---------------------------------------------------------------- 报表 / 用户
  reportRows(type: TxnType = 'OUT', projectNo?: string) {
    return request<ReportRow[]>('/reports/transactions', { query: { type, projectNo } });
  },

  /** 下载后端生成的 xlsx / pdf（真实文件，不是前端拼 CSV）。 */
  exportReport(params: {
    format: 'xlsx' | 'pdf';
    type?: string;
    projectNo?: string;
    from?: string;
    to?: string;
  }) {
    const { format, ...query } = params;
    return download(
      '/reports/export',
      { format, ...query },
      `经费对应表.${format === 'pdf' ? 'pdf' : 'xlsx'}`,
    );
  },

  users() {
    return request<SysUser[]>('/users');
  },
};

export type Api = typeof api;
