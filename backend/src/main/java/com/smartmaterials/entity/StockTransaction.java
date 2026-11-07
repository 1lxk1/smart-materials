package com.smartmaterials.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartmaterials.support.JsonbListTypeHandler;
import com.smartmaterials.support.JsonbTypeHandler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 出入库流水：IN 入库 / OUT 领用 / RETURN 归还 / SCRAP 报废。 */
@TableName(value = "stock_transaction", autoResultMap = true)
public class StockTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("batch_id")
    private Long batchId;

    @TableField("item_id")
    private Long itemId;

    @TableField("item_name")
    private String itemName;

    /** IN | OUT | RETURN | SCRAP。 */
    @TableField("txn_type")
    private String txnType;

    private BigDecimal qty;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    private BigDecimal amount;

    @TableField("user_id")
    private Long userId;

    @TableField("user_name")
    private String userName;

    /** 项目号 / 课程号，用于经费对应表。 */
    @TableField("project_no")
    private String projectNo;

    /** 用途说明（数据库列 usage_desc，前端字段仍是 usage）。 */
    @JsonProperty("usage")
    @TableField("usage_desc")
    private String usage;

    private String status;

    private String remark;

    /** JSONB 附件数组：报废照片、领用单等。 */
    @TableField(value = "attachments", typeHandler = JsonbListTypeHandler.class)
    private List<String> attachments;

    @TableField(value = "extra", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> extra;

    @TableField("create_by")
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 非表字段：批次号 / 条码，列表展示用。 */
    @TableField(exist = false)
    private String batchCode;

    @TableField(exist = false)
    private String barcode;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
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

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getProjectNo() {
        return projectNo;
    }

    public void setProjectNo(String projectNo) {
        this.projectNo = projectNo;
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<String> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<String> attachments) {
        this.attachments = attachments;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }
}
