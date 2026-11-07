import { useMemo, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  AlertOutlined,
  AppstoreOutlined,
  BarChartOutlined,
  DatabaseOutlined,
  ExportOutlined,
  InboxOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  RollbackOutlined,
  ScanOutlined,
  TeamOutlined,
  ShoppingCartOutlined,
} from '@ant-design/icons';
import { Avatar, Dropdown, Layout, Menu, Space, Tag, Typography } from 'antd';
import { useAuth } from '../auth';
import { appConfig } from '../config';
import { ROLE_LABEL } from '../types';

const { Header, Sider, Content } = Layout;

const menuItems = [
  { key: '/', icon: <BarChartOutlined />, label: <Link to="/">仪表盘</Link> },
  { key: '/categories', icon: <AppstoreOutlined />, label: <Link to="/categories">材料类目</Link> },
  { key: '/materials', icon: <DatabaseOutlined />, label: <Link to="/materials">材料台账</Link> },
  { key: '/inbound', icon: <InboxOutlined />, label: <Link to="/inbound">入库管理</Link> },
  { key: '/outbound', icon: <ScanOutlined />, label: <Link to="/outbound">扫码领用</Link> },
  { key: '/return-scrap', icon: <RollbackOutlined />, label: <Link to="/return-scrap">归还/报废</Link> },
  { key: '/inventory', icon: <ShoppingCartOutlined />, label: <Link to="/inventory">库存批次</Link> },
  { key: '/alerts', icon: <AlertOutlined />, label: <Link to="/alerts">库存预警</Link> },
  { key: '/reports', icon: <ExportOutlined />, label: <Link to="/reports">统计导出</Link> },
  { key: '/users', icon: <TeamOutlined />, label: <Link to="/users">用户权限</Link> },
];

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout, quota } = useAuth();

  const selected = useMemo(() => {
    const hit = menuItems.find((item) =>
      item.key === '/' ? location.pathname === '/' : location.pathname.startsWith(item.key),
    );
    return [hit?.key ?? '/'];
  }, [location.pathname]);

  return (
    <Layout className="cz-shell" style={{ minHeight: '100vh' }}>
      <Sider collapsible collapsed={collapsed} trigger={null} width={232}>
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            paddingInline: collapsed ? 16 : 20,
            color: '#7ed6c1',
            fontWeight: 700,
            fontSize: collapsed ? 16 : 18,
          }}
        >
          <span
            style={{
              width: 34,
              height: 34,
              borderRadius: 10,
              background: 'rgba(126,214,193,0.16)',
              display: 'grid',
              placeItems: 'center',
            }}
          >
            材
          </span>
          {!collapsed && <span>材智通</span>}
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={selected} items={menuItems} />
      </Sider>
      <Layout>
        <Header>
          <Space>
            <span
              onClick={() => setCollapsed((v) => !v)}
              style={{ cursor: 'pointer', fontSize: 18, color: '#0b3d4a' }}
            >
              {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            </span>
            <Typography.Text type="secondary">智慧材料库数字化管理系统</Typography.Text>
          </Space>
          <Space size="middle">
            {quota && !quota.unlimited && (
              <Tag color={Number(quota.used) >= Number(quota.limit) ? 'error' : 'blue'}>
                本月领用 {quota.used}/{quota.limit}
              </Tag>
            )}
            <Tag color={appConfig.useMock ? 'orange' : 'green'}>
              {appConfig.useMock ? 'Mock 数据' : '真实接口'}
            </Tag>
            <Dropdown
              menu={{
                items: [
                  {
                    key: 'logout',
                    icon: <LogoutOutlined />,
                    label: '退出登录',
                    onClick: () => {
                      logout();
                      navigate('/login');
                    },
                  },
                ],
              }}
            >
              <Space style={{ cursor: 'pointer' }}>
                <Avatar style={{ background: '#0b3d4a' }}>
                  {user?.name?.slice(0, 1) ?? 'U'}
                </Avatar>
                <div style={{ lineHeight: 1.2 }}>
                  <div style={{ fontWeight: 600 }}>{user?.name}</div>
                  <div style={{ fontSize: 12, color: '#667780' }}>
                    {user ? ROLE_LABEL[user.role] : ''}
                  </div>
                </div>
              </Space>
            </Dropdown>
          </Space>
        </Header>
        <Content className="cz-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
