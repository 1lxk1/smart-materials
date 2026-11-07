/**
 * 兼容层：这个文件原本返回本地 Mock 数据，现已替换为真实后端请求。
 *
 * 新的页面代码请直接使用 `src/api`：
 *   import { api } from '../api';
 *   const list = await api.materials();
 *
 * 这里保留 `apiGet(path)` 只是为了老代码平滑迁移，内部同样走 fetch + JWT。
 * 若确实需要离线演示（不连后端），请使用 preview/ 静态演示页，
 * 或者显式设置 VITE_USE_MOCK=true 并自行实现 mock 分支。
 */
import { api } from '../api';
import { appConfig } from '../config';
import type {
  MaterialCategory,
  MaterialItem,
  StockAlert,
  StockBatch,
  StockTransaction,
  SysUser,
} from '../types';

const routes: Record<string, () => Promise<unknown>> = {
  '/users': () => api.users() as Promise<SysUser[]>,
  '/categories': () => api.categories() as Promise<MaterialCategory[]>,
  '/materials': () => api.materials() as Promise<MaterialItem[]>,
  '/batches': () => api.batches() as Promise<StockBatch[]>,
  '/transactions': () => api.transactions() as Promise<StockTransaction[]>,
  '/alerts': () => api.alerts() as Promise<StockAlert[]>,
};

/** 真实请求：按路径转发到对应的后端接口。 */
export async function apiGet<T>(path: string): Promise<T> {
  if (appConfig.useMock) {
    throw new Error(
      'VITE_USE_MOCK=true 已开启，但本地 Mock 数据源已被移除；请关闭 Mock 或改用 preview/ 离线演示页',
    );
  }
  const handler = routes[path];
  if (!handler) {
    throw new Error(`未定义的接口路径: ${path}`);
  }
  return (await handler()) as T;
}

export { api };
