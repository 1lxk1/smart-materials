import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, DatePicker, Form, Select, Space, Table, message } from 'antd';
import type { Dayjs } from 'dayjs';
import { FileExcelOutlined, FilePdfOutlined, ReloadOutlined, DownloadOutlined } from '@ant-design/icons';
import { api } from '../api';
import { saveBlob } from '../api/http';
import { useAuth } from '../auth';
import type { ReportRow } from '../types';

interface ReportFilter {
  type: 'OUT' | 'RETURN' | 'SCRAP' | 'IN';
  projectNo?: string;
  range?: [Dayjs | null, Dayjs | null] | null;
}

/**
 * 统计与导出。
 *
 * <p>导出不再是前端拼 CSV / window.print()：按钮直接调用后端
 * {@code GET /api/reports/export}，由 Apache POI 生成 .xlsx、
 * OpenPDF 生成带中文字体的 .pdf，前端拿 Blob 触发下载。</p>
 */
export default function ReportsPage() {
  const { canExport } = useAuth();
  const [form] = Form.useForm<ReportFilter>();
  const [rows, setRows] = useState<ReportRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState<null | 'xlsx' | 'pdf'>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (projectNo?: string) => {
    setLoading(true);
    try {
      setRows(await api.reportRows('OUT', projectNo));
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

  const doExport = useCallback(
    async (format: 'xlsx' | 'pdf') => {
      if (!canExport) {
        message.warning('当前角色没有导出权限');
        return;
      }
      const values = form.getFieldsValue();
      const range = values.range;
      setExporting(format);
      try {
        const { blob, fileName } = await api.exportReport({
          format,
          type: values.type ?? 'OUT',
          projectNo: values.projectNo,
          from: range?.[0] ? range[0].format('YYYY-MM-DD') : undefined,
          to: range?.[1] ? range[1].format('YYYY-MM-DD') : undefined,
        });
        saveBlob(blob, fileName);
        message.success(`已下载 ${fileName}（${(blob.size / 1024).toFixed(1)} KB）`);
      } catch (e) {
        message.error((e as Error).message);
      } finally {
        setExporting(null);
      }
    },
    [canExport, form],
  );

  return (
    <div className="cz-page">
      <div className="cz-page-title">
        <div>
          <h2>统计与导出</h2>
          <p>一键生成学期耗材汇总、经费对应表、个人领用明细（真实 Excel / PDF）</p>
        </div>
      </div>

      <Form
        form={form}
        layout="inline"
        style={{ marginBottom: 16 }}
        initialValues={{ type: 'OUT' }}
        onValuesChange={(changed: Partial<ReportFilter>) => {
          if ('projectNo' in changed) {
            void load(changed.projectNo);
          }
        }}
      >
        <Form.Item label="类型" name="type">
          <Select
            style={{ width: 140 }}
            options={[
              { value: 'OUT', label: '领用出库' },
              { value: 'RETURN', label: '归还' },
              { value: 'SCRAP', label: '报废' },
            ]}
          />
        </Form.Item>
        <Form.Item label="项目号" name="projectNo">
          <Select
            allowClear
            style={{ width: 200 }}
            placeholder="全部项目"
            options={[
              { value: 'PRJ-2025-012', label: 'PRJ-2025-012' },
              { value: 'PRJ-2025-018', label: 'PRJ-2025-018' },
            ]}
          />
        </Form.Item>
        <Form.Item label="区间" name="range">
          <DatePicker.RangePicker />
        </Form.Item>
        <Form.Item>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
        </Form.Item>
      </Form>

      {error && <Alert type="error" showIcon message="报表加载失败" description={error} style={{ marginBottom: 12 }} />}

      <Space style={{ marginBottom: 16 }} wrap>
        <Button
          type="primary"
          icon={<FileExcelOutlined />}
          disabled={!canExport}
          loading={exporting === 'xlsx'}
          onClick={() => void doExport('xlsx')}
        >
          导出经费对应表（Excel）
        </Button>
        <Button
          icon={<FileExcelOutlined />}
          disabled={!canExport}
          loading={exporting === 'xlsx'}
          onClick={() => void doExport('xlsx')}
        >
          学期耗材汇总（Excel）
        </Button>
        <Button
          icon={<FilePdfOutlined />}
          disabled={!canExport}
          loading={exporting === 'pdf'}
          onClick={() => void doExport('pdf')}
        >
          个人领用明细（PDF）
        </Button>
      </Space>

      <Alert
        type="info"
        showIcon
        icon={<DownloadOutlined />}
        style={{ marginBottom: 16 }}
        message="导出说明"
        description="Excel 与 PDF 均由后端生成（Apache POI / OpenPDF，内置中文字体），下载的是真实文件；项目号与日期区间会作为查询条件一起下发给后端。"
      />

      <Table
        rowKey="id"
        loading={loading}
        dataSource={rows}
        locale={{ emptyText: '暂无领用流水' }}
        columns={[
          { title: '材料代码', dataIndex: 'code' },
          { title: '材料名称', dataIndex: 'name' },
          { title: '单价', dataIndex: 'price', width: 90, render: (v: number) => Number(v ?? 0).toFixed(2) },
          { title: '数量', dataIndex: 'qty', width: 80 },
          { title: '总价', dataIndex: 'amount', width: 100, render: (v: number) => Number(v ?? 0).toFixed(2) },
          { title: '使用人', dataIndex: 'user', width: 100 },
          { title: '项目号', dataIndex: 'projectNo', width: 140 },
          { title: '用途', dataIndex: 'usage' },
        ]}
      />
    </div>
  );
}
