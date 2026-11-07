# 材智通 · 智慧材料库数字化管理系统（全栈版）

依据需求文档《智慧材料库数字化管理系统》实现：**React + Vite + Ant Design** 前端，
**Spring Boot 3 + MyBatis-Plus + PostgreSQL 15 + Spring Security/JWT** 后端。

> 部署上线（Vercel + Render + 草料二维码）请看 **[Deploy_Guide.md](Deploy_Guide.md)**。
> 后端接口、建库脚本、联调实测记录见 **[backend/README.md](backend/README.md)**。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前端 | React 18、Vite 6、Ant Design 5、React Router 6、Recharts、qrcode.react |
| 后端 | Spring Boot 3.3、MyBatis-Plus 3.5、Spring Security + JWT（jjwt 0.12） |
| 数据库 | **PostgreSQL 15**（jsonb 字段，不能用 MySQL） |
| 导出 | Apache POI（xlsx）、OpenPDF（pdf，内置中文字体） |
| 部署 | 前端 Vercel、后端 Render（Docker + eclipse-temurin:17-jre-alpine） |
| CI | GitHub Actions：`npm ci && npm run build` + `mvn -B test` |

## 功能覆盖（MoSCoW）

| 优先级 | 页面 | 说明 |
| --- | --- | --- |
| Must | 材料类目 | 四级类目树（material_category.parent_id / safe_stock） |
| Must | 入库管理 | 批次号、条码、**真实可扫二维码**、打印标签、**Excel 批量导入** |
| Must | 扫码领用 | 扫码直达 → 自动选中批次；库存 + 学生月度限额校验 |
| Must | 用户权限 | 游客/学生/实验员/教师/采购/管理员（sys_user.role / ldap_uid） |
| Must | 库存预警 | 低于 safe_stock 自动写 stock_alert，补货后自动关闭 |
| Must | 统计导出 | **真实 xlsx / pdf 下载**（经费对应表、学期汇总、个人明细） |
| Should | 仪表盘 | 月度 TOP10、资金占比、库存健康度 |
| Should | 归还/报废 | 部分归还、报废扣减、损耗说明 + **损耗照片上传落库** |

## 快速开始（本地）

```bash
# 0. 准备 PostgreSQL 15 并建库（没装服务？见 backend/README.md 的「方案 B：便携版」）
psql -U postgres -c "CREATE DATABASE smart_materials;"

# 1. 启动后端（自动执行 schema.sql + data.sql）
cd backend
mvn -DskipTests package
java -jar target/smart-materials-server.jar   # http://localhost:8080/api/health

# 2. 启动前端
cd ..
npm install
npm run dev                                    # http://localhost:5173
```

> 若项目路径含中文（如 `D:\桌面\...`），`mvn spring-boot:run` 会因 classpath 编码问题报
> `ClassNotFoundException`，请改用上面的 `mvn package` + `java -jar`（原因与规避见 backend/README.md）。

种子账号（首次启动自动创建）：`admin` / `lab01` / `stu2024001` / `teacher01` / `buyer01`，密码 `123456`。

一键接口联调（24 项断言）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File backend\scripts\smoke-test.ps1
```

## 关键实现

- **领用出库**（`backend/.../service/StockService.java`）：`@Transactional` 内完成
  行级锁批次 → 校验库存/效期 → 校验学生月度限额 → 扣批次与台账库存 →
  写 `stock_transaction`（txn_type=OUT、project_no、单价金额快照）→ 低于 `safe_stock` 写 `stock_alert`。
  超额与库存不足都会整体回滚，**不留流水、不改库存**。
- **二维码链路**：入库页用 `qrcode.react` 生成
  `https://前端域名/login?redirect=/outbound&batchCode=当前批次号`；
  登录页解析参数，登录后带 `batchCode` 跳转；出库页读取参数自动选中批次。
- **批量导入**：`POST /api/batches/import` 解析 xlsx，整份文件全部合法才写库，否则返回逐行错误。
- **报表导出**：`GET /api/reports/export?format=xlsx|pdf` 由后端生成真实文件，前端 fetch → Blob 下载。
- **附件上传**：`POST /api/attachments` 落盘到 `UPLOAD_DIR`，地址写进 `stock_transaction.attachments`（jsonb）。
- **认证**：`POST /api/login` 返回 JWT，前端存 localStorage，所有请求带
  `Authorization: Bearer <token>`；401 自动登出并跳登录页。
- **Mock 已关闭**：`VITE_USE_MOCK=false`，全部页面走真实接口。

## 目录

```
backend/        Spring Boot 3 后端（Dockerfile、schema.sql、data.sql、scripts/smoke-test.ps1）
src/            React 前端（api/ auth.tsx config.ts pages/）
public/templates/  批量入库 Excel 模板（前端「下载模板」用）
deploy/         自建服务器方案（Nginx + docker-compose，备选）
docs/           文档与模板
preview/        零依赖静态演示页（不连后端）
.github/workflows/ci.yml   前端构建 + 后端测试
Deploy_Guide.md 完整部署指南
```

## 生产构建

```bash
npm run build                     # 前端 → dist/
cd backend && mvn clean package   # 后端 → target/smart-materials-server.jar
docker build -t smart-materials-server backend/
```

环境变量见 `.env.production`（前端）与 `backend/.env.example`（后端）。

> ⚠️ **`.env.production` 里的两个域名目前是占位示例**
> （`https://smart-materials-api.onrender.com/api`、`https://smart-materials.vercel.app`），
> 正式部署后必须替换，否则接口 404 / 二维码指向不存在的站点。文件里已加显著标注。

## 验证状态（2026-11-07）

### 已验证

| 项 | 结果 |
| --- | --- |
| PostgreSQL 15 启动 + `schema.sql` / `data.sql` 自动执行 | ✅ PG 15.13 全新库，零报错；JSONB、IDENTITY、外键顺序、`ON CONFLICT`、部分唯一索引全部正常 |
| 后端接口联调（24 项） | ✅ `backend/scripts/smoke-test.ps1` 全通过 |
| 超额领用 / 库存不足回滚 | ✅ 均 409，且不产生流水、库存不变 |
| 预警 OPEN → CLOSED | ✅ 出库跌破安全线写入 OPEN，补货/归还自动 CLOSED |
| 台账 = 批次之和 | ✅ 一致性 SQL 返回 0 行 |
| 批量导入 / 报表导出 / 附件上传 | ✅ xlsx 与 pdf 均为真实文件（PDF 中文可抽取），附件落盘并可静态访问 |
| `mvn test` | ✅ 9/9 通过（内嵌真实 PG15），JDK 17 与 JDK 26 各跑一次 |
| `npm run build` | ✅ tsc 无报错，dist 产出 |
| 前端 dev 服务 + 二维码 SPA 路由 | ✅ `/login?redirect=/outbound&batchCode=...` 返回 SPA 入口 |

### 未验证（环境不具备）

| 项 | 原因 |
| --- | --- |
| `docker build` / `docker run` | 联调机**没有安装 Docker**（也无 WSL 发行版）。Dockerfile 已做静态审查与优化（去掉 `dependency:go-offline`） |
| 镜像体积记录 | 同上 |
| 手机扫码真机链路 | 没有真机/摄像头，仅验证到 URL 生成 + SPA 路由 + 批次接口三层 |
| Render / Vercel 实际部署 | 需要你的账号与域名 |

### 仍需人工操作

1. 在装有 Docker 的机器上跑一次 `docker build -t smart-materials-server ./backend` 与
   `docker run -p 8080:8080 --env-file backend/.env smart-materials-server`，记录镜像大小。
2. 用手机扫一次入库页生成的二维码，确认「登录 → 自动选中批次 → 提交出库」全链路。
3. 替换 `.env.production` 中的两个占位域名，或在 Vercel 面板配置同名环境变量。
4. 推送到 GitHub 仓库，确认 `.github/workflows/ci.yml` 在 Actions 里跑通。
5. Render 建 PostgreSQL + Web Service（可用 `render.yaml` Blueprint），Vercel 导入前端仓库。
6. 生产环境替换 `JWT_SECRET`、`SEED_PASSWORD`，并按需把 `UPLOAD_DIR` 指到持久盘。
