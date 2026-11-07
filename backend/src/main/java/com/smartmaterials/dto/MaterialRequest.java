package com.smartmaterials.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

/** 新增 / 编辑材料请求。 */
public class MaterialRequest {

    @NotNull(message = "请选择类目")
    private Long categoryId;

    @NotBlank(message = "请填写材料编码")
    private String code;

    private String brand;

    private String model;

    private String spec;

    private BigDecimal unitPrice = BigDecimal.ZERO;

    private String currency = "CNY";

    private String unit = "个";

    private BigDecimal safeStock = BigDecimal.ZERO;

    private Map<String, Object> specs;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getSafeStock() {
        return safeStock;
    }

    public void setSafeStock(BigDecimal safeStock) {
        this.safeStock = safeStock;
    }

    public Map<String, Object> getSpecs() {
        return specs;
    }

    public void setSpecs(Map<String, Object> specs) {
        this.specs = specs;
    }
}
