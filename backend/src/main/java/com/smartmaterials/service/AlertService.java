package com.smartmaterials.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartmaterials.entity.MaterialItem;
import com.smartmaterials.entity.StockAlert;
import com.smartmaterials.mapper.StockAlertMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全库存预警：库存低于 safe_stock 时写入 stock_alert，恢复后自动关单。
 */
@Service
public class AlertService {

    private final StockAlertMapper alertMapper;

    public AlertService(StockAlertMapper alertMapper) {
        this.alertMapper = alertMapper;
    }

    public List<StockAlert> listOpen() {
        LambdaQueryWrapper<StockAlert> query = Wrappers.lambdaQuery(StockAlert.class)
                .eq(StockAlert::getStatus, "OPEN")
                .orderByDesc(StockAlert::getCreateTime)
                .last("LIMIT 50");
        return alertMapper.selectList(query);
    }

    public List<StockAlert> listAll() {
        return alertMapper.selectList(Wrappers.lambdaQuery(StockAlert.class)
                .orderByDesc(StockAlert::getCreateTime)
                .last("LIMIT 200"));
    }

    /**
     * 库存变动后刷新预警状态。
     *
     * @return 新建或更新的预警；库存正常时返回 null（并关闭历史未处理预警）
     */
    @Transactional(rollbackFor = Exception.class)
    public StockAlert refresh(MaterialItem item, Long batchId, Long sourceTxnId) {
        BigDecimal stock = item.getStockQty() == null ? BigDecimal.ZERO : item.getStockQty();
        BigDecimal safe = item.getSafeStock() == null ? BigDecimal.ZERO : item.getSafeStock();

        StockAlert open = findOpen(item.getId());

        if (safe.signum() <= 0 || stock.compareTo(safe) >= 0) {
            if (open != null) {
                open.setStatus("CLOSED");
                open.setClosedAt(LocalDateTime.now());
                open.setCurrentQty(stock);
                alertMapper.updateById(open);
            }
            return null;
        }

        String level = stock.compareTo(safe.multiply(new BigDecimal("0.5"))) <= 0 ? "danger" : "warning";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemCode", item.getCode());
        payload.put("itemName", describe(item));
        payload.put("brand", item.getBrand());
        payload.put("stockQty", stock);
        payload.put("safeStock", safe);
        payload.put("shortage", safe.subtract(stock));

        if (open == null) {
            StockAlert alert = new StockAlert();
            alert.setItemId(item.getId());
            alert.setItemName(describe(item));
            alert.setBatchId(batchId);
            alert.setCurrentQty(stock);
            alert.setSafeStock(safe);
            alert.setLevel(level);
            alert.setStatus("OPEN");
            alert.setSourceTxnId(sourceTxnId);
            alert.setPayload(payload);
            alert.setCreateTime(LocalDateTime.now());
            alertMapper.insert(alert);
            return alert;
        }

        open.setItemName(describe(item));
        open.setBatchId(batchId);
        open.setCurrentQty(stock);
        open.setSafeStock(safe);
        open.setLevel(level);
        open.setSourceTxnId(sourceTxnId);
        open.setPayload(payload);
        alertMapper.updateById(open);
        return open;
    }

    @Transactional(rollbackFor = Exception.class)
    public StockAlert markNotified(Long id) {
        StockAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            return null;
        }
        alert.setNotifiedAt(LocalDateTime.now());
        alertMapper.updateById(alert);
        return alert;
    }

    private StockAlert findOpen(Long itemId) {
        return alertMapper.selectOne(Wrappers.lambdaQuery(StockAlert.class)
                .eq(StockAlert::getItemId, itemId)
                .eq(StockAlert::getStatus, "OPEN")
                .last("LIMIT 1"));
    }

    private String describe(MaterialItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.getBrand() != null) {
            sb.append(item.getBrand()).append(' ');
        }
        if (item.getModel() != null) {
            sb.append(item.getModel());
        }
        if (item.getSpec() != null) {
            sb.append(" · ").append(item.getSpec());
        }
        return sb.toString().trim();
    }
}
