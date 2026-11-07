package com.smartmaterials.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartmaterials.support.JsonbTypeHandler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** 材料类目（四级树：大类 → 子类 → 品牌 → 型号）。 */
@TableName(value = "material_category", autoResultMap = true)
public class MaterialCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("parent_id")
    private Long parentId;

    private String code;

    private String name;

    private Integer level;

    private String unit;

    @TableField("safe_stock")
    private BigDecimal safeStock;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField(value = "extra", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> extra;

    @TableField("create_by")
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 非表字段：子类目，构建类目树用。 */
    @TableField(exist = false)
    private java.util.List<MaterialCategory> children;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
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

    public java.util.List<MaterialCategory> getChildren() {
        return children;
    }

    public void setChildren(java.util.List<MaterialCategory> children) {
        this.children = children;
    }
}
