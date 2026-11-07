package com.smartmaterials.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmaterials.entity.StockTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 出入库流水 Mapper。 */
@Mapper
public interface StockTransactionMapper extends BaseMapper<StockTransaction> {

    /** 指定用户在 [from, to) 区间内的领用总量（月度限额校验用）。 */
    @Select("SELECT COALESCE(SUM(qty), 0) FROM stock_transaction "
            + "WHERE user_id = #{userId} AND txn_type = 'OUT' "
            + "AND create_time >= #{from} AND create_time < #{to}")
    BigDecimal sumOutQty(@Param("userId") Long userId,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to);

    /** 本月领用单数。 */
    @Select("SELECT COUNT(*) FROM stock_transaction WHERE txn_type = 'OUT' "
            + "AND create_time >= #{from} AND create_time < #{to}")
    long countOut(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("SELECT COUNT(*) FROM stock_transaction WHERE txn_type = #{txnType} "
            + "AND create_time >= #{from} AND create_time < #{to}")
    long countByType(@Param("txnType") String txnType,
                     @Param("from") LocalDateTime from,
                     @Param("to") LocalDateTime to);

    /** 月度 TOP N 耗材（按领用次数 + 金额）。 */
    @Select("SELECT item_name AS name, COUNT(*) AS times, COALESCE(SUM(qty), 0) AS qty, "
            + "COALESCE(SUM(amount), 0) AS amount FROM stock_transaction "
            + "WHERE txn_type = 'OUT' AND create_time >= #{from} AND create_time < #{to} "
            + "GROUP BY item_name ORDER BY qty DESC, times DESC LIMIT #{limit}")
    List<Map<String, Object>> topConsumables(@Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to,
                                             @Param("limit") int limit);

    /** 按项目号汇总，用于经费对应表。 */
    @Select("SELECT COALESCE(project_no, '-') AS project_no, COUNT(*) AS times, "
            + "COALESCE(SUM(amount), 0) AS amount FROM stock_transaction "
            + "WHERE txn_type = 'OUT' AND create_time >= #{from} AND create_time < #{to} "
            + "GROUP BY project_no ORDER BY amount DESC")
    List<Map<String, Object>> summarizeByProject(@Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);
}
