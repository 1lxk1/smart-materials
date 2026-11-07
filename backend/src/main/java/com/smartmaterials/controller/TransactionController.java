package com.smartmaterials.controller;

import com.smartmaterials.entity.StockTransaction;
import com.smartmaterials.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 流水查询。 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final ReportService reportService;

    public TransactionController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public List<StockTransaction> list(@RequestParam(required = false) String type,
                                       @RequestParam(defaultValue = "50") int limit) {
        return reportService.transactions(type, limit);
    }
}
