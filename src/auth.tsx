import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { api } from './api';
import { tokenStore, userCache } from './api/http';
import type { Permissions, Quota, SysUser } from './types';

interface AuthContextValue {
  user: SysUser | null;
  permissions: Permissions;
  quota: Quota | null;
  /** 首次校验 token 期间为 true，避免闪一下登录页 */
  loading: boolean;
  login: (username: string, password: string) => Promise<SysUser>;
  logout: () => void;
  refresh: () => Promise<void>;
  canWriteStock: boolean;
  canManageDict: boolean;
  canApprove: boolean;
  canExport: boolean;
}

const EMPTY_PERMISSIONS: Permissions = {
  canWriteStock: false,
  canManageDict: false,
  canApprove: false,
  canExport: false,
};

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * 认证上下文：不再使用本地假登录。
 * 登录 → POST /api/login 拿到真实 JWT → 后续请求带 Authorization: Bearer。
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SysUser | null>(() =>
    tokenStore.get() ? userCache.get<SysUser>() : null,
  );
  const [permissions, setPermissions] = useState<Permissions>(EMPTY_PERMISSIONS);
  const [quota, setQuota] = useState<Quota | null>(null);
  const [loading, setLoading] = useState<boolean>(() => Boolean(tokenStore.get()));

  const clearSession = useCallback(() => {
    tokenStore.clear();
    userCache.clear();
    setUser(null);
    setPermissions(EMPTY_PERMISSIONS);
    setQuota(null);
  }, []);

  const refresh = useCallback(async () => {
    if (!tokenStore.get()) {
      setLoading(false);
      return;
    }
    try {
      const me = await api.me();
      setUser(me.user);
      setPermissions(me.permissions);
      setQuota(me.quota);
      userCache.set(me.user);
    } catch {
      clearSession();
    } finally {
      setLoading(false);
    }
  }, [clearSession]);

  // 启动时用 JWT 换取当前用户，token 失效则自动登出
  useEffect(() => {
    void refresh();
  }, [refresh]);

  // 任何请求收到 401 都会广播该事件
  useEffect(() => {
    const handler = () => clearSession();
    window.addEventListener('cz:unauthorized', handler);
    return () => window.removeEventListener('cz:unauthorized', handler);
  }, [clearSession]);

  const login = useCallback(
    async (username: string, password: string) => {
      const result = await api.login(username.trim(), password);
      tokenStore.set(result.token);
      userCache.set(result.user);
      setUser(result.user);
      setPermissions(result.permissions);
      // 登录响应不含额度，这里补一次 /me 拿到本月领用情况
      try {
        const me = await api.me();
        setQuota(me.quota);
        setUser(me.user);
        setPermissions(me.permissions);
        userCache.set(me.user);
        return me.user;
      } catch {
        return result.user;
      }
    },
    [],
  );

  const logout = useCallback(() => {
    clearSession();
  }, [clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      permissions,
      quota,
      loading,
      login,
      logout,
      refresh,
      canWriteStock: permissions.canWriteStock,
      canManageDict: permissions.canManageDict,
      canApprove: permissions.canApprove,
      canExport: permissions.canExport,
    }),
    [user, permissions, quota, loading, login, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
