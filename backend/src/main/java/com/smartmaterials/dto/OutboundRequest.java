package com.smartmaterials.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 领用出库请求：扫码进来时传 batchCode，手工选择时传 batchId。 */
public class OutboundRequest {

    private Long batchId;

    /** 二维码里的批次号。 */
    private String batchCode;

    @NotNull(message = "请填写领用数量")
    @DecimalMin(value = "0.001", message = "领用数量必须大于 0")
    private BigDecimal qty;

    @NotBlank(message = "请填写项目号")
    private String projectNo;

    @NotBlank(message = "请填写用途")
    private String usage;

    private String remark;

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
