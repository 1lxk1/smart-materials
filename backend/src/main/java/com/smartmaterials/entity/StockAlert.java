package com.smartmaterials.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartmaterials.support.JsonbTypeHandler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** 安全库存预警记录。 */
@TableName(value = "stock_alert", autoResultMap = true)
public class StockAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("item_id")
    private Long itemId;

    @TableField("item_name")
    private String itemName;

    @TableField("batch_id")
    private Long batchId;

    @TableField("current_qty")
    private BigDecimal currentQty;

    @TableField("safe_stock")
    private BigDecimal safeStock;

    /** warning | danger。 */
    private String level;

    /** OPEN | CLOSED。 */
    private String status;

    @TableField("source_txn_id")
    private Long sourceTxnId;

    @TableField(value = "payload", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> payload;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("notified_at")
    private LocalDateTime notifiedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("closed_at")
    private LocalDateTime closedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public BigDecimal getCurrentQty() {
        return currentQty;
    }

    public void setCurrentQty(BigDecimal currentQty) {
        this.currentQty = currentQty;
    }

    public BigDecimal getSafeStock() {
        return safeStock;
    }

    public void setSafeStock(BigDecimal safeStock) {
        this.safeStock = safeStock;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getSourceTxnId() {
        return sourceTxnId;
    }

    public void setSourceTxnId(Long sourceTxnId) {
        this.sourceTxnId = sourceTxnId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public LocalDateTime getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(LocalDateTime notifiedAt) {
        this.notifiedAt = notifiedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
