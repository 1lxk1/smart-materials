package com.smartmaterials.controller;

import com.smartmaterials.dto.OutboundRequest;
import com.smartmaterials.dto.ReturnScrapRequest;
import com.smartmaterials.entity.SysUser;
import com.smartmaterials.service.StockService;
import com.smartmaterials.vo.OperationResult;
import com.smartmaterials.vo.QuotaVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 库存核心动作：领用出库 / 归还 / 报废 / 额度。 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /** 领用出库：库存校验 → 学生月度限额校验 → 扣库存 → 写流水 → 安全库存预警。 */
    @PostMapping("/outbound")
    @PreAuthorize("hasAnyRole('STUDENT', 'LAB', 'TEACHER', 'BUYER', 'ADMIN')")
    public OperationResult outbound(@Valid @RequestBody OutboundRequest request,
                                    @AuthenticationPrincipal SysUser operator) {
        return stockService.outbound(request, operator);
    }

    /** 归还（RETURN）或报废（SCRAP）。 */
    @PostMapping("/return-scrap")
    @PreAuthorize("hasAnyRole('STUDENT', 'LAB', 'TEACHER', 'BUYER', 'ADMIN')")
    public OperationResult returnOrScrap(@Valid @RequestBody ReturnScrapRequest request,
                                         @AuthenticationPrincipal SysUser operator) {
        return stockService.returnOrScrap(request, operator);
    }

    /** 当前用户本月领用额度。 */
    @GetMapping("/quota")
    public QuotaVO quota(@AuthenticationPrincipal SysUser user) {
        return stockService.quota(user);
    }
}
