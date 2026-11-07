/**
 * 运行时配置：全部来自 Vite 环境变量。
 *
 * 注意：Vite 的环境变量是「构建时」注入的，改完要重新 build 才生效。
 * Vercel 面板里的环境变量优先级高于 .env.production。
 */

const stripTrailingSlash = (value: string) => value.replace(/\/+$/, '');

const env = import.meta.env;

const isLoopback = (host: string) =>
  host === 'localhost' || host === '127.0.0.1' || host === '[::1]' || host === '::1' || host === '0.0.0.0';

/**
 * 手机联调关键：把「localhost / 127.0.0.1」自动换成当前访问用的主机名。
 *
 * 开发时若用局域网 IP 打开（http://192.168.x.x:5173），手机扫码后拿到的二维码
 * 和接口地址都会是同一个局域网 IP，而不是手机自己的 localhost。
 * 生产域名（非回环地址）不受影响。
 */
function localize(raw: string, fallbackOrigin: string): string {
  try {
    const url = new URL(raw, fallbackOrigin);
    if (isLoopback(url.hostname) && !isLoopback(window.location.hostname)) {
      url.hostname = window.location.hostname;
    }
    return stripTrailingSlash(url.toString());
  } catch {
    return stripTrailingSlash(raw);
  }
}

/** 前端对外域名：二维码里写死的入口地址，必须是公网 / 局域网可访问的地址。 */
const publicBaseUrl = localize(
  (env.VITE_PUBLIC_BASE_URL as string | undefined) ?? window.location.origin,
  window.location.origin,
);

export const appConfig = {
  title: env.VITE_APP_TITLE ?? '材智通·智慧材料库',
  /** 后端基地址，例如 https://smart-materials-api.onrender.com/api */
  apiBaseUrl: localize(env.VITE_API_BASE_URL ?? '/api', window.location.origin),
  /**
   * Mock 开关：默认关闭。
   * 只有显式设置 VITE_USE_MOCK=true 才会使用本地假数据（用于离线演示）。
   */
  useMock: String(env.VITE_USE_MOCK ?? 'false') === 'true',
  ldapEnabled: String(env.VITE_LDAP_ENABLED ?? 'false') === 'true',
  publicBaseUrl,
  isDev: env.DEV,
};

/**
 * 批次二维码内容（任务书要求）：
 * https://前端域名/login?redirect=/outbound&batchCode=当前批次号
 *
 * 扫码 → 登录页 → 登录成功后自动跳到 /outbound 并带上 batchCode → 出库页自动选中批次。
 */
export function buildBatchQrUrl(batchCode: string): string {
  const base = appConfig.publicBaseUrl || window.location.origin;
  return `${base}/login?redirect=/outbound&batchCode=${encodeURIComponent(batchCode)}`;
}

/** 系统入口二维码内容。 */
export function buildEntryUrl(): string {
  return `${appConfig.publicBaseUrl || window.location.origin}/login`;
}
