package com.smartmaterials.vo;

import com.smartmaterials.entity.StockAlert;
import com.smartmaterials.entity.StockTransaction;

import java.util.List;
import java.util.Map;

/** 仪表盘数据。 */
public record DashboardVO(Map<String, Object> stats,
                          List<Map<String, Object>> topConsumables,
                          List<StockTransaction> recentTransactions,
                          List<StockAlert> alerts) {
}
