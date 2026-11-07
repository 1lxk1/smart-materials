package com.smartmaterials.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartmaterials.entity.MaterialItem;
import com.smartmaterials.entity.StockTransaction;
import com.smartmaterials.mapper.MaterialItemMapper;
import com.smartmaterials.mapper.StockTransactionMapper;
import com.smartmaterials.support.AppTime;
import com.smartmaterials.vo.DashboardVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 运营仪表盘：库存健康度 + 月度 TOP10 + 最近事务 + 预警。 */
@Service
public class DashboardService {

    private final MaterialItemMapper itemMapper;
    private final StockTransactionMapper txnMapper;
    private final ReportService reportService;
    private final AlertService alertService;

    public DashboardService(MaterialItemMapper itemMapper,
                            StockTransactionMapper txnMapper,
                            ReportService reportService,
                            AlertService alertService) {
        this.itemMapper = itemMapper;
        this.txnMapper = txnMapper;
        this.reportService = reportService;
        this.alertService = alertService;
    }

    public DashboardVO overview() {
        List<MaterialItem> items = itemMapper.selectList(Wrappers.lambdaQuery(MaterialItem.class)
                .orderByAsc(MaterialItem::getId));

        int lowStock = 0;
        BigDecimal stockValue = BigDecimal.ZERO;
        for (MaterialItem item : items) {
            BigDecimal stock = item.getStockQty() == null ? BigDecimal.ZERO : item.getStockQty();
            BigDecimal safe = item.getSafeStock() == null ? BigDecimal.ZERO : item.getSafeStock();
            if (safe.signum() > 0 && stock.compareTo(safe) < 0) {
                lowStock++;
            }
            BigDecimal price = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            stockValue = stockValue.add(stock.multiply(price));
        }

        YearMonth month = YearMonth.now(AppTime.ZONE);
        LocalDateTime from = month.atDay(1).atStartOfDay();
        LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("skuCount", items.size());
        stats.put("monthOut", txnMapper.countOut(from, to));
        stats.put("monthIn", txnMapper.countByType("IN", from, to));
        stats.put("monthReturn", txnMapper.countByType("RETURN", from, to));
        stats.put("monthScrap", txnMapper.countByType("SCRAP", from, to));
        stats.put("lowStock", lowStock);
        stats.put("stockValue", stockValue.setScale(2, RoundingMode.HALF_UP));
        stats.put("month", month.toString());

        List<Map<String, Object>> top = txnMapper.topConsumables(from, to, 10);
        List<StockTransaction> recent = reportService.transactions(null, 5);

        return new DashboardVO(stats, top, recent, alertService.listOpen());
    }
}
