package com.smartmaterials.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** 新增类目请求。 */
public class CategoryRequest {

    /** 父级类目 id，空表示顶级。 */
    private Long parentId;

    @NotBlank(message = "请填写类目名称")
    private String name;

    /** 类目编码，留空自动生成。 */
    private String code;

    private String unit = "个";

    private BigDecimal safeStock;

    private Integer sortNo;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
