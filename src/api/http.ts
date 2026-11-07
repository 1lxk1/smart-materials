import { appConfig } from '../config';

const TOKEN_KEY = 'cz_token';
const USER_KEY = 'cz_user';

/** 统一的接口错误：带 HTTP 状态码，页面直接展示 message。 */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export const tokenStore = {
  get(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  },
  set(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
  },
  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },
};

export const userCache = {
  get<T>(): T | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as T;
    } catch {
      return null;
    }
  },
  set(value: unknown): void {
    localStorage.setItem(USER_KEY, JSON.stringify(value));
  },
  clear(): void {
    localStorage.removeItem(USER_KEY);
  },
};

type QueryValue = string | number | boolean | undefined | null;

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, QueryValue>;
  /** 是否带 JWT，默认 true；登录接口除外 */
  auth?: boolean;
  /** 超时毫秒，Render 免费实例冷启动较慢，默认 60s */
  timeoutMs?: number;
}

/** 拼接完整地址（兼容 /api 相对路径与 https://host/api 绝对地址）。 */
function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = new URL(`${appConfig.apiBaseUrl}${path}`, window.location.origin);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    });
  }
  return url.toString();
}

/**
 * 真实 HTTP 客户端：统一带 JWT、统一解析错误。
 * 401 会清除本地 token 并广播 `cz:unauthorized`，由 AuthProvider 负责跳转登录页。
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, auth = true, timeoutMs = 60_000 } = options;

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (auth) {
    const token = tokenStore.get();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (error) {
    if ((error as Error).name === 'AbortError') {
      throw new ApiError(0, '请求超时：后端可能正在冷启动，请稍后重试');
    }
    throw new ApiError(0, `网络异常，无法连接后端：${(error as Error).message}`);
  } finally {
    window.clearTimeout(timer);
  }

  if (response.status === 401) {
    tokenStore.clear();
    window.dispatchEvent(new CustomEvent('cz:unauthorized'));
    throw new ApiError(401, '登录已过期，请重新登录');
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  let payload: unknown = undefined;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = text;
    }
  }

  if (!response.ok) {
    const message =
      (payload as { message?: string })?.message ??
      (typeof payload === 'string' ? payload : '') ??
      `接口请求失败（HTTP ${response.status}）`;
    throw new ApiError(response.status, message || `接口请求失败（HTTP ${response.status}）`);
  }

  return payload as T;
}

/** 401 统一处理：清 token + 广播，供 AuthProvider 跳登录页。 */
function handleUnauthorized(): never {
  tokenStore.clear();
  window.dispatchEvent(new CustomEvent('cz:unauthorized'));
  throw new ApiError(401, '登录已过期，请重新登录');
}

/** 从 Content-Disposition 解析文件名（支持 filename*=UTF-8''...）。 */
function parseFileName(disposition: string | null, fallback: string): string {
  if (!disposition) return fallback;
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1]);
    } catch {
      /* 忽略非法编码，退回 fallback */
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain?.[1] ?? fallback;
}

/** multipart 上传（不要手动设置 Content-Type，浏览器会补 boundary）。 */
export async function upload<T>(path: string, formData: FormData, timeoutMs = 120_000): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;

  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  let response: Response;
  try {
    response = await fetch(buildUrl(path), {
      method: 'POST',
      headers,
      body: formData,
      signal: controller.signal,
    });
  } catch (error) {
    if ((error as Error).name === 'AbortError') {
      throw new ApiError(0, '上传超时，请检查网络或稍后重试');
    }
    throw new ApiError(0, `上传失败，无法连接后端：${(error as Error).message}`);
  } finally {
    window.clearTimeout(timer);
  }

  if (response.status === 401) handleUnauthorized();

  const text = await response.text();
  let payload: unknown;
  try {
    payload = text ? JSON.parse(text) : undefined;
  } catch {
    payload = text;
  }
  if (!response.ok) {
    const message = (payload as { message?: string })?.message;
    throw new ApiError(response.status, message || `上传失败（HTTP ${response.status}）`);
  }
  return payload as T;
}

/**
 * 下载二进制文件（Excel / PDF）。
 *
 * <p>不能直接用 <a href>：接口要带 JWT，必须走 fetch 拿 blob 再触发保存。</p>
 */
export async function download(
  path: string,
  query: Record<string, QueryValue> | undefined,
  fallbackName: string,
  timeoutMs = 120_000,
): Promise<{ blob: Blob; fileName: string }> {
  const headers: Record<string, string> = {};
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;

  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), { headers, signal: controller.signal });
  } catch (error) {
    if ((error as Error).name === 'AbortError') {
      throw new ApiError(0, '导出超时，请缩小时间区间后重试');
    }
    throw new ApiError(0, `导出失败，无法连接后端：${(error as Error).message}`);
  } finally {
    window.clearTimeout(timer);
  }

  if (response.status === 401) handleUnauthorized();

  if (!response.ok) {
    const text = await response.text();
    let message = `导出失败（HTTP ${response.status}）`;
    try {
      const parsed = JSON.parse(text) as { message?: string };
      if (parsed.message) message = parsed.message;
    } catch {
      /* 非 JSON 错误体，保留默认提示 */
    }
    throw new ApiError(response.status, message);
  }

  const fileName = parseFileName(response.headers.get('Content-Disposition'), fallbackName);
  return { blob: await response.blob(), fileName };
}

/** 触发浏览器保存 Blob。 */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
