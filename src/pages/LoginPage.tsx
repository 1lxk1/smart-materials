import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, Input, Spin, Typography, message } from 'antd';
import { LockOutlined, ScanOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth';
import { appConfig } from '../config';

/**
 * 登录页。
 *
 * 支持二维码扫码直达：
 *   https://前端域名/login?redirect=/outbound&batchCode=IN20250301001
 * 登录成功后按 redirect 跳转，并把 batchCode 原样带过去，
 * 出库页据此自动选中批次。
 */
export default function LoginPage() {
  const { login, user, loading } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [submitting, setSubmitting] = useState(false);

  const redirect = searchParams.get('redirect') || '/';
  const batchCode = searchParams.get('batchCode') || '';

  /** 跳转目标：保留 batchCode 等参数。 */
  const target = useMemo(() => {
    if (!batchCode) return redirect;
    const joiner = redirect.includes('?') ? '&' : '?';
    return `${redirect}${joiner}batchCode=${encodeURIComponent(batchCode)}`;
  }, [redirect, batchCode]);

  // 已登录（或刚登录成功）直接跳转
  useEffect(() => {
    if (!loading && user) {
      navigate(target, { replace: true });
    }
  }, [loading, user, navigate, target]);

  if (loading) {
    return (
      <div className="cz-login">
        <Spin size="large" tip="正在校验登录状态…" />
      </div>
    );
  }

  return (
    <div className="cz-login">
      <div className="cz-login-card">
        <div className="cz-brand">
          <div className="cz-brand-mark">材</div>
          <div>
            <h1>材智通</h1>
            <p>智慧材料库数字化管理系统</p>
          </div>
        </div>
        <p className="cz-login-sub" style={{ margin: '12px 0 20px' }}>
          把工场的“物资仓库”变成一键可查、全程可追溯、报表自动生成的云端材料银行。
        </p>

        {batchCode && (
          <Alert
            type="success"
            showIcon
            icon={<ScanOutlined />}
            style={{ marginBottom: 16 }}
            message="已识别扫码批次"
            description={`批次号 ${batchCode}，登录后将自动跳到领用页并选中该批次。`}
          />
        )}

        <Form
          layout="vertical"
          initialValues={{ username: 'admin', password: '123456' }}
          onFinish={async (values: { username: string; password: string }) => {
            setSubmitting(true);
            try {
              await login(values.username, values.password);
              message.success('登录成功');
              navigate(target, { replace: true });
            } catch (error) {
              message.error((error as Error).message || '登录失败');
            } finally {
              setSubmitting(false);
            }
          }}
        >
          <Form.Item
            label="统一身份认证账号（LDAP UID）"
            name="username"
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="admin / lab01 / stu2024001" autoComplete="username" />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="初始密码 123456" autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large" loading={submitting}>
            进入系统
          </Button>
        </Form>

        <Typography.Paragraph style={{ marginTop: 16, marginBottom: 0, fontSize: 12, color: '#7a8b91' }}>
          接口地址：{appConfig.apiBaseUrl}
          <br />
          演示账号：admin / lab01 / stu2024001 / teacher01 / buyer01，密码 123456
        </Typography.Paragraph>
      </div>
    </div>
  );
}
