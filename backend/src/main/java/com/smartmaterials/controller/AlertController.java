package com.smartmaterials.controller;

import com.smartmaterials.entity.StockAlert;
import com.smartmaterials.service.AlertService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 库存预警。 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /** 未关闭的预警（低于安全库存）。 */
    @GetMapping
    public List<StockAlert> open() {
        return alertService.listOpen();
    }

    /** 全部预警记录（含已恢复）。 */
    @GetMapping("/history")
    public List<StockAlert> history() {
        return alertService.listAll();
    }

    /** 标记已通知采购员（邮件 / 企业微信由外部集成消费此记录）。 */
    @PostMapping("/{id}/notify")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUYER', 'LAB')")
    public StockAlert notifyBuyer(@PathVariable Long id) {
        return alertService.markNotified(id);
    }
}
