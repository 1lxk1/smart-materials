import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Space, Table, Tag, message } from 'antd';
import { MailOutlined, ReloadOutlined, WechatOutlined } from '@ant-design/icons';
import { api } from '../api';
import { useAuth } from '../auth';
import type { StockAlert } from '../types';

/** 库存预警：低于安全库存时由后端事务自动写入 stock_alert。 */
export default function AlertsPage() {
  const { user } = useAuth();
  const [alerts, setAlerts] = useState<StockAlert[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notifying, setNotifying] = useState(false);

  const canNotify = user?.role === 'buyer' || user?.role === 'admin' || user?.role === 'lab';

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setAlerts(await api.alerts());
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

  const notifyAll = async (channel: '邮件' | '企业微信') => {
    if (alerts.length === 0) {
      message.info('当前没有需要催办的预警');
      return;
    }
    setNotifying(true);
    try {
      await Promise.all(alerts.map((a) => api.notifyAlert(a.id)));
      message.success(`已向采购员推送 ${alerts.length} 条${channel}提醒`);
      await load();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setNotifying(false);
    }
  };

  return (
    <div className="cz-page">
      <div className="cz-page-title">
        <div>
          <h2>库存预警</h2>
          <p>低于安全库存时自动生成预警，可一键催办采购员</p>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
          <Button
            icon={<MailOutlined />}
            disabled={!canNotify}
            loading={notifying}
            onClick={() => void notifyAll('邮件')}
          >
            邮件催办
          </Button>
          <Button
            icon={<WechatOutlined />}
            type="primary"
            disabled={!canNotify}
            loading={notifying}
            onClick={() => void notifyAll('企业微信')}
          >
            企微催办
          </Button>
        </Space>
      </div>

      {error && <Alert type="error" showIcon message="预警加载失败" description={error} style={{ marginBottom: 12 }} />}
      {!canNotify && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="当前角色只读；催办需要采购员 / 实验员 / 管理员权限"
        />
      )}

      <Table
        rowKey="id"
        loading={loading}
        dataSource={alerts}
        locale={{ emptyText: '库存健康，暂无预警' }}
        columns={[
          { title: '材料', dataIndex: 'itemName' },
          { title: '当前库存', dataIndex: 'currentQty', width: 110 },
          { title: '安全库存', dataIndex: 'safeStock', width: 110 },
          {
            title: '缺口',
            width: 90,
            render: (_: unknown, r: StockAlert) => (Number(r.safeStock) - Number(r.currentQty)).toFixed(0),
          },
          {
            title: '级别',
            dataIndex: 'level',
            width: 100,
            render: (v: string) => (
              <Tag color={v === 'danger' ? 'error' : 'warning'}>{v === 'danger' ? '紧急' : '关注'}</Tag>
            ),
          },
          { title: '最近通知', dataIndex: 'notifiedAt', width: 180, render: (v?: string) => v ?? '—' },
          { title: '生成时间', dataIndex: 'createTime', width: 180 },
          {
            title: '操作',
            width: 140,
            render: (_: unknown, r: StockAlert) => (
              <Button
                type="link"
                disabled={!canNotify}
                onClick={async () => {
                  try {
                    await api.notifyAlert(r.id);
                    message.success('已推送采购提醒');
                    await load();
                  } catch (e) {
                    message.error((e as Error).message);
                  }
                }}
              >
                通知采购员
              </Button>
            ),
          },
        ]}
      />
    </div>
  );
}
