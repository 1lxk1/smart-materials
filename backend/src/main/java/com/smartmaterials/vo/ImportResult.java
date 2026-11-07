package com.smartmaterials.vo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 批量导入结果：写入了哪些批次、哪些行失败（失败时整体回滚，imported 为 0）。 */
public class ImportResult {

    private final int total;
    private int imported;
    private final List<ImportedBatch> batches = new ArrayList<>();
    private final List<RowError> errors = new ArrayList<>();

    public ImportResult(int total) {
        this.total = total;
    }

    public int getTotal() {
        return total;
    }

    public int getImported() {
        return imported;
    }

    public void markImported() {
        this.imported++;
    }

    public List<ImportedBatch> getBatches() {
        return batches;
    }

    public List<RowError> getErrors() {
        return errors;
    }

    public void addError(int row, String message) {
        errors.add(new RowError(row, message));
    }

    /** 单行导入成功明细。 */
    public record ImportedBatch(int row, String itemCode, String batchCode, BigDecimal qty) {
    }

    /** 单行失败原因，row 为 Excel 行号（从 1 开始，含表头）。 */
    public record RowError(int row, String message) {
    }
}
