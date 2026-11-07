package com.smartmaterials.controller;

import com.smartmaterials.service.DashboardService;
import com.smartmaterials.vo.DashboardVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 运营仪表盘。 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardVO overview() {
        return dashboardService.overview();
    }
}
