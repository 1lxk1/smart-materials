import { useCallback, useEffect, useState } from 'react';
import { Alert, Input, Space, Table, Tag, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import { api } from '../api';
import type { StockBatch } from '../types';

/** 库存批次：对应 stock_batch（批次号、数量、有效期、条码、货架位）。 */
export default function InventoryPage() {
  const [batches, setBatches] = useState<StockBatch[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (keyword?: string) => {
    setLoading(true);
    try {
      setBatches(await api.batches({ keyword }));
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
          <h2>库存批次</h2>
          <p>对应 stock_batch：批次号、数量、有效期、条码、货架位</p>
        </div>
        <Space>
          <Input.Search
            placeholder="按条码/批次号检索"
            style={{ width: 240 }}
            allowClear
            onSearch={(value) => void load(value)}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
        </Space>
      </div>

      {error && <Alert type="error" showIcon message="批次加载失败" description={error} style={{ marginBottom: 12 }} />}

      <Table
        rowKey="id"
        loading={loading}
        dataSource={batches}
        columns={[
          { title: '批次号', dataIndex: 'batchCode' },
          { title: '材料', dataIndex: 'itemName' },
          { title: '条码', dataIndex: 'barcode' },
          {
            title: '数量',
            dataIndex: 'quantity',
            width: 110,
            render: (v: number, r: StockBatch) => {
              const low = r.safeStock != null && Number(v) < Number(r.safeStock);
              return <Tag color={low ? 'error' : 'processing'}>{v}</Tag>;
            },
          },
          { title: '货架位', dataIndex: 'location', width: 100 },
          { title: '有效期', dataIndex: 'expireDate', width: 120 },
          {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (v: string) => (
              <Tag color={v === 'DEPLETED' ? 'default' : 'green'}>
                {v === 'DEPLETED' ? '已用完' : '在库'}
              </Tag>
            ),
          },
        ]}
      />
    </div>
  );
}
