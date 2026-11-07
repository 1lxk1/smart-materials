# 材智通 · 智慧材料库 —— 后端服务

Spring Boot 3.3.5 + MyBatis-Plus 3.5.9 + PostgreSQL 15 + Spring Security/JWT。

> 全量接口联调结果见本文末尾 **[联调记录](#联调记录实测)**（2026-11-07，PG 15.13，JDK 17 与 JDK 26 双运行时验证）。

## 目录结构

```
backend/
├── pom.xml
├── Dockerfile                     # Render 部署用（eclipse-temurin:17-jre-alpine）
├── .env.example                   # 环境变量模板（复制为 .env 供 docker run 使用）
├── scripts/
│   └── smoke-test.ps1             # 24 项接口冒烟脚本（UTF-8 with BOM）
└── src/
    ├── main/java/com/smartmaterials/
    │   ├── common/                # 业务异常 + 全局异常处理（含 400/404/405 归一）
    │   ├── config/                # Security、CORS、静态资源映射、启动数据初始化
    │   ├── controller/            # REST 接口（含 AttachmentController）
    │   ├── dto/ vo/               # 请求 / 响应模型（含 ImportResult）
    │   ├── entity/                # 5 张主表 + 预警表实体
    │   ├── mapper/                # MyBatis-Plus Mapper（含 FOR UPDATE 行锁）
    │   ├── security/              # JWT 签发与过滤器
    │   ├── service/               # StockService / BatchImportService / ReportExportService / FileStorageService
    │   └── support/               # JSONB TypeHandler、时区
    ├── main/resources/
    │   ├── application.yml
    │   ├── schema.sql             # PG 15 建表（IF NOT EXISTS，可重复执行）
    │   └── data.sql               # 类目 / 材料 / 批次种子数据
    └── test/java/com/smartmaterials/
        ├── support/IntegrationTestBase.java   # 内嵌 PG15 测试基类
        ├── StockFlowIntegrationTest.java      # 登录 / 库存 / 限额 / 预警
        └── FeatureIntegrationTest.java        # 导入 / 导出 / 附件
```

## 本地启动

### 方案 A：本机已有 PostgreSQL 15

```bash
psql -U postgres -c "CREATE DATABASE smart_materials;"
cd backend
mvn spring-boot:run          # 首次启动自动执行 schema.sql + data.sql
curl http://localhost:8080/api/health
```

默认连接 `localhost:5432/smart_materials`，账号 `postgres/postgres`，可用环境变量覆盖
（`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD`）。

### 方案 B：Windows 无 Docker，用 PostgreSQL 15 便携版（本次联调实际采用）

本机既没有 Docker 也没有装 PG，用的是 EDB 官方 Windows 二进制包，**不需要管理员权限、不装服务**：

```powershell
# 1. 下载并解压 PG 15 Windows 二进制包（约 300MB）
curl.exe -L -o pg15.zip https://get.enterprisedb.com/postgresql/postgresql-15.13-1-windows-x64-binaries.zip
Expand-Archive pg15.zip -DestinationPath .tooling\pg

# 2. 初始化数据目录（密码文件里只写 postgres）
#    ⚠️ 关键坑：数据目录路径不能含中文！本项目路径是 D:\桌面\...，
#       直接 initdb 会报 invalid byte sequence for encoding "UTF8": 0xd7 0xc0
#       （"桌面" 的 GBK 字节被当成 UTF-8 写入）。
#       解决：建一个纯 ASCII 的目录联接，用联接路径初始化。
cmd /c mklink /J D:\sm-pgroot "D:\桌面\smart-materials\.tooling"
$bin = 'D:\sm-pgroot\pg\pgsql\bin'
& "$bin\initdb.exe" -D D:\sm-pgroot\pgdata -U postgres --pwfile=D:\sm-pgroot\pgpw.txt `
    -E UTF8 --locale=C --auth-host=scram-sha-256 --auth-local=trust

# 3. 启动（必须用 Start-Process 分离启动，见下方「两个 Windows 坑」）
Start-Process -FilePath "$bin\postgres.exe" `
  -ArgumentList '-D','D:\sm-pgroot\pgdata','-p','5432' `
  -RedirectStandardOutput D:\sm-pgroot\pg.out.log `
  -RedirectStandardError  D:\sm-pgroot\pg.err.log -WindowStyle Hidden

# 4. 建库
$env:PGPASSWORD='postgres'
& "$bin\createdb.exe" -h 127.0.0.1 -p 5432 -U postgres -w smart_materials

# 5. 停止
& "$bin\pg_ctl.exe" -D D:\sm-pgroot\pgdata stop
```

**两个 Windows 坑（已踩过，方案已验证）**

| 现象 | 原因 | 解法 |
| --- | --- | --- |
| `initdb: invalid byte sequence for encoding "UTF8": 0xd7 0xc0` | 数据目录路径含中文（`D:\桌面\...`），路径字节按 GBK 写入 UTF8 库 | 用 `mklink /J` 建 ASCII 路径联接后再 initdb |
| `pg_ctl start` 执行后 PowerShell 一直挂住 | postgres 继承了 pwsh 的 stdout 管道，父进程等 EOF | 用 `Start-Process` + `-RedirectStandard*` 分离启动，或用 `pg_ctl -l 日志 start` 并在独立进程里执行 |

### 启动后端（推荐打 jar，避免中文路径问题）

```powershell
mvn -f backend\pom.xml -DskipTests package
Start-Process -FilePath "$PWD\.tooling\jdk-17\bin\java.exe" `
  -ArgumentList '-jar',"$PWD\backend\target\smart-materials-server.jar" `
  -RedirectStandardOutput "$PWD\.tooling\boot.out.log" `
  -RedirectStandardError  "$PWD\.tooling\boot.err.log" -WindowStyle Hidden
```

> ⚠️ 在中文路径下 `mvn spring-boot:run` 会报
> `ClassNotFoundException: com.smartmaterials.SmartMaterialsApplication`
> （插件拼接的 classpath 在 GBK/UTF-8 之间错位），而类文件其实存在。
> 规避方式：`mvn -DskipTests package` 后 `java -jar`，或把项目放到纯 ASCII 路径再 `spring-boot:run`。

## 种子账号（首次启动自动创建）

| 账号 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| admin | 123456 | admin | 系统管理员，可维护类目 |
| lab01 | 123456 | lab | 实验员，入库 / 归还 / 报废 |
| stu2024001 | 123456 | student | 学生，月度限额 20 |
| teacher01 | 123456 | teacher | 教师 / PI |
| buyer01 | 123456 | buyer | 采购员 |

初始密码由 `SEED_PASSWORD` 控制（仅首次创建时使用）。

## 数据库表

| 表 | 关键字段 |
| --- | --- |
| material_category | parent_id, safe_stock, level, extra(jsonb) |
| material_item | unit_price, currency, safe_stock, stock_qty, specs(jsonb) |
| stock_batch | batch_code, barcode, quantity, expire_date, location |
| stock_transaction | txn_type, project_no, usage_desc, amount, attachments(jsonb) |
| sys_user | role, ldap_uid, monthly_limit, password_hash |
| stock_alert | item_id, current_qty, safe_stock, level, status |

JSONB 列通过 `support/JsonbTypeHandler` / `JsonbListTypeHandler` 映射，参数必须用 `Types.OTHER`
写入，否则 PG 会按 varchar 处理并报类型错误。

## 核心接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /api/health | 健康检查（免登录） |
| POST | /api/login | 登录，返回 JWT |
| GET | /api/me | 当前用户 + 权限 + 月度额度 |
| GET | /api/dashboard | 仪表盘聚合 |
| GET | /api/categories | 类目树 |
| GET | /api/materials | 材料台账 |
| GET | /api/batches | 批次列表（支持 keyword / itemId） |
| GET | /api/batches/{batchCode} | 扫码查询批次 |
| POST | /api/batches | 入库，生成批次号 + 条码 |
| **POST** | **/api/batches/import** | **Excel 批量入库（multipart file）** |
| POST | /api/stock/outbound | 领用出库（@Transactional） |
| POST | /api/stock/return-scrap | 归还 / 报废 |
| GET | /api/stock/quota | 本月领用额度 |
| GET | /api/transactions | 流水 |
| GET | /api/alerts | 未关闭预警 |
| GET | /api/alerts/history | 全部预警（含已关闭） |
| **GET** | **/api/reports/export** | **导出 xlsx / pdf（format=xlsx\|pdf）** |
| **POST** | **/api/attachments** | **上传损耗照片，返回 /uploads/... 地址** |
| GET | /api/users | 用户列表 |

除 `/api/login`、`/api/health`、`/actuator/health`、`/uploads/**` 外，都需要
`Authorization: Bearer <token>`。

### 批量导入（POST /api/batches/import）

- 表单字段 `file`，支持 `.xlsx` / `.xls`，单次 ≤ 500 行。
- 第 1 行为表头，列名中英文均可：
  **必需**「材料代码」「数量」；可选「货架位」「有效期（yyyy-MM-dd）」「供应商」「条码」「备注」。
- 语义：**整份文件全部合法才写库**；任意一行不合法则返回逐行错误且不写入任何数据。
- 返回：`{ total, imported, batches:[{row,itemCode,batchCode,qty}], errors:[{row,message}] }`
- 前端入库页可直接「下载模板」：`public/templates/materials-inbound-template.xlsx`。

### 报表导出（GET /api/reports/export）

- 参数：`format=xlsx|pdf`（默认 xlsx）、`type=OUT|RETURN|SCRAP|IN`、`projectNo`、`from`、`to`（yyyy-MM-dd）。
- Excel 由 Apache POI 生成（表头样式、金额格式、冻结首行、合计行）。
- PDF 由 OpenPDF 生成，使用内置 CJK 字体 `STSong-Light` + `UniGB-UCS2-H`，
  **不依赖操作系统字体**，Alpine 容器里也能正确输出中文。
- 响应带 `Content-Disposition: attachment; filename*=UTF-8''...`，前端用 fetch + Blob 下载。

### 附件上传（POST /api/attachments）

- 表单字段 `file` + 可选 `category`（默认 `scrap`）。
- 允许 jpg/jpeg/png/webp/gif/bmp/pdf，单个 ≤ 10MB（`spring.servlet.multipart.max-file-size`）。
- 落盘规则：`${UPLOAD_DIR}/类别/yyyyMM/uuid.ext`，返回 `{url,name,size}`。
- `url` 随归还/报废请求的 `photos` 数组提交，最终写入 `stock_transaction.attachments`（jsonb 数组）。
- 静态访问：`WebConfig` 把 `/uploads/**` 映射到该目录。

## 领用出库事务逻辑

`StockService#outbound` 标注 `@Transactional(rollbackFor = Exception.class)`：

1. `SELECT ... FOR UPDATE` 锁定批次行，防并发超卖；
2. 校验批次库存与有效期；
3. 学生角色校验月度限额：`本月 OUT 流水合计 + 本次数量 ≤ sys_user.monthly_limit`；
4. 扣减 `stock_batch.quantity` 与 `material_item.stock_qty`（两条 UPDATE 都带 `>= 0` 守卫）；
5. 写入 `stock_transaction`（txn_type=OUT，project_no、usage_desc、单价与金额快照）；
6. 重新读取总库存，低于 `material_item.safe_stock` 时写入 `stock_alert`，恢复后自动置 `CLOSED`。

任一步抛异常则整个事务回滚——**超额领用与库存不足都不会留下流水、也不会改库存**（见联调记录 B/C 两条）。

## 集成测试

```bash
cd backend
mvn test        # 9 个用例，约 20s
```

- 使用 **zonky embedded-postgres**（真实 PostgreSQL 15.8 二进制，构建时从 Maven 依赖解压），
  **不需要本机数据库、不需要 Docker**；`schema.sql` / `data.sql` 由测试上下文真实执行，
  因此同时回归了建表脚本的 PG 语法。
- Windows / Linux 二进制都在 `pom.xml` 的 test 依赖里声明，CI（ubuntu）与本地（Windows）都能跑。
- 覆盖：
  1. 登录成功 / 失败（401）；
  2. 领用超批次库存 → 409 且批次、台账、流水全部回滚；
  3. 学生超月度限额 → 409 且只留下第一次的流水；
  4. 出库后 `stock_transaction` 有 OUT 记录、`stock_alert` OPEN 且数量/级别正确；
  5. 归还补足后预警自动 CLOSED、台账 = 批次之和；
  6. Excel 批量导入成功 / 含非法行整体不写库 / 非 Excel 被拒；
  7. 导出 xlsx 是真实 zip 工作簿、pdf 是真实 PDF；
  8. 附件上传落盘、可静态访问、并写进 `attachments` jsonb。

> 注：本地若在中文路径下运行，测试同样正常（内嵌 PG 解压到 `%TEMP%`，路径为 ASCII）。

## 联调记录（实测）

环境：Windows 11 + PowerShell 5.1，PostgreSQL **15.13**（EDB 便携版，端口 5432，全新库），
JDK **17.0.21** 运行（另在 JDK 26 上复测通过），后端 `java -jar` 启动，端口 8080。

一键复跑：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File backend\scripts\smoke-test.ps1
# → 通过 24 项，失败 0 项
```

### 启动

```text
INFO c.z.hikari.HikariDataSource : HikariPool-1 - Start completed.
INFO o.s.b.w.e.tomcat.TomcatWebServer : Tomcat started on port 8080 (http)
INFO c.s.SmartMaterialsApplication : Started SmartMaterialsApplication in 12.875 seconds
INFO c.s.config.DataSeeder : 已创建种子用户 admin / admin（初始密码 123456）
INFO c.s.config.DataSeeder : 已写入 5 条示例流水
INFO c.s.config.DataSeeder : 启动巡检：4 种材料低于安全库存，已生成预警
```

`schema.sql` + `data.sql` 首次执行**零报错**（JSONB 默认值、`GENERATED BY DEFAULT AS IDENTITY`、
外键顺序、`ON CONFLICT (code/batch_code) DO NOTHING` 均正常；`uk_alert_open_item` 部分唯一索引生效）。

### 接口清单

| # | 命令 | 实测结果 |
| --- | --- | --- |
| 1 | `GET /api/health` | `200 {"status":"UP","service":"smart-materials-server"}` |
| 2 | `POST /api/login {"username":"admin","password":"123456"}` | `200`，token 224 字符，`tokenType=Bearer`，`expiresInMinutes=720`，`role=admin` |
| 3 | `POST /api/login`（错误密码） | `401 {"message":"账号或密码错误"}` |
| 4 | `GET /api/me`（Bearer） | `200`，含 user + permissions + quota |
| 5 | `GET /api/dashboard` | `200 {"stats":{"skuCount":6,"lowStock":4,"stockValue":6034.50,...}}` |
| 6 | `GET /api/materials` | `200`，6 条，`categoryPath` 与 `specs`(jsonb) 正常 |
| 7 | `GET /api/batches` | `200`，6 条，含 `batchCode`/`barcode`/`expireDate` |
| 8 | `GET /api/alerts` | `200`，4 条 OPEN（TIP-200/SENSOR/GLV 等） |
| 9 | `GET /api/me`（无 token） | `401 {"message":"未登录或登录已过期，请重新登录"}` |
| 10 | `POST /api/batches`（入库 item=HCL-AR，qty=10） | `200`，`batchCode=IN20261107001`（格式 `INyyyyMMdd###`），`itemStock=15` |
| 11 | `GET /api/stock/quota`（stu2024001） | `200 {"used":6.000,"limit":20.000,"remaining":14.000}` |
| 12 | 领用 10（新批次） | `200`，`monthlyUsed=16`，写入 OUT 流水 |
| 13 | **再领用 10（批次库存充足）** | **`409 超出个人月度限额：本月已领用 16，限额 20，本次申请 10，最多还能领用 4`** |
| 14 | **领用 1（批次已空）** | **`409 库存不足：批次 IN20261107001 当前仅剩 0，本次申请 1`** |
| 15 | 失败两次后查库 | `stock_transaction` **无新增**；批次 0、台账 5 保持不变（回滚生效） |
| 16 | 领用后查预警 | `OPEN`，`current_qty=5`、`safe_stock=8`、`level=warning` |
| 17 | 补货 15 后查预警 | 该材料预警自动变 `CLOSED`（`closed_at` 有值），`/api/alerts` 不再返回 |
| 18 | `POST /api/stock/return-scrap` RETURN 3 | `200`，`txnType=RETURN`，批次剩余 3 |
| 19 | `POST /api/stock/return-scrap` SCRAP 1 | `200`，`txnType=SCRAP`，批次剩余 2 |
| 20 | `POST /api/batches/import`（2 行 xlsx） | `200 {"total":2,"imported":2,...}`，批次 `IN20261107004` / `IN20261107005` |
| 21 | 同上（含不存在材料 + 数量 ≤0） | `200 {"imported":0,"errors":[{"row":3,...},{"row":4,...}]}`，**一条未写** |
| 22 | `GET /api/reports/export?format=xlsx` | `200`，4127 字节，文件头 `PK`，`Content-Disposition` 带 UTF-8 文件名 |
| 23 | `GET /api/reports/export?format=pdf` | `200`，2493 字节，文件头 `%PDF-1.5`，提取文本为正确中文 |
| 24 | `POST /api/attachments`（png） | `200 {"url":"/uploads/scrap/202611/....png"}`；GET 该 url → `200 image/png` |
| 25 | 异常 JSON 体 | `400 {"message":"请求体不是合法 JSON，请检查 Content-Type 与 JSON 格式"}` |
| 26 | 不存在的路由 `DELETE /api/users/1` | `404 {"message":"接口不存在: DELETE /api/users/1"}`（修复前是 500） |

PDF 文本抽取实测（`pypdf`）：

```text
耗材领用与经费对应表
类型：OUT    项目号：全部    区间：不限 ~ 不限    导出时间：2026-11-07 16:20
共 3 条，合计金额 ¥522.00    制表：智慧材料库
材料代码 材料名称 单价 数量 总价 使用人 项目号 用途 时间
MAT-HCL-AR-001 国药集团 HCl-AR · 500mL / AR 28.50 10 285.00 王同学 PRJ-2025-012 联调领用 ...
```

### 数据一致性校验

```sql
-- 要求返回 0 行：台账库存必须等于该材料所有批次之和
SELECT mi.id, mi.code, mi.stock_qty, COALESCE(SUM(b.quantity), 0) AS batch_sum
FROM material_item mi
LEFT JOIN stock_batch b ON b.item_id = mi.id AND b.deleted = 0
WHERE mi.deleted = 0
GROUP BY mi.id, mi.code, mi.stock_qty
HAVING mi.stock_qty <> COALESCE(SUM(b.quantity), 0);
```

实测（全部操作跑完之后）：**0 行**；逐材料对照 6/6 全部相等
（HCL-AR 7=7、TIP-200 52=52、DISH-90 80=80 …）。

### 本次联调发现并修复的问题

| # | 问题 | 修法 |
| --- | --- | --- |
| 1 | 畸形 JSON 请求体返回 **500**（`JSON parse error`） | 新增 `HttpMessageNotReadableException` → 400 |
| 2 | 不存在的路由落到静态资源处理器，返回 **500** `No static resource` | 新增 `NoResourceFoundException` → 404，另补 405 / 参数类型错误 400 |
| 3 | 批量导入返回的 `batchCode` 为 `null` | 改取 `OperationResult.batch().getBatchCode()` |
| 4 | 导出表里时间是 `2026-11-07T16:15:43.237066`（带 T 和微秒） | 统一格式化为 `yyyy-MM-dd HH:mm` |
| 5 | 集成测试断言 401 时 `HttpURLConnection` 报 `cannot retry due to server authentication` | 测试改用 Apache HttpClient5 |
| 6 | `initdb` 在中文路径下报 UTF8 字节错误 | ASCII 目录联接后再 initdb（见方案 B） |
| 7 | 中文路径下 `mvn spring-boot:run` 找不到主类 | 改 `java -jar` 运行 |

## 打包

```bash
mvn clean package -DskipTests
# 产物：target/smart-materials-server.jar
# 实测 54.6MB / 74 个依赖（POI ~12MB、OpenPDF 含中文字体 ~5.7MB、Tomcat 3.4MB）
```

## Docker

```bash
docker build -t smart-materials-server ./backend
docker run -p 8080:8080 --env-file backend/.env smart-materials-server
curl http://localhost:8080/api/health
```

- 构建阶段用 `maven:3.9-eclipse-temurin-17`，运行阶段固定 `eclipse-temurin:17-jre-alpine`。
- 已把易失败的 `dependency:go-offline` 换成 `dependency:resolve -DincludeScope=runtime`：
  `go-offline` 会把所有插件依赖全下一遍，在 Render 免费构建机上常因超时或个别构件 404 直接失败。
- 构建命令加 `-Dmaven.test.skip=true`：镜像里不跑测试，连测试源码都不编译，
  顺带避免下载内嵌 PostgreSQL 测试二进制（每个约 15MB）。
- 容器内 `UPLOAD_DIR=/app/uploads`，镜像构建时已 `mkdir` 并 `chown app:app`。
- 预计镜像体积 **约 230MB**（alpine JRE 17 约 175MB + jar 55MB），未实测。

> ⚠️ **本次未能实际验证 `docker build` / `docker run`**：联调机器上没有安装 Docker（也没有 WSL 发行版），
> 详见根目录 README 的「验证状态」清单。Dockerfile 已按静态审查优化，请在装有 Docker 的机器上补跑一次。

## 已知限制

| 项 | 现状 | 后续 |
| --- | --- | --- |
| LDAP | **当前版本不做 LDAP 绑定校验**。`app.ldap.enabled` / `VITE_LDAP_ENABLED` 只是开关，登录走 `sys_user.password_hash`（BCrypt）本地密码。`sys_user.ldap_uid` 字段已预留。 | 二期接入 LDAP bind / 统一身份认证 |
| 附件持久化 | 存本地磁盘 `${UPLOAD_DIR}`。Render 免费实例文件系统是临时的，重启即丢。 | 换 S3 / 阿里云 OSS，接口契约不变 |
| 报表上限 | 单次导出最多 500 行（`ReportService` 里 `LIMIT 500`） | 大数据量改分页或流式写出 |
| 预警并发 | 依赖 `uk_alert_open_item` 部分唯一索引保证同一材料只有一条 OPEN | 高并发下可加 `ON CONFLICT` 处理 |
