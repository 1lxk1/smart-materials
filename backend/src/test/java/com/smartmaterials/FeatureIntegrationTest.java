package com.smartmaterials;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmaterials.support.IntegrationTestBase;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1-3 功能补全的集成测试：Excel 批量导入、报表导出（xlsx/pdf）、附件上传。
 */
class FeatureIntegrationTest extends IntegrationTestBase {

    // ------------------------------------------------------------------ 批量导入

    @Test
    @DisplayName("Excel 批量导入：合法文件全量入库，返回新批次号")
    void importExcelSuccess() throws Exception {
        long[] ids = createItemAndBatch("IT-IMPORT", 0, 0);
        String code = jdbc.queryForObject(
                "SELECT code FROM material_item WHERE id = ?", String.class, ids[0]);
        String token = login("admin", "123456");

        byte[] xlsx = buildWorkbook(List.of(
                new String[]{code, "7", "T-09", "2030-12-31", "联调供应商"},
                new String[]{code, "5", "T-10", "2030-12-31", "联调供应商"}
        ));

        ResponseEntity<String> response =
                postMultipart("/api/batches/import", "file", "import.xlsx", xlsx, token, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json(response.getBody());
        assertThat(body.path("total").asInt()).isEqualTo(2);
        assertThat(body.path("imported").asInt()).isEqualTo(2);
        assertThat(body.path("errors")).isEmpty();
        assertThat(body.path("batches").get(0).path("batchCode").asText()).startsWith("IN");

        Integer batches = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_batch WHERE item_id = ?", Integer.class, ids[0]);
        assertThat(batches).isEqualTo(3); // 1 个初始 + 2 个导入
        assertThat(itemStock(ids[0])).isEqualTo(12.0);
    }

    @Test
    @DisplayName("Excel 批量导入：存在非法行时整体不写库，返回逐行错误")
    void importExcelRejectsWholeFileOnInvalidRow() throws Exception {
        long[] ids = createItemAndBatch("IT-IMPORT-BAD", 0, 0);
        String code = jdbc.queryForObject(
                "SELECT code FROM material_item WHERE id = ?", String.class, ids[0]);
        String token = login("admin", "123456");

        byte[] xlsx = buildWorkbook(List.of(
                new String[]{code, "3", "T-01", "2030-12-31", "合法行"},
                new String[]{"NOT-EXIST-" + System.nanoTime(), "3", "T-02", "2030-12-31", "非法行"},
                new String[]{code, "0", "T-03", "2030-12-31", "数量非法"}
        ));

        ResponseEntity<String> response =
                postMultipart("/api/batches/import", "file", "bad.xlsx", xlsx, token, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json(response.getBody());
        assertThat(body.path("imported").asInt()).isZero();
        assertThat(body.path("errors").size()).isEqualTo(2);

        Integer batches = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_batch WHERE item_id = ?", Integer.class, ids[0]);
        assertThat(batches).isEqualTo(1); // 只有初始批次，非法文件一个都没写
        assertThat(itemStock(ids[0])).isZero();
    }

    @Test
    @DisplayName("非 Excel 文件被拒绝：400")
    void importRejectsNonExcel() {
        String token = login("admin", "123456");
        ResponseEntity<String> response = postMultipart("/api/batches/import", "file", "note.txt",
                "hello".getBytes(StandardCharsets.UTF_8), token, null);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response.getBody()).path("message").asText()).contains("xlsx");
    }

    // ------------------------------------------------------------------ 报表导出

    @Test
    @DisplayName("报表导出：xlsx 是真实 zip 工作簿，pdf 是真实 PDF 且含中文")
    void exportReportGeneratesRealFiles() {
        String token = login("admin", "123456");

        ResponseEntity<byte[]> xlsx =
                getBytes("/api/reports/export?format=xlsx&type=OUT", token);
        assertThat(xlsx.getStatusCode().value()).isEqualTo(200);
        assertThat(xlsx.getHeaders().getContentType().toString()).contains("spreadsheetml");
        assertThat(xlsx.getBody()).isNotNull();
        assertThat(xlsx.getBody().length).isGreaterThan(1000);
        assertThat(new String(xlsx.getBody(), 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");

        ResponseEntity<byte[]> pdf =
                getBytes("/api/reports/export?format=pdf&type=OUT", token);
        assertThat(pdf.getStatusCode().value()).isEqualTo(200);
        assertThat(pdf.getHeaders().getContentType().toString()).contains("application/pdf");
        assertThat(pdf.getBody()).isNotNull();
        assertThat(new String(pdf.getBody(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    // ------------------------------------------------------------------ 附件

    @Test
    @DisplayName("附件上传：落盘 + 返回 /uploads 地址 + 可静态访问，并写进流水 attachments")
    void uploadAttachmentAndAttachToTransaction() {
        long[] ids = createItemAndBatch("IT-ATTACH", 0, 20);
        String token = login("admin", "123456");

        // 1x1 PNG
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

        ResponseEntity<String> upload = postMultipart("/api/attachments", "file", "scrap.png",
                png, token, Map.of("category", "scrap"));

        assertThat(upload.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json(upload.getBody());
        String url = body.path("url").asText();
        assertThat(url).startsWith("/uploads/scrap/");
        assertThat(body.path("size").asLong()).isEqualTo(png.length);

        // 静态资源可访问
        ResponseEntity<byte[]> fetched = getBytes(url, null);
        assertThat(fetched.getStatusCode().value()).isEqualTo(200);
        assertThat(fetched.getBody()).hasSize(png.length);

        // 写进 stock_transaction.attachments（jsonb 数组）
        ResponseEntity<String> scrap = postJson("/api/stock/return-scrap", Map.of(
                "type", "SCRAP",
                "batchId", ids[1],
                "qty", 1,
                "usage", "破损报废（带队照片）",
                "projectNo", "PRJ-IT-009",
                "photos", List.of(url)
        ), token);
        assertThat(scrap.getStatusCode().value()).isEqualTo(200);

        String attachments = jdbc.queryForObject(
                "SELECT attachments::text FROM stock_transaction WHERE batch_id = ? ORDER BY id DESC LIMIT 1",
                String.class, ids[1]);
        assertThat(attachments).contains(url);
    }

    // ------------------------------------------------------------------ 辅助

    private byte[] buildWorkbook(List<String[]> dataRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("入库单");
            String[] headers = {"材料代码", "数量", "货架位", "有效期", "供应商"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int rowIndex = 1;
            for (String[] values : dataRows) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < values.length; i++) {
                    row.createCell(i).setCellValue(values[i]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
