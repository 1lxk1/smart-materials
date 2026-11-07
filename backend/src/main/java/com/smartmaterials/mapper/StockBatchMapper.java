package com.smartmaterials.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmaterials.entity.StockBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/** 库存批次 Mapper。 */
@Mapper
public interface StockBatchMapper extends BaseMapper<StockBatch> {

    /** 行级锁：领用出库必须先锁定批次，防止并发超卖。 */
    @Select("SELECT * FROM stock_batch WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    StockBatch selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM stock_batch WHERE batch_code = #{batchCode} AND deleted = 0 LIMIT 1")
    StockBatch selectByBatchCode(@Param("batchCode") String batchCode);

    @Select("SELECT * FROM stock_batch WHERE barcode = #{barcode} AND deleted = 0 LIMIT 1")
    StockBatch selectByBarcode(@Param("barcode") String barcode);

    @Update("UPDATE stock_batch SET quantity = quantity + #{delta}, "
            + "status = CASE WHEN quantity + #{delta} <= 0 THEN 'DEPLETED' ELSE 'IN_STOCK' END, "
            + "update_time = now() WHERE id = #{batchId} AND quantity + #{delta} >= 0")
    int addQuantity(@Param("batchId") Long batchId, @Param("delta") BigDecimal delta);
}
