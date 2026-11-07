import { Alert, Button, Form, Input, InputNumber, Radio, Select, Space, Upload, message } from 'antd';
import type { UploadFile } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import { useCallback, useEffect, useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import type { StockBatch } from '../types';

/**
 * 归还（库存回补） / 报废（库存扣减），都写入 stock_transaction。
 *
 * <p>损耗照片是真的上传：选中即调用 POST /api/attachments 落盘，
 * 返回的 /uploads/... 地址随 return-scrap 一起提交，
 * 后端写进 stock_transaction.attachments 这个 jsonb 数组。</p>
 */
export default function ReturnScrapPage() {
  const { canWriteStock } = useAuth();
  const [form] = Form.useForm();
  const [batches, setBatches] = useState<StockBatch[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setBatches(await api.batches());
    } catch (e) {
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
          <h2>归还 / 报废</h2>
          <p>支持部分归还；报废可直接扣减批次库存并记录损耗说明与照片</p>
        </div>
      </div>

      <Form
        form={form}
        layout="vertical"
        style={{ maxWidth: 640 }}
        disabled={!canWriteStock}
        initialValues={{ type: 'RETURN', qty: 1 }}
        onFinish={async (values: {
          type: 'RETURN' | 'SCRAP';
          batchId: number;
          qty: number;
          usage: string;
          projectNo?: string;
        }) => {
          setSubmitting(true);
          try {
            const photos = fileList
              .map((f) => f.url)
              .filter((url): url is string => typeof url === 'string' && url.length > 0);
            const result = await api.returnScrap({
              type: values.type,
              batchId: values.batchId,
              qty: values.qty,
              usage: values.usage,
              projectNo: values.projectNo,
              photos,
            });
            message.success(
              values.type === 'RETURN'
                ? `归还成功，批次剩余 ${result.batchQuantity}`
                : `报废成功，批次剩余 ${result.batchQuantity}`,
            );
            form.resetFields(['qty', 'usage']);
            setFileList([]);
            await load();
          } catch (e) {
            message.error((e as Error).message);
          } finally {
            setSubmitting(false);
          }
        }}
      >
        <Form.Item label="业务类型" name="type">
          <Radio.Group
            options={[
              { label: '归还 RETURN（库存回补）', value: 'RETURN' },
              { label: '报废 SCRAP（库存扣减）', value: 'SCRAP' },
            ]}
          />
        </Form.Item>
        <Form.Item label="关联批次" name="batchId" rules={[{ required: true, message: '请选择批次' }]}>
          <Select
            loading={loading}
            showSearch
            optionFilterProp="label"
            options={batches.map((b) => ({
              value: b.id,
              label: `${b.itemName ?? ''} · ${b.batchCode}（剩余 ${b.quantity}）`,
            }))}
          />
        </Form.Item>
        <Form.Item label="数量" name="qty" rules={[{ required: true, message: '请填写数量' }]}>
          <InputNumber min={0.001} step={1} />
        </Form.Item>
        <Form.Item label="关联项目号（可选）" name="projectNo">
          <Input placeholder="PRJ-2025-012" />
        </Form.Item>
        <Form.Item label="说明" name="usage" rules={[{ required: true, message: '请填写说明' }]}>
          <Input.TextArea rows={3} placeholder="未开封归还 / 包装破损等" />
        </Form.Item>
        <Form.Item
          label="损耗照片"
          extra="选中即上传（≤10MB，支持 jpg/png/webp/pdf），最多 3 张；提交后写入该条流水的 attachments 字段"
        >
          <Upload
            listType="picture-card"
            accept="image/*,.pdf"
            maxCount={3}
            fileList={fileList}
            onChange={({ fileList: next }) => setFileList(next)}
            onRemove={({ uid }) => {
              setFileList((prev) => prev.filter((f) => f.uid !== uid));
            }}
            customRequest={(options) => {
              const file = options.file as File;
              const uid = (options.file as unknown as UploadFile).uid;
              api
                .uploadAttachment(file, 'scrap')
                .then((res) => {
                  setFileList((prev) =>
                    prev.map((f) =>
                      f.uid === uid
                        ? { ...f, status: 'done', url: res.url, thumbUrl: res.url, response: res }
                        : f,
                    ),
                  );
                  options.onSuccess?.(res);
                })
                .catch((e: Error) => {
                  message.error(`上传失败：${e.message}`);
                  setFileList((prev) => prev.filter((f) => f.uid !== uid));
                  options.onError?.(e);
                });
            }}
          >
            <div>
              <UploadOutlined />
              <div style={{ marginTop: 8 }}>上传</div>
            </div>
          </Upload>
        </Form.Item>
        <Space>
          <Button type="primary" htmlType="submit" loading={submitting} disabled={!canWriteStock}>
            提交
          </Button>
          {fileList.length > 0 && (
            <Button onClick={() => setFileList([])} disabled={submitting}>
              清空照片
            </Button>
          )}
        </Space>
      </Form>

      {batches.some((b) => b.safeStock != null && Number(b.quantity) < Number(b.safeStock)) && (
        <Alert
          style={{ marginTop: 20, maxWidth: 640 }}
          type="warning"
          showIcon
          message="有批次低于安全库存"
          description="报废会进一步降低总库存，后端会自动写入 stock_alert 预警记录；补货后自动关闭。"
        />
      )}
    </div>
  );
}
