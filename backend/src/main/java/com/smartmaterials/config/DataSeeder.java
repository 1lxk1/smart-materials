package com.smartmaterials.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartmaterials.entity.MaterialItem;
import com.smartmaterials.entity.StockBatch;
import com.smartmaterials.entity.StockTransaction;
import com.smartmaterials.entity.SysUser;
import com.smartmaterials.mapper.MaterialItemMapper;
import com.smartmaterials.mapper.StockBatchMapper;
import com.smartmaterials.mapper.StockTransactionMapper;
import com.smartmaterials.mapper.SysUserMapper;
import com.smartmaterials.service.AlertService;
import com.smartmaterials.support.AppTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动数据初始化：种子用户（密码需 BCrypt 加密，所以放在 Java 里）、示例流水、
 * 以及按当前库存补齐安全库存预警。
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppProperties appProperties;
    private final SysUserMapper userMapper;
    private final MaterialItemMapper itemMapper;
    private final StockTransactionMapper txnMapper;
    private final StockBatchMapper batchMapper;
    private final AlertService alertService;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public DataSeeder(AppProperties appProperties,
                      SysUserMapper userMapper,
                      MaterialItemMapper itemMapper,
                      StockTransactionMapper txnMapper,
                      StockBatchMapper batchMapper,
                      AlertService alertService,
                      PasswordEncoder passwordEncoder,
                      Environment environment) {
        this.appProperties = appProperties;
        this.userMapper = userMapper;
        this.itemMapper = itemMapper;
        this.txnMapper = txnMapper;
        this.batchMapper = batchMapper;
        this.alertService = alertService;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        if (!appProperties.getInit().isSeed()) {
            return;
        }
        seedUsers();
        seedTransactions();
        reconcileAlerts();
    }

    private void seedUsers() {
        String rawPassword = environment.getProperty("SEED_PASSWORD", "123456");
        Map<String, String[]> seeds = new LinkedHashMap<>();
        // ldapUid -> {name, role, email, phone, dept, monthlyLimit}
        seeds.put("admin", new String[]{"系统管理员", "admin", "admin@univ.edu.cn", "13800000001", "实验中心", ""});
        seeds.put("lab01", new String[]{"李实验", "lab", "lab@univ.edu.cn", "13800000002", "材料实验室", ""});
        seeds.put("stu2024001", new String[]{"王同学", "student", "wang@stu.edu.cn", "13800000003", "材料学院", "20"});
        seeds.put("teacher01", new String[]{"张老师", "teacher", "zhang@univ.edu.cn", "13800000004", "材料学院", ""});
        seeds.put("buyer01", new String[]{"赵采购", "buyer", "zhao@univ.edu.cn", "13800000005", "采购办", ""});

        for (Map.Entry<String, String[]> entry : seeds.entrySet()) {
            String uid = entry.getKey();
            Long exists = userMapper.selectCount(Wrappers.lambdaQuery(SysUser.class)
                    .eq(SysUser::getLdapUid, uid));
            if (exists != null && exists > 0) {
                continue;
            }
            String[] v = entry.getValue();
            SysUser user = new SysUser();
            user.setLdapUid(uid);
            user.setName(v[0]);
            user.setRole(v[1]);
            user.setEmail(v[2]);
            user.setPhone(v[3]);
            user.setDept(v[4]);
            user.setMonthlyLimit(v[5].isBlank() ? null : new BigDecimal(v[5]));
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setStatus("ACTIVE");
            user.setPreferences(new LinkedHashMap<>());
            user.setCreateTime(LocalDateTime.now(AppTime.ZONE));
            user.setUpdateTime(LocalDateTime.now(AppTime.ZONE));
            userMapper.insert(user);
            log.info("已创建种子用户 {} / {}（初始密码 {}）", uid, v[1], rawPassword);
        }
    }

    private void seedTransactions() {
        Long count = txnMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        SysUser student = findUser("stu2024001");
        SysUser lab = findUser("lab01");
        List<MaterialItem> items = itemMapper.selectList(Wrappers.lambdaQuery(MaterialItem.class)
                .orderByAsc(MaterialItem::getId));
        if (student == null || lab == null || items.size() < 5) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(AppTime.ZONE);
        insertTxn(items.get(0), "OUT", 2, student, "PRJ-2025-012", "分析化学实验", now.minusDays(3));
        insertTxn(items.get(2), "OUT", 4, student, "PRJ-2025-018", "分子生物学实验", now.minusDays(2));
        insertTxn(items.get(1), "RETURN", 1, student, "PRJ-2025-012", "未开封归还", now.minusDays(1));
        insertTxn(items.get(5), "SCRAP", 2, lab, "-", "包装破损报废", now.minusHours(20));
        insertTxn(items.get(0), "IN", 10, lab, "-", "学期初补货", now.minusDays(5));
        log.info("已写入 5 条示例流水");
    }

    private void insertTxn(MaterialItem item, String type, int qty, SysUser user,
                           String projectNo, String usage, LocalDateTime time) {
        StockBatch batch = batchMapper.selectOne(Wrappers.lambdaQuery(StockBatch.class)
                .eq(StockBatch::getItemId, item.getId())
                .orderByAsc(StockBatch::getId)
                .last("LIMIT 1"));
        StockTransaction txn = new StockTransaction();
        txn.setBatchId(batch == null ? 1L : batch.getId());
        txn.setItemId(item.getId());
        txn.setItemName((item.getBrand() == null ? "" : item.getBrand() + " ") + item.getModel());
        txn.setTxnType(type);
        txn.setQty(BigDecimal.valueOf(qty));
        txn.setUnitPrice(item.getUnitPrice());
        txn.setAmount((item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice())
                .multiply(BigDecimal.valueOf(qty)));
        txn.setUserId(user.getId());
        txn.setUserName(user.getName());
        txn.setProjectNo(projectNo);
        txn.setUsage(usage);
        txn.setStatus("DONE");
        txn.setAttachments(List.of());
        txn.setCreateBy(user.getLdapUid());
        txn.setCreateTime(time);
        txnMapper.insert(txn);
    }

    private void reconcileAlerts() {
        List<MaterialItem> items = itemMapper.selectList(Wrappers.lambdaQuery(MaterialItem.class));
        int created = 0;
        for (MaterialItem item : items) {
            if (alertService.refresh(item, null, null) != null) {
                created++;
            }
        }
        if (created > 0) {
            log.info("启动巡检：{} 种材料低于安全库存，已生成预警", created);
        }
    }

    private SysUser findUser(String ldapUid) {
        return userMapper.selectOne(Wrappers.lambdaQuery(SysUser.class)
                .eq(SysUser::getLdapUid, ldapUid)
                .last("LIMIT 1"));
    }
}
