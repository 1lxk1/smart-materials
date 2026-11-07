import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Steps,
  Tag,
  message,
} from 'antd';
import { ScanOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import type { MaterialItem, OperationResult, StockBatch } from '../types';

/**
 * 扫码领用。
 *
 * 扫码链路：二维码 → /login?redirect=/outbound&batchCode=xxx → 登录 →
 * /outbound?batchCode=xxx → 本页读取 URL 参数自动选中对应批次。
 */
export default function OutboundPage() {
  const { user, quota, refresh } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const batchCodeParam = searchParams.get('batchCode') ?? '';

  const [form] = Form.useForm();
  const [step, setStep] = useState(0);
  const [batches, setBatches] = useState<StockBatch[]>([]);
  const [materials, setMaterials] = useState<MaterialItem[]>([]);
  const [selectedBatchId, setSelectedBatchId] = useState<number | undefined>();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<OperationResult | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [batchList, items] = await Promise.all([api.batches(), api.materials()]);
      setBatches(batchList);
      setMaterials(items);
      setLoadError(null);
      return batchList;
    } catch (e) {
      setLoadError((e as Error).message);
      return [] as StockBatch[];
    } finally {
      setLoading(false);
    }
  }, []);

  // 首次加载 + 解析 URL 中的 batchCode
  useEffect(() => {
    void (async () => {
      const batchList = await load();
      if (!batchCodeParam) return;
      const hit = batchList.find(
        (b) => b.batchCode === batchCodeParam || b.barcode === batchCodeParam,
      );
      if (hit) {
        setSelectedBatchId(hit.id);
        setStep(1);
        message.success(`已识别扫码批次 ${hit.batchCode}`);
      } else {
        message.warning(`未找到批次 ${batchCodeParam}，请手动选择或检查标签`);
      }
    })();
    // 只在挂载时执行一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const batch = useMemo(
    () => batches.find((b) => b.id === selectedBatchId),
    [batches, selectedBatchId],
  );
  const material = useMemo(
    () => materials.find((m) => m.id === batch?.itemId),
    [materials, batch],
  );

  const limit = quota?.limit ?? user?.monthlyLimit ?? null;
  const used = quota?.used ?? 0;
  const unlimited = quota?.unlimited ?? limit === null;
  const remaining = unlimited ? null : Math.max(0, (limit ?? 0) - used);
  const unitPrice = Number(batch?.unitPrice ?? material?.unitPrice ?? 0);

  const onSelectBatch = (id: number) => {
    setSelectedBatchId(id);
    setStep(1);
    const target = batches.find((b) => b.id === id);
    if (target) {
      const next = new URLSearchParams(searchParams);
      next.set('batchCode', target.batchCode);
      setSearchParams(next, { replace: true });
    }
  };

  const submit = async (values: { qty: number; projectNo: string; usage: string }) => {
    if (!batch) {
      message.error('请先扫码或选择批次');
      return;
    }
    setSubmitting(true);
    try {
      const res = await api.outbound({
        batchId: batch.id,
        batchCode: batch.batchCode,
        qty: values.qty,
        projectNo: values.projectNo,
        usage: values.usage,
      });
      setResult(res);
      setStep(2);
      message.success(`领用成功，剩余批次库存 ${res.batchQuantity}`);
      if (res.warning) {
        message.warning(`注意：材料总库存已低于安全库存（剩余 ${res.itemStock}）`);
      }
      await Promise.all([load(), refresh()]);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="cz-page">
      <div className="cz-page-title">
        <div>
          <h2>扫码领用</h2>
          <p>填写数量、用途、项目号；后端事务实时校验库存与个人月度限额</p>
        </div>
      </div>

      {batchCodeParam && (
        <Alert
          type="success"
          showIcon
          icon={<ScanOutlined />}
          style={{ marginBottom: 16 }}
          message={`扫码批次：${batchCodeParam}`}
          description={batch ? `已自动选中 ${batch.itemName ?? batch.batchCode}` : '批次未找到，请核对标签'}
        />
      )}
      {loadError && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="后端接口异常"
          description={loadError}
        />
      )}

      <Steps
        current={step}
        style={{ marginBottom: 24 }}
        items={[{ title: '扫码选料' }, { title: '填写领用' }, { title: '生成领用单' }]}
      />

      <Row gutter={16}>
        <Col xs={24} md={14}>
          <Form
            form={form}
            layout="vertical"
            initialValues={{ qty: 1, usage: '课程实验', projectNo: 'PRJ-2025-012' }}
            onFinish={submit}
          >
            <Form.Item label="扫码/选择批次" required>
              <Select
                showSearch
                loading={loading}
                value={selectedBatchId}
                onChange={onSelectBatch}
                optionFilterProp="label"
                placeholder="选择批次（或扫码自动带入）"
                options={batches.map((b) => ({
                  value: b.id,
                  label: `${b.batchCode} · ${b.itemName ?? ''}（库存 ${b.quantity}）`,
                }))}
              />
            </Form.Item>
            <Space wrap size="large">
              <Form.Item label="数量" name="qty" rules={[{ required: true, message: '请填写数量' }]}>
                <InputNumber min={0.001} max={batch ? Number(batch.quantity) : undefined} step={1} />
              </Form.Item>
              <Form.Item label="项目号" name="projectNo" rules={[{ required: true, message: '请填写项目号' }]}>
                <Input placeholder="PRJ-2025-xxx" style={{ width: 200 }} />
              </Form.Item>
            </Space>
            <Form.Item label="用途" name="usage" rules={[{ required: true, message: '请填写用途' }]}>
              <Input.TextArea rows={3} />
            </Form.Item>
            <Button type="primary" htmlType="submit" disabled={user?.role === 'guest'} loading={submitting}>
              确认出库
            </Button>
          </Form>
        </Col>

        <Col xs={24} md={10}>
          <Alert
            type="info"
            showIcon
            message="实时校验"
            description={
              <div>
                <div>当前批次库存：{batch?.quantity ?? 0}</div>
                <div>
                  个人月度已用 / 限额：
                  {unlimited ? `${used} / 不限` : `${used} / ${limit}`}
                </div>
                {remaining !== null && <div>本月还可领用：{remaining}</div>}
                <div>单价：¥{unitPrice.toFixed(2)}</div>
                <div>货架位：{batch?.location ?? '-'}</div>
              </div>
            }
            style={{ marginBottom: 16 }}
          />

          {result?.warning && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
              message="库存已低于安全库存"
              description={`${result.warning.itemName} 剩余 ${result.warning.currentQty}，安全库存 ${result.warning.safeStock}，系统已写入预警记录。`}
            />
          )}

          {step >= 2 && result && (
            <div className="cz-panel">
              <h3>领用单预览</h3>
              <p>领用人：{result.transaction.userName}</p>
              <p>材料：{result.transaction.itemName}</p>
              <p>批次：{result.transaction.batchCode ?? batch?.batchCode}</p>
              <p>数量：{result.transaction.qty}</p>
              <p>项目号：{result.transaction.projectNo}</p>
              <p>
                金额：¥{Number(result.transaction.amount ?? 0).toFixed(2)}
                <Tag color="green" style={{ marginLeft: 8 }}>
                  已计入经费
                </Tag>
              </p>
              <div
                style={{
                  marginTop: 12,
                  height: 72,
                  border: '1px dashed #9bb4bb',
                  borderRadius: 8,
                  display: 'grid',
                  placeItems: 'center',
                  color: '#667780',
                }}
              >
                手写签名区域
              </div>
            </div>
          )}
        </Col>
      </Row>
    </div>
  );
}
