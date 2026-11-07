package com.smartmaterials.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 库存批次：一个批次一个二维码标签。 */
@TableName("stock_batch")
public class StockBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("item_id")
    private Long itemId;

    @TableField("batch_code")
    private String batchCode;

    /** 条码：二维码标签可编码 batchCode，也可编码 barcode。 */
    private String barcode;

    private BigDecimal quantity;

    @TableField("initial_qty")
    private BigDecimal initialQty;

    private String location;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("expire_date")
    private LocalDate expireDate;

    private String supplier;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("inbound_time")
    private LocalDateTime inboundTime;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 非表字段：材料名称 / 品牌型号，列表展示用。 */
    @TableField(exist = false)
    private String itemName;

    @TableField(exist = false)
    private String brand;

    @TableField(exist = false)
    private String model;

    @TableField(exist = false)
    private BigDecimal unitPrice;

    @TableField(exist = false)
    private BigDecimal safeStock;

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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getInitialQty() {
        return initialQty;
    }

    public void setInitialQty(BigDecimal initialQty) {
        this.initialQty = initialQty;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(LocalDate expireDate) {
        this.expireDate = expireDate;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public LocalDateTime getInboundTime() {
        return inboundTime;
    }

    public void setInboundTime(LocalDateTime inboundTime) {
        this.inboundTime = inboundTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getSafeStock() {
        return safeStock;
    }

    public void setSafeStock(BigDecimal safeStock) {
        this.safeStock = safeStock;
    }
}
