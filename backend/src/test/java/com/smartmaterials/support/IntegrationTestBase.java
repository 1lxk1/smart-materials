package com.smartmaterials.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 集成测试基类：启动一个真实的 PostgreSQL 15（zonky embedded-postgres，
 * 二进制从 Maven 依赖里解压，不依赖本机数据库、也不需要 Docker）。
 *
 * <p>端口随机、数据目录随机，每个测试类共享同一个集群；测试方法各自
 * 用唯一 code 造数据，互不影响。schema.sql / data.sql 由应用启动时的
 * spring.sql.init 自动执行，因此这里同时验证了建表脚本的 PG 语法。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    private static EmbeddedPostgres startPostgres() {
        try {
            EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    pg.close();
                } catch (IOException ignored) {
                    // 关闭失败不影响测试结果
                }
            }));
            return pg;
        } catch (IOException e) {
            throw new IllegalStateException("内嵌 PostgreSQL 启动失败", e);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        // 测试用固定 JWT 密钥，避免依赖环境变量
        registry.add("app.jwt.secret", () -> "test-secret-key-for-integration-tests-32bytes!!");
        registry.add("app.upload.dir", () -> "target/test-uploads");
    }

    @AfterAll
    static void keepAlive() {
        // 集群由 JVM 关闭钩子统一关闭；这里保留方法以显式说明生命周期
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * JDK 自带的 HttpURLConnection 在「401 + 流式请求体」时会抛
     * "cannot retry due to server authentication, in streaming mode"，
     * 而登录失败用例正是要断言 401，因此统一切到 Apache HttpClient5。
     */
    @org.junit.jupiter.api.BeforeEach
    void useApacheHttpClient() {
        if (!(rest.getRestTemplate().getRequestFactory()
                instanceof org.springframework.http.client.HttpComponentsClientHttpRequestFactory)) {
            rest.getRestTemplate().setRequestFactory(
                    new org.springframework.http.client.HttpComponentsClientHttpRequestFactory());
        }
    }

    // ------------------------------------------------------------------ HTTP

    protected ResponseEntity<String> getJson(String path, String token) {
        return rest.exchange(path, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers(token)), String.class);
    }

    protected ResponseEntity<String> postJson(String path, Object body, String token) {
        HttpHeaders headers = headers(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }

    protected ResponseEntity<String> postMultipart(String path, String field, String fileName,
                                                   byte[] content, String token,
                                                   Map<String, String> extraFields) {
        HttpHeaders headers = headers(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add(field, new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        if (extraFields != null) {
            extraFields.forEach(form::add);
        }
        return rest.postForEntity(path, new HttpEntity<>(form, headers), String.class);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    /** 下载二进制（Excel / PDF 导出）。 */
    protected ResponseEntity<byte[]> getBytes(String path, String token) {
        HttpHeaders headers = headers(token);
        headers.setAccept(java.util.List.of(MediaType.ALL));
        return rest.exchange(path, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);
    }

    protected String login(String username, String password) {
        ResponseEntity<String> response = rest.postForEntity("/api/login",
                new HttpEntity<>(Map.of("username", username, "password", password), jsonHeaders()),
                String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("登录失败：" + response.getBody());
        }
        return json(response.getBody()).path("token").asText();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected JsonNode json(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (IOException e) {
            throw new IllegalStateException("响应不是合法 JSON：" + body, e);
        }
    }

    // ------------------------------------------------------------------ 造数

    /** 造一个独立材料 + 一个批次，返回 {itemId, batchId}。 */
    protected long[] createItemAndBatch(String codePrefix, double safeStock, double batchQty) {
        int seq = SEQ.incrementAndGet();
        String code = codePrefix + "-" + seq;
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM material_category ORDER BY id LIMIT 1", Long.class);

        Long itemId = jdbc.queryForObject("""
                INSERT INTO material_item (category_id, code, brand, model, spec, unit_price, unit, safe_stock, stock_qty)
                VALUES (?, ?, '联调品牌', ?, '联调规格', 10.00, '个', ?, ?)
                RETURNING id
                """, Long.class, categoryId, code, code, safeStock, batchQty);

        Long batchId = jdbc.queryForObject("""
                INSERT INTO stock_batch (item_id, batch_code, barcode, quantity, initial_qty,
                                         location, expire_date, supplier, status)
                VALUES (?, ?, ?, ?, ?, 'T-01', DATE '2030-01-01', '联调供应商', 'IN_STOCK')
                RETURNING id
                """, Long.class, itemId, "TB" + seq, "TBC" + seq, batchQty, batchQty);

        return new long[]{itemId, batchId};
    }

    /** 造一个独立用户，返回 {userId, username}。 */
    protected Object[] createUser(String role, Double monthlyLimit) {
        int seq = SEQ.incrementAndGet();
        String uid = "it_" + role + "_" + seq;
        Long id = jdbc.queryForObject("""
                INSERT INTO sys_user (ldap_uid, name, role, password_hash, monthly_limit, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                RETURNING id
                """, Long.class, uid, "测试" + role + seq, role,
                passwordEncoder.encode("123456"), monthlyLimit);
        return new Object[]{id, uid};
    }

    protected int countTransactions(long userId, String txnType) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_transaction WHERE user_id = ? AND txn_type = ?",
                Integer.class, userId, txnType);
        return value == null ? 0 : value;
    }

    protected double batchQuantity(long batchId) {
        Double value = jdbc.queryForObject(
                "SELECT quantity FROM stock_batch WHERE id = ?", Double.class, batchId);
        return value == null ? 0 : value;
    }

    protected double itemStock(long itemId) {
        Double value = jdbc.queryForObject(
                "SELECT stock_qty FROM material_item WHERE id = ?", Double.class, itemId);
        return value == null ? 0 : value;
    }

    protected String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
