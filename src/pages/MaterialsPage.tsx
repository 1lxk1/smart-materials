import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Input, Space, Table, Tag, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { api } from '../api';
import type { MaterialItem } from '../types';

/** 材料台账：数据来自 material_item 表。 */
export default function MaterialsPage() {
  const [items, setItems] = useState<MaterialItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (kw?: string) => {
    setLoading(true);
    try {
      setItems(await api.materials(kw));
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
          <h2>材料台账</h2>
          <p>品牌 / 型号 / 规格 / 单价，对应 material_item</p>
        </div>
        <Space>
          <Input.Search
            placeholder="搜索品牌、型号、规格"
            allowClear
            style={{ width: 260 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={(value) => void load(value)}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void load(keyword)}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} disabled>
            新建材料
          </Button>
        </Space>
      </div>

      {error && <Alert type="error" showIcon message="台账加载失败" description={error} style={{ marginBottom: 12 }} />}

      <Table
        rowKey="id"
        loading={loading}
        dataSource={items}
        pagination={{ pageSize: 8 }}
        columns={[
          { title: '类目路径', dataIndex: 'categoryPath', ellipsis: true },
          { title: '品牌', dataIndex: 'brand', width: 110 },
          { title: '型号', dataIndex: 'model', width: 120 },
          { title: '规格', dataIndex: 'spec', width: 160 },
          {
            title: '单价',
            dataIndex: 'unitPrice',
            width: 110,
            render: (v: number, r: MaterialItem) => `${r.currency ?? 'CNY'} ${Number(v ?? 0).toFixed(2)}`,
          },
          { title: '单位', dataIndex: 'unit', width: 70 },
          {
            title: '库存',
            dataIndex: 'stockQty',
            width: 110,
            render: (v: number, r: MaterialItem) => (
              <Tag color={Number(v) < Number(r.safeStock) ? 'error' : 'success'}>
                {v} / 安全 {r.safeStock}
              </Tag>
            ),
          },
        ]}
      />
    </div>
  );
}
