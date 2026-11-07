package com.smartmaterials.controller;

import com.smartmaterials.dto.InboundRequest;
import com.smartmaterials.entity.StockBatch;
import com.smartmaterials.entity.SysUser;
import com.smartmaterials.service.BatchImportService;
import com.smartmaterials.service.BatchService;
import com.smartmaterials.service.StockService;
import com.smartmaterials.vo.ImportResult;
import com.smartmaterials.vo.OperationResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 库存批次 + 入库。 */
@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;
    private final StockService stockService;
    private final BatchImportService batchImportService;

    public BatchController(BatchService batchService,
                           StockService stockService,
                           BatchImportService batchImportService) {
        this.batchService = batchService;
        this.stockService = stockService;
        this.batchImportService = batchImportService;
    }

    @GetMapping
    public List<StockBatch> list(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) Long itemId) {
        return batchService.list(keyword, itemId);
    }

    @GetMapping("/{batchCode}")
    public StockBatch byBatchCode(@PathVariable String batchCode) {
        return batchService.getByBatchCode(batchCode);
    }

    @GetMapping("/barcode/{barcode}")
    public StockBatch byBarcode(@PathVariable String barcode) {
        return batchService.getByBarcode(barcode);
    }

    /** 入库：自动生成批次号与条码，返回可直接打印二维码的批次信息。 */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB', 'BUYER')")
    public OperationResult inbound(@Valid @RequestBody InboundRequest request,
                                   @AuthenticationPrincipal SysUser operator) {
        return stockService.inbound(request, operator);
    }

    /**
     * 批量导入入库（Excel）。
     *
     * <p>表格第 1 行是表头，必需列：材料代码、数量；可选列：货架位、有效期（yyyy-MM-dd）、
     * 供应商、条码、备注。整份文件全成功才写库，否则返回逐行错误且不写入任何数据。</p>
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB', 'BUYER')")
    public ImportResult importExcel(@RequestParam("file") MultipartFile file,
                                    @AuthenticationPrincipal SysUser operator) {
        return batchImportService.importExcel(file, operator);
    }
}
