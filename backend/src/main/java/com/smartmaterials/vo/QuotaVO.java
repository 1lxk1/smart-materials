package com.smartmaterials.vo;

import java.math.BigDecimal;

/** 个人月度领用额度。 */
public record QuotaVO(String month, BigDecimal used, BigDecimal limit, BigDecimal remaining, boolean unlimited,
                      String unit) {
}
