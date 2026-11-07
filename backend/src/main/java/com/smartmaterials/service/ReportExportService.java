package com.smartmaterials.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smartmaterials.common.BizException;
import com.smartmaterials.support.AppTime;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 报表导出：真正生成 .xlsx（Apache POI）与 .pdf（OpenPDF）二进制。
 *
 * <p>PDF 使用 OpenPDF 自带的 CJK 字体 STSong-Light + UniGB-UCS2-H，
 * 不依赖操作系统字体，Linux 容器内也能正确输出中文。</p>
 */
@Service
public class ReportExportService {

    private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter HUMAN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String[] HEADERS = {
            "材料代码", "材料名称", "单价", "数量", "总价", "使用人", "项目号", "用途", "时间"
    };
    private static final String[] KEYS = {
            "code", "name", "price", "qty", "amount", "user", "projectNo", "usage", "createTime"
    };

    private final ReportService reportService;

    public ReportExportService(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 生成 Excel 文件字节。 */
    public byte[] toExcel(String txnType, String projectNo, LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = reportService.exportRows(txnType, projectNo, from, to);
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("报表");
            CellStyle header = headerStyle(workbook);
            CellStyle money = moneyStyle(workbook);
            CellStyle text = textStyle(workbook);

            Row head = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                org.apache.poi.ss.usermodel.Cell cell = head.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(header);
            }

            int rowIndex = 1;
            for (Map<String, Object> row : rows) {
                Row line = sheet.createRow(rowIndex++);
                for (int c = 0; c < KEYS.length; c++) {
                    org.apache.poi.ss.usermodel.Cell cell = line.createCell(c);
                    Object value = row.get(KEYS[c]);
                    if ("price".equals(KEYS[c]) || "amount".equals(KEYS[c])) {
                        cell.setCellValue(value == null ? 0d : new BigDecimal(value.toString()).doubleValue());
                        cell.setCellStyle(money);
                    } else if ("qty".equals(KEYS[c])) {
                        cell.setCellValue(value == null ? 0d : new BigDecimal(value.toString()).doubleValue());
                        cell.setCellStyle(text);
                    } else {
                        cell.setCellValue(value == null ? "" : value.toString());
                        cell.setCellStyle(text);
                    }
                }
            }

            Row total = sheet.createRow(rowIndex);
            org.apache.poi.ss.usermodel.Cell label = total.createCell(0);
            label.setCellValue("合计");
            label.setCellStyle(header);
            org.apache.poi.ss.usermodel.Cell amount = total.createCell(4);
            amount.setCellValue(rows.stream()
                    .map(r -> r.get("amount") == null ? BigDecimal.ZERO : new BigDecimal(r.get("amount").toString()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .doubleValue());
            amount.setCellStyle(money);

            int[] widths = {14, 30, 10, 8, 11, 10, 14, 22, 20};
            for (int c = 0; c < widths.length; c++) {
                sheet.setColumnWidth(c, widths[c] * 256);
            }
            sheet.createFreezePane(0, 1);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("生成 Excel 失败", e);
            throw new BizException(500, "生成 Excel 失败：" + e.getMessage());
        }
    }

    /** 生成 PDF 文件字节。 */
    public byte[] toPdf(String txnType, String projectNo, LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = reportService.exportRows(txnType, projectNo, from, to);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = cjkFont(16, Font.BOLD);
            Font metaFont = cjkFont(9, Font.NORMAL);
            Font headFont = cjkFont(8, Font.BOLD, Color.WHITE);
            Font bodyFont = cjkFont(8, Font.NORMAL);

            Paragraph title = new Paragraph("耗材领用与经费对应表", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            String range = (from == null ? "不限" : from.toString()) + " ~ " + (to == null ? "不限" : to.toString());
            Paragraph meta = new Paragraph(
                    "类型：" + txnType + "    项目号：" + (StringUtils.hasText(projectNo) ? projectNo : "全部")
                            + "    区间：" + range
                            + "    导出时间：" + LocalDateTime.now(AppTime.ZONE).format(HUMAN), metaFont);
            meta.setSpacingBefore(6);
            meta.setSpacingAfter(10);
            document.add(meta);

            PdfPTable table = new PdfPTable(HEADERS.length);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2.1f, 4.2f, 1.5f, 1.2f, 1.7f, 1.6f, 2.0f, 3.2f, 2.6f});
            table.setHeaderRows(1);
            for (String name : HEADERS) {
                PdfPCell cell = new PdfPCell(new Phrase(name, headFont));
                cell.setBackgroundColor(new Color(21, 102, 119));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(5);
                table.addCell(cell);
            }

            for (Map<String, Object> row : rows) {
                addCell(table, text(row.get("code")), bodyFont, Element.ALIGN_LEFT);
                addCell(table, text(row.get("name")), bodyFont, Element.ALIGN_LEFT);
                addCell(table, money(row.get("price")), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, number(row.get("qty")), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, money(row.get("amount")), bodyFont, Element.ALIGN_RIGHT);
                addCell(table, text(row.get("user")), bodyFont, Element.ALIGN_LEFT);
                addCell(table, text(row.get("projectNo")), bodyFont, Element.ALIGN_LEFT);
                addCell(table, text(row.get("usage")), bodyFont, Element.ALIGN_LEFT);
                addCell(table, text(row.get("createTime")), bodyFont, Element.ALIGN_LEFT);
            }
            if (rows.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("暂无数据", bodyFont));
                empty.setColspan(HEADERS.length);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                empty.setPadding(8);
                table.addCell(empty);
            }
            document.add(table);

            BigDecimal total = rows.stream()
                    .map(r -> r.get("amount") == null ? BigDecimal.ZERO : new BigDecimal(r.get("amount").toString()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Paragraph summary = new Paragraph(
                    "共 " + rows.size() + " 条，合计金额 ¥" + total.setScale(2, java.math.RoundingMode.HALF_UP)
                            + "    制表：智慧材料库", metaFont);
            summary.setSpacingBefore(10);
            document.add(summary);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("生成 PDF 失败", e);
            throw new BizException(500, "生成 PDF 失败：" + e.getMessage());
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    /** 导出文件名：中文名 + 时间戳，前端据此保存。 */
    public String fileName(String base, String ext) {
        return base + "_" + LocalDateTime.now(AppTime.ZONE).format(STAMP) + "." + ext;
    }

    // ------------------------------------------------------------------ 样式

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle moneyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private CellStyle textStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private Font cjkFont(int size, int style) {
        return cjkFont(size, style, null);
    }

    private Font cjkFont(int size, int style, Color color) {
        Font font;
        try {
            BaseFont base = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            font = new Font(base, size, style);
        } catch (Exception e) {
            // 万一下发环境缺少 CJK cmap，退化成内置字体，保证接口仍能返回 PDF
            log.warn("中文字体加载失败，PDF 中文可能显示为空白：{}", e.getMessage());
            font = new Font(Font.HELVETICA, size, style);
        }
        if (color != null) {
            font.setColor(color);
        }
        return font;
    }

    private void addCell(PdfPTable table, String value, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(4);
        cell.setBorderColor(new Color(200, 210, 214));
        table.addCell(cell);
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String money(Object value) {
        if (value == null) {
            return "0.00";
        }
        return new BigDecimal(value.toString()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String number(Object value) {
        if (value == null) {
            return "0";
        }
        return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
    }
}
