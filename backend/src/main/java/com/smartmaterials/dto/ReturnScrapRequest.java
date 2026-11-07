package com.smartmaterials.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;

/** 归还 / 报废请求。 */
public class ReturnScrapRequest {

    /** RETURN 归还（库存回补）| SCRAP 报废（库存扣减）。 */
    @NotBlank(message = "请选择业务类型")
    @Pattern(regexp = "RETURN|SCRAP", message = "业务类型只能是 RETURN 或 SCRAP")
    private String type;

    @NotNull(message = "请选择关联批次")
    private Long batchId;

    @NotNull(message = "请填写数量")
    @DecimalMin(value = "0.001", message = "数量必须大于 0")
    private BigDecimal qty;

    @NotBlank(message = "请填写说明")
    private String usage;

    private String projectNo;

    private String remark;

    /** 报废照片 / 附件地址。 */
    private List<String> photos;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }

    public String getProjectNo() {
        return projectNo;
    }

    public void setProjectNo(String projectNo) {
        this.projectNo = projectNo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<String> getPhotos() {
        return photos;
    }

    public void setPhotos(List<String> photos) {
        this.photos = photos;
    }
}
