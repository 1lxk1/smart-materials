import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { useAuth } from './auth';
import AppLayout from './layouts/AppLayout';
import AlertsPage from './pages/AlertsPage';
import CategoriesPage from './pages/CategoriesPage';
import DashboardPage from './pages/DashboardPage';
import InboundPage from './pages/InboundPage';
import InventoryPage from './pages/InventoryPage';
import LoginPage from './pages/LoginPage';
import MaterialsPage from './pages/MaterialsPage';
import OutboundPage from './pages/OutboundPage';
import ReportsPage from './pages/ReportsPage';
import ReturnScrapPage from './pages/ReturnScrapPage';
import UsersPage from './pages/UsersPage';

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div style={{ minHeight: '60vh', display: 'grid', placeItems: 'center' }}>
        <Spin size="large" tip="正在校验登录状态…" />
      </div>
    );
  }
  if (!user) {
    // 带上原始地址（含 batchCode），登录后能回到扫码目标页
    const target = `${location.pathname}${location.search}`;
    return <Navigate to={`/login?redirect=${encodeURIComponent(target)}`} replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <PrivateRoute>
              <AppLayout />
            </PrivateRoute>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="categories" element={<CategoriesPage />} />
          <Route path="materials" element={<MaterialsPage />} />
          <Route path="inbound" element={<InboundPage />} />
          <Route path="outbound" element={<OutboundPage />} />
          <Route path="return-scrap" element={<ReturnScrapPage />} />
          <Route path="inventory" element={<InventoryPage />} />
          <Route path="alerts" element={<AlertsPage />} />
          <Route path="reports" element={<ReportsPage />} />
          <Route path="users" element={<UsersPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
