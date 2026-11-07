package com.smartmaterials.vo;

import com.smartmaterials.entity.StockAlert;
import com.smartmaterials.entity.StockBatch;
import com.smartmaterials.entity.StockTransaction;

import java.math.BigDecimal;

/** 出入库操作结果：流水 + 剩余库存 + 预警。 */
public record OperationResult(StockTransaction transaction,
                              StockBatch batch,
                              BigDecimal batchQuantity,
                              BigDecimal itemStock,
                              StockAlert warning,
                              BigDecimal monthlyUsed,
                              BigDecimal monthlyLimit) {
}
