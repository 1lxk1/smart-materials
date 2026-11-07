import { useCallback, useEffect, useState } from 'react';
import { Alert, Card, List, Progress, Skeleton, Table, Tag, message } from 'antd';
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { api } from '../api';
import { TXN_LABEL } from '../types';
import type { DashboardData, StockTransaction } from '../types';

export default function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api.dashboard());
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

  if (loading && !data) {
    return (
      <div className="cz-page">
        <Skeleton active paragraph={{ rows: 8 }} />
      </div>
    );
  }

  const stats = data?.stats;
  const top = data?.topConsumables ?? [];
  const maxAmount = top.length ? Math.max(...top.map((t) => Number(t.amount))) : 1;

  return (
    <div className="cz-page">
      <div className="cz-page-title">
        <div>
          <h2>运营仪表盘</h2>
          <p>月度 TOP10 耗材、资金占比与库存健康度一览（数据来自 PostgreSQL）</p>
        </div>
      </div>

      {error && <Alert type="error" showIcon message="仪表盘数据加载失败" description={error} style={{ marginBottom: 16 }} />}

      <div className="cz-stat-grid">
        <div className="cz-stat">
          <div className="label">在库 SKU</div>
          <div className="value">{stats?.skuCount ?? 0}</div>
        </div>
        <div className="cz-stat">
          <div className="label">本月领用单</div>
          <div className="value">{stats?.monthOut ?? 0}</div>
        </div>
        <div className="cz-stat warn">
          <div className="label">低于安全库存</div>
          <div className="value">{stats?.lowStock ?? 0}</div>
        </div>
        <div className="cz-stat">
          <div className="label">库存估值（元）</div>
          <div className="value">{Number(stats?.stockValue ?? 0).toFixed(0)}</div>
        </div>
      </div>

      <div className="cz-panel-grid">
        <div className="cz-panel">
          <h3>月度 TOP10 耗材</h3>
          {top.length === 0 ? (
            <Alert type="info" showIcon message="本月还没有领用记录" />
          ) : (
            <div style={{ width: '100%', height: 320 }}>
              <ResponsiveContainer>
                <BarChart data={top} margin={{ left: 4, right: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="name" tick={{ fontSize: 11 }} interval={0} angle={-20} textAnchor="end" height={70} />
                  <YAxis />
                  <Tooltip />
                  <Bar dataKey="qty" name="领用量" fill="#0b3d4a" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>

        <div className="cz-panel">
          <h3>资金占比</h3>
          <List
            dataSource={top.slice(0, 6)}
            locale={{ emptyText: '暂无数据' }}
            renderItem={(item) => (
              <List.Item>
                <div style={{ width: '100%' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>{item.name}</span>
                    <span>¥{Number(item.amount).toFixed(2)}</span>
                  </div>
                  <Progress
                    percent={Math.round((Number(item.amount) / maxAmount) * 100)}
                    showInfo={false}
                    strokeColor="#7ed6c1"
                  />
                </div>
              </List.Item>
            )}
          />
        </div>
      </div>

      <div className="cz-panel-grid" style={{ marginTop: 16 }}>
        <div className="cz-panel">
          <h3>最近事务</h3>
          <Table
            size="small"
            pagination={false}
            rowKey="id"
            dataSource={data?.recentTransactions ?? []}
            locale={{ emptyText: '暂无流水' }}
            columns={[
              { title: '材料', dataIndex: 'itemName' },
              {
                title: '类型',
                dataIndex: 'txnType',
                render: (v: StockTransaction['txnType']) => (
                  <Tag className={TXN_LABEL[v]?.className}>{TXN_LABEL[v]?.text ?? v}</Tag>
                ),
              },
              { title: '数量', dataIndex: 'qty', width: 70 },
              { title: '人员', dataIndex: 'userName', width: 90 },
              { title: '时间', dataIndex: 'createTime', width: 160 },
            ]}
          />
        </div>
        <div className="cz-panel">
          <h3>预警快讯</h3>
          <List
            dataSource={data?.alerts ?? []}
            locale={{ emptyText: '库存健康，暂无预警' }}
            renderItem={(item) => (
              <List.Item>
                <Card size="small" style={{ width: '100%' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <strong>{item.itemName}</strong>
                    <Tag color={item.level === 'danger' ? 'error' : 'warning'}>
                      {item.level === 'danger' ? '紧急' : '关注'}
                    </Tag>
                  </div>
                  <div style={{ color: '#667780', marginTop: 6 }}>
                    当前 {item.currentQty} / 安全库存 {item.safeStock}
                  </div>
                </Card>
              </List.Item>
            )}
          />
        </div>
      </div>
    </div>
  );
}
