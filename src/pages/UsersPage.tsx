import { useCallback, useEffect, useState } from 'react';
import { Alert, Table, Tag, message } from 'antd';
import { api } from '../api';
import { ROLE_LABEL, type Role, type SysUser } from '../types';

const roleColor: Record<Role, string> = {
  guest: 'default',
  student: 'blue',
  lab: 'cyan',
  teacher: 'purple',
  buyer: 'gold',
  admin: 'magenta',
};

/** 用户与权限：数据来自 sys_user（role / ldap_uid / monthly_limit）。 */
export default function UsersPage() {
  const [users, setUsers] = useState<SysUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setUsers(await api.users());
      setError(null);
    } catch (e) {
      setError((e as Error).message);
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="cz-page">
      <div className="cz-page-title">
        <div>
          <h2>用户与权限</h2>
          <p>游客仅查 · 学生领用 · 实验员出入库 · 管理员字典维护（可对接 LDAP）</p>
        </div>
      </div>

      {error && <Alert type="error" showIcon message="用户加载失败" description={error} style={{ marginBottom: 12 }} />}

      <Table
        rowKey="id"
        loading={loading}
        dataSource={users}
        columns={[
          { title: 'LDAP UID', dataIndex: 'ldapUid', width: 140 },
          { title: '姓名', dataIndex: 'name', width: 120 },
          {
            title: '角色',
            dataIndex: 'role',
            width: 130,
            render: (v: Role) => <Tag color={roleColor[v] || 'default'}>{ROLE_LABEL[v] ?? v}</Tag>,
          },
          { title: '院系/部门', dataIndex: 'dept' },
          { title: '邮箱', dataIndex: 'email' },
          { title: '手机', dataIndex: 'phone', width: 130 },
          {
            title: '月度限额',
            dataIndex: 'monthlyLimit',
            width: 110,
            render: (v?: number | null) => (v === null || v === undefined ? '—' : v),
          },
          {
            title: '最近登录',
            dataIndex: 'lastLoginTime',
            width: 180,
            render: (v?: string | null) => v ?? '—',
          },
        ]}
      />
    </div>
  );
}
