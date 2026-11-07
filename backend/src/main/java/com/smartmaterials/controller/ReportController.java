package com.smartmaterials.controller;

import com.smartmaterials.service.ReportExportService;
import com.smartmaterials.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 统计与导出数据接口。 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService exportService;

    public ReportController(ReportService reportService, ReportExportService exportService) {
        this.reportService = reportService;
        this.exportService = exportService;
    }

    /** 导出明细行（前端表格展示用）。 */
    @GetMapping("/transactions")
    public List<Map<String, Object>> rows(@RequestParam(defaultValue = "OUT") String type,
                                          @RequestParam(required = false) String projectNo) {
        return reportService.exportRows(type, projectNo);
    }

    /** 按项目号汇总（本月）。 */
    @GetMapping("/projects")
    public List<Map<String, Object>> projects() {
        return reportService.projectSummary();
    }

    /**
     * 生成真正的 Excel / PDF 文件并下载。
     *
     * @param format    xlsx（默认）或 pdf
     * @param type      流水类型 OUT / RETURN / SCRAP / IN
     * @param projectNo 项目号过滤，可空
     * @param from      起始日期（含），可空
     * @param to        结束日期（含），可空
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('STUDENT', 'LAB', 'TEACHER', 'BUYER', 'ADMIN')")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(defaultValue = "OUT") String type,
            @RequestParam(required = false) String projectNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        boolean pdf = "pdf".equalsIgnoreCase(format);
        String ext = pdf ? "pdf" : "xlsx";
        byte[] bytes = pdf
                ? exportService.toPdf(type, projectNo, from, to)
                : exportService.toExcel(type, projectNo, from, to);

        String fileName = exportService.fileName("经费对应表", ext);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(pdf
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + "report." + ext + "\"; filename*=UTF-8''" + encoded);
        headers.setContentLength(bytes.length);
        headers.setAccessControlExposeHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
