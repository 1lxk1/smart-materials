package com.smartmaterials.service;

import com.smartmaterials.common.BizException;
import com.smartmaterials.dto.InboundRequest;
import com.smartmaterials.dto.OutboundRequest;
import com.smartmaterials.dto.ReturnScrapRequest;
import com.smartmaterials.entity.MaterialItem;
import com.smartmaterials.entity.StockAlert;
import com.smartmaterials.entity.StockBatch;
import com.smartmaterials.entity.StockTransaction;
import com.smartmaterials.entity.SysUser;
import com.smartmaterials.mapper.MaterialItemMapper;
import com.smartmaterials.mapper.StockBatchMapper;
import com.smartmaterials.mapper.StockTransactionMapper;
import com.smartmaterials.mapper.SysUserMapper;
import com.smartmaterials.support.AppTime;
import com.smartmaterials.vo.OperationResult;
import com.smartmaterials.vo.QuotaVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 库存核心业务：入库 / 领用出库 / 归还 / 报废。
 *
 * <p>领用出库的全流程都在一个事务里：</p>
 * <ol>
 *   <li>行级锁锁定批次（FOR UPDATE），防止并发超卖；</li>
 *   <li>校验批次库存与效期；</li>
 *   <li>校验学生月度限额（sys_user.monthly_limit + 本月 OUT 流水汇总）；</li>
 *   <li>扣减批次库存与台账库存；</li>
 *   <li>写入 stock_transaction；</li>
 *   <li>重新计算总库存，低于 safe_stock 时写入 stock_alert 预警记录。</li>
 * </ol>
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StockBatchMapper batchMapper;
    private final MaterialItemMapper itemMapper;
    private final StockTransactionMapper txnMapper;
    private final SysUserMapper userMapper;
    private final AlertService alertService;

    public StockService(StockBatchMapper batchMapper,
                        MaterialItemMapper itemMapper,
                        StockTransactionMapper txnMapper,
                        SysUserMapper userMapper,
                        AlertService alertService) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.txnMapper = txnMapper;
        this.userMapper = userMapper;
        this.alertService = alertService;
    }

    // ------------------------------------------------------------------ 领用出库

    /** 领用出库：校验库存 → 校验月度限额 → 扣库存 → 写流水 → 低于安全库存写预警。 */
    @Transactional(rollbackFor = Exception.class)
    public OperationResult outbound(OutboundRequest request, SysUser operator) {
        BigDecimal qty = request.getQty();

        StockBatch batch = lockBatch(request.getBatchId(), request.getBatchCode());
        MaterialItem item = requireItem(batch.getItemId());

        // 1. 校验批次库存
        if (batch.getQuantity().compareTo(qty) < 0) {
            throw new BizException(409, "库存不足：批次 " + batch.getBatchCode()
                    + " 当前仅剩 " + plain(batch.getQuantity()) + "，本次申请 " + plain(qty));
        }

        // 1.1 校验效期
        if (batch.getExpireDate() != null && batch.getExpireDate().isBefore(LocalDate.now(AppTime.ZONE))) {
            throw new BizException(409, "批次 " + batch.getBatchCode() + " 已过期（有效期 "
                    + batch.getExpireDate() + "），禁止领用");
        }

        // 2. 校验学生月度限额
        SysUser account = userMapper.selectById(operator.getId());
        if (account == null) {
            throw BizException.forbidden("用户不存在或已停用");
        }
        BigDecimal usedBefore = monthUsed(account.getId());
        checkMonthlyLimit(account, usedBefore, qty);

        // 3. 扣减库存（批次 + 台账，两条更新都在同一事务内）
        if (batchMapper.addQuantity(batch.getId(), qty.negate()) == 0) {
            throw new BizException(409, "库存不足，扣减失败，请刷新后重试");
        }
        if (itemMapper.addStock(item.getId(), qty.negate()) == 0) {
            throw new BizException(409, "台账库存不足，扣减失败");
        }
        MaterialItem latestItem = itemMapper.selectById(item.getId());
        StockBatch latestBatch = batchMapper.selectById(batch.getId());

        // 4. 写 stock_transaction
        StockTransaction txn = buildTransaction(latestItem, latestBatch, "OUT", qty,
                request.getUsage(), request.getProjectNo(), request.getRemark(), operator, null);
        txnMapper.insert(txn);

        // 5. 判断总库存是否低于 safe_stock，低于则写预警记录
        StockAlert warning = alertService.refresh(latestItem, latestBatch.getId(), txn.getId());
        if (warning != null) {
            log.warn("库存预警：材料 {} 剩余 {} 低于安全库存 {}",
                    latestItem.getCode(), plain(latestItem.getStockQty()), plain(latestItem.getSafeStock()));
        }

        BigDecimal usedAfter = usedBefore.add(qty);
        return new OperationResult(txn, latestBatch, latestBatch.getQuantity(), latestItem.getStockQty(),
                warning, usedAfter, account.getMonthlyLimit());
    }

    // ------------------------------------------------------------------ 归还 / 报废

    /** 归还（RETURN）：库存回补；报废（SCRAP）：库存扣减。两者都写流水。 */
    @Transactional(rollbackFor = Exception.class)
    public OperationResult returnOrScrap(ReturnScrapRequest request, SysUser operator) {
        BigDecimal qty = request.getQty();
        StockBatch batch = lockBatch(request.getBatchId(), null);
        MaterialItem item = requireItem(batch.getItemId());

        boolean isReturn = "RETURN".equals(request.getType());
        BigDecimal delta = isReturn ? qty : qty.negate();

        if (!isReturn && batch.getQuantity().compareTo(qty) < 0) {
            throw new BizException(409, "报废数量超过批次剩余库存：当前仅剩 " + plain(batch.getQuantity()));
        }

        if (batchMapper.addQuantity(batch.getId(), delta) == 0) {
            throw new BizException(409, "库存不足，" + (isReturn ? "归还" : "报废") + "失败");
        }
        int itemRows = itemMapper.addStock(item.getId(), delta);
        if (itemRows == 0 && !isReturn) {
            throw new BizException(409, "台账库存不足，报废失败");
        }
        if (itemRows == 0) {
            // 归还时台账可能为负（历史数据），这里直接按批次汇总校准
            itemMapper.syncStockFromBatches(item.getId());
        }

        MaterialItem latestItem = itemMapper.selectById(item.getId());
        StockBatch latestBatch = batchMapper.selectById(batch.getId());

        StockTransaction txn = buildTransaction(latestItem, latestBatch, request.getType(), qty,
                request.getUsage(), request.getProjectNo(), request.getRemark(), operator, request.getPhotos());
        txnMapper.insert(txn);

        StockAlert warning = alertService.refresh(latestItem, latestBatch.getId(), txn.getId());

        return new OperationResult(txn, latestBatch, latestBatch.getQuantity(), latestItem.getStockQty(),
                warning, null, null);
    }

    // ------------------------------------------------------------------ 入库

    /** 入库：生成批次号 + 条码，增加库存，写 IN 流水。 */
    @Transactional(rollbackFor = Exception.class)
    public OperationResult inbound(InboundRequest request, SysUser operator) {
        MaterialItem item = requireItem(request.getItemId());
        BigDecimal qty = request.getQty();

        String batchCode = nextBatchCode();
        String barcode = StringUtils.hasText(request.getBarcode())
                ? request.getBarcode().trim()
                : "MAT-" + item.getCode() + "-" + batchCode.substring(batchCode.length() - 3);

        if (batchMapper.selectByBarcode(barcode) != null) {
            throw new BizException(409, "条码已存在：" + barcode + "，请更换或留空自动生成");
        }

        StockBatch batch = new StockBatch();
        batch.setItemId(item.getId());
        batch.setBatchCode(batchCode);
        batch.setBarcode(barcode);
        batch.setQuantity(qty);
        batch.setInitialQty(qty);
        batch.setLocation(request.getLocation());
        batch.setExpireDate(request.getExpireDate());
        batch.setSupplier(request.getSupplier());
        batch.setInboundTime(LocalDateTime.now(AppTime.ZONE));
        batch.setStatus("IN_STOCK");
        batch.setCreateTime(LocalDateTime.now(AppTime.ZONE));
        batch.setUpdateTime(LocalDateTime.now(AppTime.ZONE));
        batchMapper.insert(batch);

        itemMapper.addStock(item.getId(), qty);
        MaterialItem latestItem = itemMapper.selectById(item.getId());

        String usage = StringUtils.hasText(request.getRemark()) ? request.getRemark() : "入库上架";
        StockTransaction txn = buildTransaction(latestItem, batch, "IN", qty, usage, null, null, operator, null);
        txnMapper.insert(txn);

        // 入库可能让库存回到安全线以上，这里同样刷新（会关闭已恢复的预警）
        alertService.refresh(latestItem, batch.getId(), txn.getId());

        return new OperationResult(txn, batch, batch.getQuantity(), latestItem.getStockQty(), null, null, null);
    }

    // ------------------------------------------------------------------ 额度查询

    /** 当前用户本月已用 / 限额。 */
    public QuotaVO quota(SysUser user) {
        YearMonth month = YearMonth.now(AppTime.ZONE);
        BigDecimal used = monthUsed(user.getId());
        BigDecimal limit = user.getMonthlyLimit();
        boolean unlimited = limit == null;
        BigDecimal remaining = unlimited ? null : limit.subtract(used).max(BigDecimal.ZERO);
        return new QuotaVO(month.toString(), used, limit, remaining, unlimited, "件");
    }

    /** 指定用户在 [本月1日, 下月1日) 的 OUT 流水总量。 */
    public BigDecimal monthUsed(Long userId) {
        YearMonth month = YearMonth.now(AppTime.ZONE);
        LocalDateTime from = month.atDay(1).atStartOfDay();
        LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();
        BigDecimal sum = txnMapper.sumOutQty(userId, from, to);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    // ------------------------------------------------------------------ 私有方法

    private void checkMonthlyLimit(SysUser account, BigDecimal usedBefore, BigDecimal qty) {
        if (!"student".equals(account.getRole()) || account.getMonthlyLimit() == null) {
            return;
        }
        BigDecimal limit = account.getMonthlyLimit();
        if (usedBefore.add(qty).compareTo(limit) > 0) {
            throw new BizException(409, "超出个人月度限额：本月已领用 " + plain(usedBefore)
                    + "，限额 " + plain(limit) + "，本次申请 " + plain(qty)
                    + "，最多还能领用 " + plain(limit.subtract(usedBefore).max(BigDecimal.ZERO)));
        }
    }

    private StockBatch lockBatch(Long batchId, String batchCode) {
        StockBatch found;
        if (batchId != null) {
            found = batchMapper.selectForUpdate(batchId);
        } else if (StringUtils.hasText(batchCode)) {
            StockBatch byCode = batchMapper.selectByBatchCode(batchCode.trim());
            if (byCode == null) {
                throw BizException.notFound("未找到批次：" + batchCode);
            }
            found = batchMapper.selectForUpdate(byCode.getId());
        } else {
            throw new BizException("请传入 batchId 或 batchCode");
        }
        if (found == null) {
            throw BizException.notFound("批次不存在或已删除");
        }
        return found;
    }

    private MaterialItem requireItem(Long itemId) {
        MaterialItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw BizException.notFound("材料不存在或已删除");
        }
        return item;
    }

    private StockTransaction buildTransaction(MaterialItem item, StockBatch batch, String txnType,
                                              BigDecimal qty, String usage, String projectNo,
                                              String remark, SysUser operator, List<String> attachments) {
        BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
        BigDecimal amount = unitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP);

        StockTransaction txn = new StockTransaction();
        txn.setBatchId(batch.getId());
        txn.setItemId(item.getId());
        txn.setItemName(describe(item));
        txn.setTxnType(txnType);
        txn.setQty(qty);
        txn.setUnitPrice(unitPrice);
        txn.setAmount(amount);
        txn.setUserId(operator == null ? null : operator.getId());
        txn.setUserName(operator == null ? "system" : operator.getName());
        txn.setProjectNo(StringUtils.hasText(projectNo) ? projectNo : "-");
        txn.setUsage(usage);
        txn.setStatus("DONE");
        txn.setRemark(remark);
        txn.setAttachments(attachments == null ? List.of() : attachments);
        txn.setCreateBy(operator == null ? "system" : operator.getLdapUid());
        txn.setCreateTime(LocalDateTime.now(AppTime.ZONE));
        return txn;
    }

    private String nextBatchCode() {
        String prefix = "IN" + LocalDate.now(AppTime.ZONE).format(DAY);
        long todayCount = batchMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .lambdaQuery(StockBatch.class)
                .likeRight(StockBatch::getBatchCode, prefix));
        for (int seq = 1; seq <= 999; seq++) {
            String candidate = prefix + String.format("%03d", (int) (todayCount + seq));
            if (batchMapper.selectByBatchCode(candidate) == null) {
                return candidate;
            }
        }
        throw new BizException(500, "今日批次号已用尽，请调整编码规则");
    }

    private String describe(MaterialItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.getBrand() != null) {
            sb.append(item.getBrand()).append(' ');
        }
        if (item.getModel() != null) {
            sb.append(item.getModel());
        }
        if (item.getSpec() != null) {
            sb.append(" · ").append(item.getSpec());
        }
        return sb.toString().trim();
    }

    private String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
