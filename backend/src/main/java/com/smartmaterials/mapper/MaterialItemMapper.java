package com.smartmaterials.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmaterials.entity.MaterialItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/** 材料台账 Mapper。 */
@Mapper
public interface MaterialItemMapper extends BaseMapper<MaterialItem> {

    /** 原子增减台账库存（负数即扣减），避免读出再写回的并发覆盖。 */
    @Update("UPDATE material_item SET stock_qty = stock_qty + #{delta}, update_time = now() "
            + "WHERE id = #{itemId} AND stock_qty + #{delta} >= 0 AND deleted = 0")
    int addStock(@Param("itemId") Long itemId, @Param("delta") BigDecimal delta);

    /** 直接用批次汇总值校准台账库存。 */
    @Update("UPDATE material_item SET stock_qty = COALESCE("
            + "(SELECT SUM(b.quantity) FROM stock_batch b WHERE b.item_id = #{itemId} AND b.deleted = 0), 0), "
            + "update_time = now() WHERE id = #{itemId}")
    int syncStockFromBatches(@Param("itemId") Long itemId);
}
