package com.smartmaterials;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmaterials.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 核心库存链路的集成测试（真实 PostgreSQL 15 + 真实 Spring 上下文）。
 *
 * <p>覆盖验收清单：① 登录成功/失败；② 库存不足回滚；③ 学生超额回滚且不产生流水；
 * ④ 出库后 stock_transaction 有 OUT 记录且 stock_alert 正确；⑤ 归还后预警自动关闭。</p>
 */
class StockFlowIntegrationTest extends IntegrationTestBase {

    // ------------------------------------------------------------------ ① 登录

    @Test
    @DisplayName("① 登录成功返回 JWT；密码错误返回 401 且不泄露账号信息")
    void loginSuccessAndFailure() {
        ResponseEntity<String> ok = rest.postForEntity("/api/login",
                new org.springframework.http.HttpEntity<>(
                        Map.of("username", "admin", "password", "123456"),
                        jsonHeaders()),
                String.class);

        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json(ok.getBody());
        assertThat(body.path("token").asText()).isNotBlank();
        assertThat(body.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.path("user").path("role").asText()).isEqualTo("admin");
        assertThat(body.path("permissions").path("canWriteStock").asBoolean()).isTrue();

        ResponseEntity<String> bad = rest.postForEntity("/api/login",
                new org.springframework.http.HttpEntity<>(
                        Map.of("username", "admin", "password", "wrong-password"),
                        jsonHeaders()),
                String.class);
        assertThat(bad.getStatusCode().value()).isEqualTo(401);
        assertThat(json(bad.getBody()).path("message").asText()).contains("账号或密码错误");

        // 未带 token 访问受保护接口必须是 401
        assertThat(getJson("/api/materials", null).getStatusCode().value()).isEqualTo(401);
    }

    // ------------------------------------------------------------------ ② 库存不足

    @Test
    @DisplayName("② 领用数量超过批次库存：409 且批次、台账、流水全部回滚")
    void outboundOverStockRollsBack() {
        long[] ids = createItemAndBatch("IT-OVERSTOCK", 0, 3);
        long itemId = ids[0];
        long batchId = ids[1];
        String token = login("admin", "123456");

        ResponseEntity<String> response = postJson("/api/stock/outbound", Map.of(
                "batchId", batchId,
                "qty", 5,
                "projectNo", "PRJ-IT-001",
                "usage", "集成测试超量领用"
        ), token);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(json(response.getBody()).path("message").asText()).contains("库存不足");

        assertThat(batchQuantity(batchId)).isEqualTo(3.0);
        assertThat(itemStock(itemId)).isEqualTo(3.0);
        assertThat(countTransactionsForBatch(batchId)).isZero();
    }

    // ------------------------------------------------------------------ ③ 月度限额

    @Test
    @DisplayName("③ 学生超过月度限额：409，且不产生任何流水、库存不变")
    void studentOverMonthlyLimitRollsBackWithoutTransaction() {
        long[] ids = createItemAndBatch("IT-QUOTA", 0, 100);
        long itemId = ids[0];
        long batchId = ids[1];
        Object[] user = createUser("student", 5.0);
        long userId = (Long) user[0];
        String username = (String) user[1];
        String token = login(username, "123456");

        ResponseEntity<String> first = postJson("/api/stock/outbound", Map.of(
                "batchId", batchId, "qty", 3, "projectNo", "PRJ-IT-002", "usage", "第一次领用"
        ), token);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(json(first.getBody()).path("monthlyUsed").asDouble()).isEqualTo(3.0);

        ResponseEntity<String> second = postJson("/api/stock/outbound", Map.of(
                "batchId", batchId, "qty", 3, "projectNo", "PRJ-IT-002", "usage", "第二次领用（应超限）"
        ), token);
        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(json(second.getBody()).path("message").asText()).contains("超出个人月度限额");

        // 只有第一次产生流水；第二次必须完整回滚
        assertThat(countTransactions(userId, "OUT")).isEqualTo(1);
        assertThat(batchQuantity(batchId)).isEqualTo(97.0);
        assertThat(itemStock(itemId)).isEqualTo(97.0);
    }

    // ------------------------------------------------------------------ ④⑤ 预警

    @Test
    @DisplayName("④ 出库低于安全库存写入 OUT 流水与 OPEN 预警；⑤ 归还补足后预警自动 CLOSED")
    void alertOpensOnOutboundAndClosesAfterReturn() {
        long[] ids = createItemAndBatch("IT-ALERT", 10, 12);
        long itemId = ids[0];
        long batchId = ids[1];
        String token = login("admin", "123456");

        ResponseEntity<String> outbound = postJson("/api/stock/outbound", Map.of(
                "batchId", batchId, "qty", 5, "projectNo", "PRJ-IT-003", "usage", "降到安全线以下"
        ), token);
        assertThat(outbound.getStatusCode().value()).isEqualTo(200);
        JsonNode result = json(outbound.getBody());
        assertThat(result.path("transaction").path("txnType").asText()).isEqualTo("OUT");
        assertThat(result.path("transaction").path("qty").asDouble()).isEqualTo(5.0);
        assertThat(result.path("warning").path("status").asText()).isEqualTo("OPEN");

        // ④ 台账 / 批次 / 流水 / 预警四项一致
        assertThat(itemStock(itemId)).isEqualTo(7.0);
        assertThat(batchQuantity(batchId)).isEqualTo(7.0);
        Map<String, Object> txn = jdbc.queryForMap("""
                SELECT txn_type, qty, amount, project_no FROM stock_transaction
                WHERE batch_id = ? ORDER BY id DESC LIMIT 1
                """, batchId);
        assertThat(txn.get("txn_type")).isEqualTo("OUT");
        assertThat(((Number) txn.get("qty")).doubleValue()).isEqualTo(5.0);
        assertThat(txn.get("project_no")).isEqualTo("PRJ-IT-003");

        Map<String, Object> alert = jdbc.queryForMap("""
                SELECT status, level, current_qty, safe_stock FROM stock_alert
                WHERE item_id = ? AND status = 'OPEN' ORDER BY id DESC LIMIT 1
                """, itemId);
        assertThat(alert.get("status")).isEqualTo("OPEN");
        assertThat(alert.get("level")).isEqualTo("warning");
        assertThat(((Number) alert.get("current_qty")).doubleValue()).isEqualTo(7.0);
        assertThat(((Number) alert.get("safe_stock")).doubleValue()).isEqualTo(10.0);

        // ⑤ 归还 5 个，库存回到 12 ≥ 安全库存 10，预警自动关闭
        ResponseEntity<String> returned = postJson("/api/stock/return-scrap", Map.of(
                "type", "RETURN", "batchId", batchId, "qty", 5,
                "usage", "归还补足库存", "projectNo", "PRJ-IT-003"
        ), token);
        assertThat(returned.getStatusCode().value()).isEqualTo(200);
        assertThat(itemStock(itemId)).isEqualTo(12.0);

        Map<String, Object> closed = jdbc.queryForMap("""
                SELECT status, closed_at FROM stock_alert
                WHERE item_id = ? ORDER BY id DESC LIMIT 1
                """, itemId);
        assertThat(closed.get("status")).isEqualTo("CLOSED");
        assertThat(closed.get("closed_at")).isNotNull();

        String returnType = jdbc.queryForObject("""
                SELECT txn_type FROM stock_transaction WHERE batch_id = ? ORDER BY id DESC LIMIT 1
                """, String.class, batchId);
        assertThat(returnType).isEqualTo("RETURN");

        // 台账库存必须等于该材料全部批次之和（数据一致性验收项）
        Double diff = jdbc.queryForObject("""
                SELECT mi.stock_qty - COALESCE((SELECT SUM(b.quantity) FROM stock_batch b
                         WHERE b.item_id = mi.id AND b.deleted = 0), 0)
                FROM material_item mi WHERE mi.id = ?
                """, Double.class, itemId);
        assertThat(diff).isZero();
    }

    // ------------------------------------------------------------------ 辅助

    private org.springframework.http.HttpHeaders jsonHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private int countTransactionsForBatch(long batchId) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_transaction WHERE batch_id = ?", Integer.class, batchId);
        return value == null ? 0 : value;
    }
}
