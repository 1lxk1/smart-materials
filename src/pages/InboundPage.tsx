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
  Table,
  Tag,
  Upload,
  message,
} from 'antd';
import { PrinterOutlined, QrcodeOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import { QRCodeCanvas } from 'qrcode.react';
import { api } from '../api';
import { useAuth } from '../auth';
import { buildBatchQrUrl } from '../config';
import type { ImportResult, MaterialItem, StockBatch } from '../types';

interface LabelInfo {
  batchCode: string;
  barcode: string;
  itemName: string;
  location?: string;
  expireDate?: string;
  quantity?: number;
}

/**
 * 入库管理。
 *
 * 二维码不再是假占位：使用 qrcode.react 生成真实可扫的二维码，
 * 内容固定为 https://前端域名/login?redirect=/outbound&batchCode=当前批次号
 */
export default function InboundPage() {
  const { canWriteStock } = useAuth();
  const [form] = Form.useForm();
  const [materials, setMaterials] = useState<MaterialItem[]>([]);
  const [batches, setBatches] = useState<StockBatch[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [label, setLabel] = useState<LabelInfo | null>(null);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<ImportResult | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [items, batchList] = await Promise.all([api.materials(), api.batches()]);
      setMaterials(items);
      setBatches(batchList);
      setError(null);
      setLabel((prev) => prev ?? (batchList.length > 0 ? toLabel(batchList[0]) : null));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const qrUrl = useMemo(() => (label ? buildBatchQrUrl(label.batchCode) : ''), [label]);

  const printLabel = useCallback(() => {
    if (!label) return;
    const canvas = document.getElementById('batch-qr-canvas') as HTMLCanvasElement | null;
    const dataUrl = canvas?.toDataURL('image/png') ?? '';
    const win = window.open('', '_blank', 'width=460,height=640');
    if (!win) {
      message.warning('浏览器拦截了弹窗，请允许弹窗后重试');
      return;
    }
    win.document.write(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8" />
      <title>批次标签 ${label.batchCode}</title>
      <style>
        body{font-family:"Segoe UI","Microsoft YaHei",sans-serif;padding:24px;text-align:center;color:#0b3d4a}
        h1{font-size:18px;margin:0 0 4px}
        .code{font-family:Consolas,monospace;font-size:13px;color:#156677;margin-top:8px}
        img{width:260px;height:260px}
        .meta{font-size:12px;color:#4b5f66;margin-top:6px}
        .tip{font-size:11px;color:#7a8b91;margin-top:12px;word-break:break-all}
      </style></head><body>
      <h1>材智通 · 材料批次标签</h1>
      <div class="code">${label.batchCode}</div>
      <img src="${dataUrl}" alt="批次二维码" />
      <div class="meta">${label.itemName ?? ''}</div>
      <div class="meta">货架位：${label.location ?? '-'} · 有效期：${label.expireDate ?? '-'}</div>
      <div class="tip">扫码直达领用页：${qrUrl}</div>
      </body></html>`);
    win.document.close();
    win.focus();
    window.setTimeout(() => win.print(), 400);
  }, [label, qrUrl]);

  const downloadQr = useCallback(() => {
    const canvas = document.getElementById('batch-qr-canvas') as HTMLCanvasElement | null;
    if (!canvas || !label) return;
    const link = document.createElement('a');
    link.href = canvas.toDataURL('image/png');
    link.download = `${label.batchCode}.png`;
    link.click();
  }, [label]);

  /** 真正的 Excel 批量入库：整份文件校验通过才写库，失败返回逐行原因。 */
  const handleImport = useCallback(async (file: File) => {
    setImporting(true);
    setImportResult(null);
    try {
      const result = await api.importBatches(file);
      setImportResult(result);
      if (result.errors.length > 0) {
        message.error(`导入未执行：${result.errors.length} 行校验失败，未写入任何数据`);
      } else {
        message.success(`批量导入成功：新增 ${result.imported} 个批次`);
        const [freshItems, freshBatches] = await Promise.all([api.materials(), api.batches()]);
        setMaterials(freshItems);
        setBatches(freshBatches);
      }
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setImporting(false);
    }
    return false;
  }, []);

  return (
    <div className="cz-page">
      <div className="cz-page-title">
        <div>
          <h2>入库管理</h2>
          <p>扫码/手动录入，自动生成批次号与真实可扫的电子标签二维码</p>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => void load()}>
          刷新
        </Button>
      </div>

      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="后端接口异常"
          description={`${error}（请确认 Spring Boot 已启动，且 .env 里的 VITE_API_BASE_URL 正确）`}
        />
      )}

      <Row gutter={16}>
        <Col xs={24} lg={14}>
          <Form
            form={form}
            layout="vertical"
            disabled={!canWriteStock}
            initialValues={{ qty: 10 }}
            onFinish={async (values: {
              itemId: number;
              qty: number;
              location: string;
              expireDate?: string;
              supplier?: string;
              barcode?: string;
              remark?: string;
            }) => {
              setSubmitting(true);
              try {
                const result = await api.inbound({
                  itemId: values.itemId,
                  qty: values.qty,
                  location: values.location,
                  expireDate: values.expireDate || undefined,
                  supplier: values.supplier,
                  barcode: values.barcode,
                  remark: values.remark,
                });
                const info: LabelInfo = {
                  batchCode: result.batch.batchCode,
                  barcode: result.batch.barcode,
                  itemName:
                    result.batch.itemName ??
                    materials.find((m) => m.id === values.itemId)?.model ??
                    '',
                  location: result.batch.location,
                  expireDate: result.batch.expireDate,
                  quantity: Number(result.batch.quantity),
                };
                setLabel(info);
                message.success(`入库成功，批次号 ${info.batchCode}`);
                form.resetFields(['qty', 'barcode', 'remark']);
                const fresh = await api.batches();
                setBatches(fresh);
              } catch (e) {
                message.error((e as Error).message);
              } finally {
                setSubmitting(false);
              }
            }}
          >
            <Form.Item label="材料" name="itemId" rules={[{ required: true, message: '请选择材料' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                placeholder={materials.length ? '选择材料' : '材料列表为空，请检查后端'}
                options={materials.map((m) => ({
                  value: m.id,
                  label: `${m.brand ?? ''} ${m.model ?? ''} · ${m.spec ?? ''}`,
                }))}
              />
            </Form.Item>
            <Space size="large" wrap>
              <Form.Item label="入库数量" name="qty" rules={[{ required: true, message: '请填写数量' }]}>
                <InputNumber min={0.001} step={1} style={{ width: 140 }} />
              </Form.Item>
              <Form.Item label="货架位" name="location" rules={[{ required: true, message: '请填写货架位' }]}>
                <Input placeholder="如 A-01-03" style={{ width: 160 }} />
              </Form.Item>
              <Form.Item label="有效期" name="expireDate" rules={[{ required: true, message: '请选择有效期' }]}>
                <Input type="date" style={{ width: 180 }} />
              </Form.Item>
            </Space>
            <Form.Item label="供应商" name="supplier">
              <Input placeholder="如 国药集团" style={{ width: 220 }} />
            </Form.Item>
            <Form.Item label="条码（可扫码枪填入）" name="barcode">
              <Input prefix={<QrcodeOutlined />} placeholder="扫码或留空自动生成" />
            </Form.Item>
            <Form.Item label="备注" name="remark">
              <Input placeholder="学期初补货" />
            </Form.Item>
            <Form.Item
              label="批量导入（Excel）"
              extra="表头需包含「材料代码」「数量」；可选：货架位、有效期(yyyy-MM-dd)、供应商、条码、备注。整份文件全部合法才会写入。"
            >
              <Space wrap>
                <Upload
                  accept=".xlsx,.xls"
                  showUploadList={false}
                  disabled={!canWriteStock}
                  beforeUpload={(file) => {
                    void handleImport(file as File);
                    return false;
                  }}
                >
                  <Button icon={<UploadOutlined />} loading={importing} disabled={!canWriteStock}>
                    选择文件并导入
                  </Button>
                </Upload>
                <Button
                  type="link"
                  href={`${import.meta.env.BASE_URL}templates/materials-inbound-template.xlsx`}
                  download
                >
                  下载模板
                </Button>
              </Space>
            </Form.Item>

            {importResult && importResult.errors.length > 0 && (
              <Alert
                type="error"
                showIcon
                style={{ marginBottom: 16 }}
                message={`导入未执行：${importResult.errors.length} 行校验失败，未写入任何数据`}
                description={
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {importResult.errors.map((e) => (
                      <li key={e.row}>
                        第 {e.row} 行：{e.message}
                      </li>
                    ))}
                  </ul>
                }
              />
            )}

            {importResult && importResult.errors.length === 0 && (
              <Alert
                type="success"
                showIcon
                style={{ marginBottom: 16 }}
                message={`批量导入成功：${importResult.imported} 个批次`}
                description={
                  <Space wrap size={[8, 4]}>
                    {importResult.batches.map((b) => (
                      <Tag key={b.batchCode ?? b.row} color="cyan">
                        第{b.row}行 {b.itemCode} → {b.batchCode ?? '(无批次号)'} × {b.qty}
                      </Tag>
                    ))}
                  </Space>
                }
              />
            )}

            <Button type="primary" htmlType="submit" loading={submitting} disabled={!canWriteStock}>
              确认入库并打印标签
            </Button>
          </Form>
        </Col>

        <Col xs={24} lg={10}>
          <div className="cz-panel">
            <h3>电子标签预览（真实二维码）</h3>
            {label ? (
              <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                <div style={{ background: '#fff', padding: 10, borderRadius: 12, border: '1px solid #e3ecee' }}>
                  <QRCodeCanvas
                    id="batch-qr-canvas"
                    value={qrUrl}
                    size={200}
                    level="M"
                    includeMargin
                    bgColor="#ffffff"
                    fgColor="#0b3d4a"
                  />
                </div>
                <div style={{ flex: 1, minWidth: 220 }}>
                  <div style={{ fontSize: 12, color: '#667780' }}>批次号</div>
                  <strong style={{ fontSize: 16 }}>{label.batchCode}</strong>
                  <div style={{ fontSize: 12, color: '#667780', marginTop: 8 }}>二维码内容</div>
                  <code style={{ fontSize: 12, wordBreak: 'break-all', color: '#156677' }}>{qrUrl}</code>
                  <div style={{ marginTop: 12 }}>
                    <Space wrap>
                      <Button type="primary" icon={<PrinterOutlined />} onClick={printLabel}>
                        打印标签
                      </Button>
                      <Button onClick={downloadQr}>下载 PNG</Button>
                    </Space>
                  </div>
                  <p style={{ color: '#667780', marginTop: 10, fontSize: 12 }}>
                    手机扫码 → 登录页 → 自动跳转领用页并选中该批次。
                  </p>
                </div>
              </div>
            ) : (
              <Alert type="info" showIcon message="入库成功后会在这里生成可扫码的批次二维码" />
            )}
          </div>
        </Col>
      </Row>

      <div style={{ marginTop: 20 }}>
        <h3 style={{ color: '#0b3d4a' }}>近期入库批次</h3>
        <Table
          rowKey="id"
          size="middle"
          loading={loading}
          dataSource={batches}
          pagination={{ pageSize: 8 }}
          onRow={(record) => ({
            onClick: () => setLabel(toLabel(record)),
            style: { cursor: 'pointer' },
          })}
          columns={[
            { title: '批次号', dataIndex: 'batchCode' },
            { title: '材料', dataIndex: 'itemName' },
            { title: '数量', dataIndex: 'quantity', width: 80 },
            {
              title: '库存状态',
              width: 110,
              render: (_: unknown, r: StockBatch) => {
                const low = r.safeStock != null && Number(r.quantity) < Number(r.safeStock);
                return <Tag color={low ? 'error' : 'success'}>{low ? '低于安全' : '正常'}</Tag>;
              },
            },
            { title: '条码', dataIndex: 'barcode' },
            { title: '货架', dataIndex: 'location', width: 100 },
            { title: '有效期', dataIndex: 'expireDate', width: 120 },
          ]}
        />
      </div>
    </div>
  );
}

function toLabel(batch: StockBatch): LabelInfo {
  return {
    batchCode: batch.batchCode,
    barcode: batch.barcode,
    itemName: batch.itemName ?? '',
    location: batch.location,
    expireDate: batch.expireDate,
    quantity: Number(batch.quantity),
  };
}
