package com.smartmaterials.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartmaterials.entity.StockBatch;
import com.smartmaterials.entity.StockTransaction;
import com.smartmaterials.mapper.StockBatchMapper;
import com.smartmaterials.mapper.StockTransactionMapper;
import com.smartmaterials.support.AppTime;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 流水查询与报表汇总。 */
@Service
public class ReportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final StockTransactionMapper txnMapper;
    private final StockBatchMapper batchMapper;

    public ReportService(StockTransactionMapper txnMapper, StockBatchMapper batchMapper) {
        this.txnMapper = txnMapper;
        this.batchMapper = batchMapper;
    }

    /** 最近流水（可按类型过滤），带批次号便于展示。 */
    public List<StockTransaction> transactions(String txnType, int limit) {
        return transactions(txnType, null, null, limit);
    }

    /** 按时间区间查询流水（from / to 为闭区间日期，可为空）。 */
    public List<StockTransaction> transactions(String txnType, LocalDate from, LocalDate to, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
        LambdaQueryWrapper<StockTransaction> query = Wrappers.lambdaQuery(StockTransaction.class)
                .eq(StringUtils.hasText(txnType), StockTransaction::getTxnType, txnType)
                .ge(fromTime != null, StockTransaction::getCreateTime, fromTime)
                .lt(toTime != null, StockTransaction::getCreateTime, toTime)
                .orderByDesc(StockTransaction::getCreateTime)
                .orderByDesc(StockTransaction::getId)
                .last("LIMIT " + safeLimit);

        List<StockTransaction> list = txnMapper.selectList(query);
        enrich(list);
        return list;
    }

    /** 导出用行数据：材料代码 / 名称 / 单价 / 数量 / 总价 / 使用人 / 项目号 / 用途。 */
    public List<Map<String, Object>> exportRows(String txnType, String projectNo) {
        return exportRows(txnType, projectNo, null, null);
    }

    /** 导出用行数据（带时间区间，供 Excel / PDF 生成使用）。 */
    public List<Map<String, Object>> exportRows(String txnType, String projectNo,
                                                LocalDate from, LocalDate to) {
        return transactions(StringUtils.hasText(txnType) ? txnType : "OUT", from, to, 500).stream()
                .filter(t -> !StringUtils.hasText(projectNo) || projectNo.equals(t.getProjectNo()))
                .map(this::toRow)
                .toList();
    }

    /** 按项目号汇总（经费对应表）。 */
    public List<Map<String, Object>> projectSummary() {
        YearMonth month = YearMonth.now(AppTime.ZONE);
        return txnMapper.summarizeByProject(month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay());
    }

    private Map<String, Object> toRow(StockTransaction txn) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", txn.getId());
        row.put("code", StringUtils.hasText(txn.getBarcode()) ? txn.getBarcode() : txn.getBatchCode());
        row.put("name", txn.getItemName());
        row.put("price", txn.getUnitPrice() == null ? BigDecimal.ZERO : txn.getUnitPrice());
        row.put("qty", txn.getQty());
        row.put("amount", txn.getAmount() == null ? BigDecimal.ZERO : txn.getAmount());
        row.put("user", txn.getUserName());
        row.put("projectNo", txn.getProjectNo());
        row.put("usage", txn.getUsage());
        row.put("createTime", txn.getCreateTime() == null ? ""
                : txn.getCreateTime().format(DATE_TIME));
        return row;
    }

    private void enrich(List<StockTransaction> list) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> batchIds = list.stream().map(StockTransaction::getBatchId).distinct().toList();
        List<StockBatch> batches = batchMapper.selectBatchIds(batchIds);
        Map<Long, StockBatch> index = new LinkedHashMap<>();
        for (StockBatch batch : batches) {
            index.put(batch.getId(), batch);
        }
        for (StockTransaction txn : list) {
            StockBatch batch = index.get(txn.getBatchId());
            if (batch != null) {
                txn.setBatchCode(batch.getBatchCode());
                txn.setBarcode(batch.getBarcode());
            }
        }
    }

    /** 供导出接口使用的空列表兜底。 */
    public List<StockTransaction> empty() {
        return new ArrayList<>();
    }
}
