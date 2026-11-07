import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Spin, Tree, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { DataNode } from 'antd/es/tree';
import { api } from '../api';
import type { MaterialCategory } from '../types';
import { useAuth } from '../auth';

function toTree(nodes: MaterialCategory[]): DataNode[] {
  return nodes.map((n) => ({
    key: n.id,
    title: `${n.name}（单位:${n.unit} · 安全库存:${n.safeStock}）`,
    children: n.children && n.children.length ? toTree(n.children) : undefined,
  }));
}

function flatten(nodes: MaterialCategory[], depth = 0): Array<{ id: number; name: string; depth: number }> {
  return nodes.flatMap((n) => [
    { id: n.id, name: n.name, depth },
    ...(n.children ? flatten(n.children, depth + 1) : []),
  ]);
}

export default function CategoriesPage() {
  const { canManageDict } = useAuth();
  const [tree, setTree] = useState<MaterialCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setTree(await api.categories());
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const options = useMemo(
    () =>
      flatten(tree).map((node) => ({
        value: node.id,
        label: `${'　'.repeat(node.depth)}${node.name}`,
      })),
    [tree],
  );

  return (
    <div className="cz-page">
      <div className="cz-page-title">
        <div>
          <h2>材料类目树</h2>
          <p>支持四级：大类 → 子类 → 品牌 → 型号；数据存于 material_category</p>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={!canManageDict}
            onClick={() => {
              form.resetFields();
              setOpen(true);
            }}
          >
            新增类目
          </Button>
        </Space>
      </div>

      {!canManageDict && (
        <div style={{ marginBottom: 12, color: '#9a3412' }}>
          当前角色仅可查看；字典维护需管理员权限。
        </div>
      )}
      {error && <Alert type="error" showIcon message="类目加载失败" description={error} style={{ marginBottom: 12 }} />}

      {loading ? (
        <Spin />
      ) : (
        <Tree
          showLine
          defaultExpandAll
          treeData={toTree(tree)}
          style={{ background: '#fff', padding: 12, borderRadius: 12 }}
        />
      )}

      <Modal
        title="新增类目节点"
        open={open}
        confirmLoading={submitting}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={async (values: {
            parentId?: number;
            name: string;
            unit: string;
            safeStock: number;
          }) => {
            setSubmitting(true);
            try {
              await api.createCategory({
                parentId: values.parentId ?? null,
                name: values.name,
                unit: values.unit,
                safeStock: values.safeStock,
              });
              message.success('类目已创建');
              setOpen(false);
              await load();
            } catch (e) {
              message.error((e as Error).message);
            } finally {
              setSubmitting(false);
            }
          }}
        >
          <Form.Item label="挂载到（留空=顶级大类）" name="parentId">
            <Select allowClear showSearch optionFilterProp="label" options={options} placeholder="顶级大类" />
          </Form.Item>
          <Form.Item label="名称" name="name" rules={[{ required: true, message: '请填写名称' }]}>
            <Input />
          </Form.Item>
          <Space style={{ display: 'flex' }} size="large">
            <Form.Item label="单位" name="unit" rules={[{ required: true, message: '请填写单位' }]} initialValue="个">
              <Input style={{ width: 120 }} />
            </Form.Item>
            <Form.Item label="安全库存" name="safeStock" rules={[{ required: true, message: '请填写安全库存' }]} initialValue={0}>
              <InputNumber min={0} style={{ width: 120 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  );
}
