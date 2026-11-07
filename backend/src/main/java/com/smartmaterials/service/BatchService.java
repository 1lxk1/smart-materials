package com.smartmaterials.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartmaterials.common.BizException;
import com.smartmaterials.entity.MaterialItem;
import com.smartmaterials.entity.StockBatch;
import com.smartmaterials.mapper.MaterialItemMapper;
import com.smartmaterials.mapper.StockBatchMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 库存批次查询（含材料信息拼装）。 */
@Service
public class BatchService {

    private final StockBatchMapper batchMapper;
    private final MaterialItemMapper itemMapper;

    public BatchService(StockBatchMapper batchMapper, MaterialItemMapper itemMapper) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
    }

    public List<StockBatch> list(String keyword, Long itemId) {
        List<StockBatch> batches = batchMapper.selectList(Wrappers.lambdaQuery(StockBatch.class)
                .eq(itemId != null, StockBatch::getItemId, itemId)
                .orderByDesc(StockBatch::getInboundTime)
                .orderByDesc(StockBatch::getId));
        enrich(batches);

        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (kw.isEmpty()) {
            return batches;
        }
        return batches.stream().filter(b -> contains(b.getBatchCode(), kw)
                || contains(b.getBarcode(), kw)
                || contains(b.getItemName(), kw)
                || contains(b.getLocation(), kw)).toList();
    }

    public StockBatch getByBatchCode(String batchCode) {
        StockBatch batch = batchMapper.selectByBatchCode(batchCode);
        if (batch == null) {
            throw BizException.notFound("未找到批次：" + batchCode);
        }
        enrich(List.of(batch));
        return batch;
    }

    public StockBatch getByBarcode(String barcode) {
        StockBatch batch = batchMapper.selectByBarcode(barcode);
        if (batch == null) {
            throw BizException.notFound("未找到条码对应的批次：" + barcode);
        }
        enrich(List.of(batch));
        return batch;
    }

    /** 批量补齐材料名称 / 价格 / 安全库存。 */
    public void enrich(List<StockBatch> batches) {
        if (batches.isEmpty()) {
            return;
        }
        List<Long> itemIds = batches.stream().map(StockBatch::getItemId).distinct().toList();
        Map<Long, MaterialItem> items = new LinkedHashMap<>();
        for (MaterialItem item : itemMapper.selectBatchIds(itemIds)) {
            items.put(item.getId(), item);
        }
        for (StockBatch batch : batches) {
            MaterialItem item = items.get(batch.getItemId());
            if (item == null) {
                continue;
            }
            batch.setItemName(describe(item));
            batch.setBrand(item.getBrand());
            batch.setModel(item.getModel());
            batch.setUnitPrice(item.getUnitPrice());
            batch.setSafeStock(item.getSafeStock());
        }
    }

    private String describe(MaterialItem item) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(item.getBrand())) {
            sb.append(item.getBrand()).append(' ');
        }
        if (StringUtils.hasText(item.getModel())) {
            sb.append(item.getModel());
        }
        if (StringUtils.hasText(item.getSpec())) {
            sb.append(" · ").append(item.getSpec());
        }
        return sb.toString().trim();
    }

    private boolean contains(String value, String kw) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(kw);
    }
}
