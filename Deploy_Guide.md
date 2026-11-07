# 材智通 · 智慧材料库 —— 部署指南

> 技术栈：React 18 + Vite 6 + Ant Design 5（前端） / Spring Boot 3 + MyBatis-Plus + PostgreSQL 15 + Spring Security JWT（后端）
> 目标环境：前端 **Vercel**、后端 **Render（Docker）**、数据库 **Render PostgreSQL 15**

---

## 0. 交付物与目录结构

```
smart-materials/
├── backend/                     # Spring Boot 3 后端（Task 1）
│   ├── pom.xml
│   ├── Dockerfile               # eclipse-temurin:17-jre-alpine
│   ├── .env.example             # 环境变量模板
│   ├── README.md
│   └── src/main/
│       ├── java/com/smartmaterials/
│       │   ├── config/          # CORS、Security、启动数据初始化
│       │   ├── controller/      # /api/*
│       │   ├── service/         # StockService（领用出库核心事务）
│       │   ├── entity/ mapper/  # 5 张主表 + 预警表
│       │   └── security/        # JWT 签发与过滤器
│       └── resources/
│           ├── application.yml
│           ├── schema.sql       # PostgreSQL 15 建表
│           └── data.sql         # 类目 / 材料 / 批次种子数据
├── src/                         # React 前端（Task 2）
│   ├── api/http.ts              # fetch 客户端（JWT、错误统一处理）
│   ├── api/index.ts             # 全部后端接口封装
│   ├── auth.tsx                 # 真实登录（POST /api/login）
│   ├── config.ts                # 环境变量 + 二维码 URL 生成
│   ├── mock/api.ts              # 已替换为真实请求（兼容层）
│   └── pages/                   # Login / Inbound / Outbound 等
├── vercel.json                  # Vercel SPA 重写配置
├── render.yaml                  # Render Blueprint（可选一键部署）
└── Deploy_Guide.md              # 本文件
```

---

## 1. 本地联调（上线前先跑通）

### 1.1 准备 PostgreSQL 15

```bash
# 建库（名字要与 DB_NAME 一致）
psql -U postgres -c "CREATE DATABASE smart_materials;"
```

后端启动时会自动执行 `schema.sql` + `data.sql`（脚本幂等），无需手工建表。

### 1.2 启动后端

```bash
cd backend
mvn spring-boot:run
# 或者
mvn clean package -DskipTests && java -jar target/smart-materials-server.jar
```

健康检查：

```bash
curl http://localhost:8080/api/health
# {"status":"UP","service":"smart-materials-server",...}
```

首次启动会自动创建种子账号（密码 `123456`）：

| 账号 | 角色 | 说明 |
| --- | --- | --- |
| admin | 管理员 | 维护类目 |
| lab01 | 实验员 | 入库 / 归还 / 报废 |
| stu2024001 | 学生 | 月度限额 20 |
| teacher01 | 教师 | 审批 |
| buyer01 | 采购员 | 预警催办 |

### 1.3 启动前端

```bash
npm install
npm run dev      # http://localhost:5173
```

`.env.development` 已经把接口指向 `http://localhost:8080/api`，Mock 已关闭（`VITE_USE_MOCK=false`）。

---

## 2. 打包并推送到 GitHub

### 2.1 本地打包自检

```bash
# 前端
npm run build            # 产物在 dist/，同时执行 tsc 类型检查

# 后端
cd backend
mvn clean package -DskipTests
# 产物：target/smart-materials-server.jar
```

### 2.2 初始化仓库并推送

```bash
# 在项目根目录
git init
git add .
git commit -m "feat: 智慧材料库全栈版（Spring Boot 3 + PG15 + React）"

# 关联远程仓库（先在 GitHub 上建一个空仓库）
git branch -M main
git remote add origin https://github.com/<你的用户名>/smart-materials.git
git push -u origin main
```

### 2.3 提交前确认这些**没有**被提交

`.gitignore` 已处理，重点检查：

| 禁止提交 | 原因 |
| --- | --- |
| `node_modules/`、`dist/` | 体积大，平台会重新安装/构建 |
| `.env`、`.env.local` | 含本机地址与密钥 |
| `backend/target/`、`*.jar` | 构建产物 |
| `.tooling/` | 本机 Maven 等工具 |
| 任何真实的 `JWT_SECRET`、数据库密码 | 泄露风险 |

```bash
git status --ignored | head -50   # 自查
```

> `.env.production` **可以**提交，但它只放占位域名；真实域名由 Vercel 环境变量覆盖。

---

## 3. 后端部署到 Render

### 3.1 先创建 PostgreSQL 15 数据库

Render 控制台 → **New +** → **Postgres**：

| 配置项 | 建议值 |
| --- | --- |
| Name | `smart-materials-db` |
| Database | `smart_materials` |
| User | `sm_user` |
| Region | Singapore（与后端、Vercel 尽量同区） |
| PostgreSQL Version | **15** |
| Plan | Free |

创建完成后，在数据库详情页复制三样东西：

- **Hostname**（内网地址，如 `dpg-xxxx-a.singapore-postgres.render.com`）
- **Port**（通常 `5432`）
- **Database / Username / Password**

> Render 只提供 PostgreSQL，正好满足本项目使用 JSONB 字段的要求（MySQL 无法存 JSONB）。

### 3.2 部署后端（方式 A：Blueprint 一键）

仓库根目录已提供 `render.yaml`：

Render → **New +** → **Blueprint** → 选择仓库 → Apply。

它会自动创建「PostgreSQL 15 + Docker Web Service」，并把数据库连接注入环境变量。
其中 `CORS_ALLOWED_ORIGINS`、`SEED_PASSWORD` 需要部署后在面板里补填。

### 3.3 部署后端（方式 B：手动创建，推荐初学者）

Render → **New +** → **Web Service** → 连接 GitHub 仓库，按下表填写：

| 配置项 | 值 |
| --- | --- |
| Name | `smart-materials-api` |
| Language / Runtime | **Docker** |
| Root Directory | `backend` |
| Dockerfile Path | `./Dockerfile` |
| Health Check Path | `/api/health` |
| Plan | Free |
| Region | Singapore |

> Dockerfile 运行时基础镜像固定为 `eclipse-temurin:17-jre-alpine`（多阶段构建：Maven 编译 → JRE 运行）。

### 3.4 必须填写的环境变量

在 **Environment** 页逐条添加（`backend/.env.example` 也有同样一份）：

| 变量名 | 示例值 | 说明 |
| --- | --- | --- |
| `DB_HOST` | `dpg-xxxx-a.singapore-postgres.render.com` | 数据库 Hostname（内网） |
| `DB_PORT` | `5432` | 端口 |
| `DB_NAME` | `smart_materials` | 库名 |
| `DB_USER` | `sm_user` | 用户名 |
| `DB_PASSWORD` | `******` | 创建数据库时生成的密码 |
| `DB_SSLMODE` | `prefer` | 内网用 `prefer`；外部连接用 `require` |
| `JWT_SECRET` | 32 位以上随机串 | **必改**，例如 `openssl rand -base64 48` |
| `JWT_EXPIRE_MINUTES` | `720` | Token 有效期（分钟） |
| `CORS_ALLOWED_ORIGINS` | `https://xxx.vercel.app` | 前端域名，多个用逗号分隔；调试期可先填 `*` |
| `SQL_INIT_MODE` | `always` | 每次启动执行建表/种子脚本；稳定后可改 `never` |
| `SEED_DATA` | `true` | 是否写入演示数据 |
| `SEED_PASSWORD` | `123456` | 种子账号初始密码，**建议修改** |
| `PORT` | Render 自动注入 | 不需要手动填 |

**另一种连接方式**：如果只想贴 Render 的 Database URL，可设置

```
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/smart_materials?sslmode=require
```

它的优先级高于 `DB_*` 拆分变量。

### 3.5 首次部署验证

```bash
# 1. 健康检查
curl https://smart-materials-api.onrender.com/api/health

# 2. 登录拿 token
curl -X POST https://smart-materials-api.onrender.com/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
# 返回 { token, user, permissions }

# 3. 带 token 访问业务接口
curl https://smart-materials-api.onrender.com/api/materials \
  -H "Authorization: Bearer <上一步的 token>"
```

Render 日志里应能看到：

```
Executing SQL script from classpath resource [schema.sql]
Executing SQL script from classpath resource [data.sql]
已创建种子用户 admin / admin（初始密码 123456）
Tomcat started on port 8080
```

> **免费实例会休眠**：15 分钟无请求后进入休眠，下一次请求需要 30–60 秒冷启动。
> 前端已把请求超时设为 60 秒，并会提示“后端可能正在冷启动”。

---

## 4. 前端部署到 Vercel

### 4.1 导入项目

Vercel → **Add New…** → **Project** → 选择同一个 GitHub 仓库。

| 配置项 | 值 |
| --- | --- |
| Framework Preset | Vite |
| Root Directory | `./`（留空） |
| Build Command | `npm run build` |
| Output Directory | `dist` |
| Install Command | `npm install` |
| Node.js Version | 20.x 或以上 |

### 4.2 环境变量（Project → Settings → Environment Variables）

| 变量名 | 值 | 说明 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `https://你的后端.onrender.com/api` | **必须**，Render 后端地址，结尾带 `/api` |
| `VITE_PUBLIC_BASE_URL` | `https://你的项目.vercel.app` | **必须**，二维码里写死的前端域名，不能带结尾 `/` |
| `VITE_USE_MOCK` | `false` | **必须**，关闭 Mock |
| `VITE_APP_TITLE` | `材智通·智慧材料库` | 可选 |
| `VITE_LDAP_ENABLED` | `false` | 可选（仅影响登录页文案，见 8.5） |

> ⚠️ **仓库里的 `.env.production` 目前是两个占位示例域名**
> （`smart-materials-api.onrender.com` / `smart-materials.vercel.app`），并不属于你。
> 部署前必须在 Vercel 面板按上表配好真实值并 Redeploy，否则会出现：
> 接口 404 / CORS 报错；批次二维码指向一个不存在的站点，扫码打不开。

三个环境都建议勾选（Production / Preview / Development）。

> ⚠️ Vite 环境变量是**构建时**注入的，改完环境变量必须 **Redeploy** 才生效。
> 平台环境变量优先级高于仓库里的 `.env.production`。

### 4.3 SPA 路由

仓库根目录的 `vercel.json` 已配置：

```json
{ "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }] }
```

没有它，直接打开 `/login?redirect=/outbound&batchCode=xxx` 或刷新 `/outbound` 会 404。

### 4.4 回填 CORS

前端拿到域名后，回到 Render 后端把 `CORS_ALLOWED_ORIGINS` 改成该域名：

```
https://你的项目.vercel.app
```

保存后 Render 会自动重启服务。多域名（如自定义域名）用英文逗号分隔。

### 4.5 验证

1. 打开 `https://你的项目.vercel.app/login`
2. 用 `admin / 123456` 登录
3. 仪表盘出现数据（说明 `/api/dashboard` 打通）
4. 浏览器 F12 → Network 里请求带 `Authorization: Bearer ...`

---

## 5. 用草料二维码生成系统入口与批次标签

### 5.1 系统入口二维码（一张贴门口）

1. 打开 [草料二维码](https://cli.im/) → 网址码。
2. 输入系统入口地址：

   ```
   https://你的项目.vercel.app/login
   ```

3. 生成后下载 PNG，打印贴在仓库/实验室门口。扫码 → 登录页 → 登录后进仪表盘。

### 5.2 批次标签二维码（每批一张）

**方式一：系统内直接生成（推荐，已内置）**

入库成功后，页面右侧「电子标签预览」会生成真实二维码，内容固定为：

```
https://你的项目.vercel.app/login?redirect=/outbound&batchCode=IN20250301001
```

即：

```
{VITE_PUBLIC_BASE_URL}/login?redirect=/outbound&batchCode={当前批次号}
```

点「打印标签」即可直接出纸质标签；点「下载 PNG」可保存图片。
（前提：Vercel 上的 `VITE_PUBLIC_BASE_URL` 必须是正式域名，否则二维码会指到 localhost。）

**方式二：草料批量生码**

1. 草料 → **批量生码** → 选「网址码」。
2. 准备 Excel，第一列表头写 `批次号`，逐行填批次号（可从后端导出）：

   ```sql
   -- 在 Render PostgreSQL 控制台执行
   SELECT batch_code FROM stock_batch ORDER BY batch_code;
   ```

3. 在草料的网址模板里填：

   ```
   https://你的项目.vercel.app/login?redirect=/outbound&batchCode={批次号}
   ```

4. 生成后批量下载/打印，贴到对应物料包装或货架上。

### 5.3 扫码后的完整链路

```
扫码
 → https://前端域名/login?redirect=/outbound&batchCode=IN20250301001
 → 登录页识别到 batchCode，提示“已识别扫码批次”
 → 输入账号密码，POST /api/login 拿到 JWT
 → 自动跳转 /outbound?batchCode=IN20250301001
 → 出库页读取 URL 参数，自动选中该批次
 → 填写数量/项目号/用途，提交 POST /api/stock/outbound
 → 后端事务：校验库存 → 校验学生月度限额 → 扣库存 → 写流水 → 低于安全库存写预警
```

---

## 6. 验收清单

- [ ] `GET /api/health` 返回 `status: UP`
- [ ] `POST /api/login`（admin/123456）返回 JWT
- [ ] 前端登录成功，仪表盘有真实数据
- [ ] 入库一次，批次号形如 `IN20260907001`，二维码可被手机识别
- [ ] 手机扫码 → 登录 → 自动跳到领用页且批次已选中
- [ ] 学生账号 `stu2024001` 领用超过 20 件时被拦截并提示「超出个人月度限额」
- [ ] 领用后材料总库存低于安全库存时，`/api/alerts` 出现预警记录；补货后自动 `CLOSED`
- [ ] 领用数量超过批次库存时被拦截并提示「库存不足」，且流水表**没有**新增记录
- [ ] 入库页「批量导入（Excel）」能下载模板并成功导入，错误行会逐行提示且不写入
- [ ] 报表页「导出 Excel / PDF」能下载真实文件（不是 CSV，也不是打印预览）
- [ ] 归还/报废页上传损耗照片后，`stock_transaction.attachments` 里有对应的 `/uploads/...` 地址
- [ ] 浏览器控制台无 CORS 报错

---

## 7. 常见问题排查

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 页面一直转圈 / 提示“网络异常” | `VITE_API_BASE_URL` 没配或没 Redeploy | 检查 Vercel 环境变量后重新部署 |
| 登录返回 401 | 密码错，或 `SEED_PASSWORD` 与库中哈希不一致 | 用种子密码；或清空 `sys_user` 表重启重建 |
| 控制台 CORS 报错 | Render 的 `CORS_ALLOWED_ORIGINS` 未包含 Vercel 域名 | 回填域名并等待服务重启 |
| 后端启动报 `column "xxx" is of type jsonb but expression is of type character varying` | 用了 MySQL/参数未按 `Types.OTHER` 写入 | 确认数据库是 PG 15；JSONB 字段走 `JsonbTypeHandler` |
| Render 构建失败 `Could not find artifact` | Docker 上下文不对 | Root Directory 设为 `backend`，Dockerfile Path 为 `./Dockerfile` |
| 刷新子路由 404 | 缺少 SPA rewrite | 确认 `vercel.json` 已提交并重新部署 |
| 二维码扫出来是 `localhost` | `VITE_PUBLIC_BASE_URL` 没配 | Vercel 配好正式域名后 Redeploy，重新打印标签 |
| 上传照片后重启服务，图片 404 | Render 免费实例磁盘是临时的，`UPLOAD_DIR=/app/uploads` 重启即清空 | 挂持久盘或改 S3/OSS（文件地址仍写 `attachments`，接口不变） |
| 首次访问特别慢（30–60s） | Render 免费实例休眠冷启动 | 属正常现象；升级付费实例可消除 |

---

## 8. 上线后的安全收尾

1. **改初始密码**：`SEED_PASSWORD` 换成强密码；或登录后由管理员重置 `sys_user.password_hash`。
2. **换 JWT_SECRET**：至少 32 位随机串，泄露后立即更换（所有旧 token 失效）。
3. **收窄 CORS**：`CORS_ALLOWED_ORIGINS` 只填自己的前端域名，不要长期保留 `*`。
4. **关闭自动建表**：稳定后把 `SQL_INIT_MODE` 改为 `never`，避免每次启动重复执行脚本。
5. **数据库备份**：Render PostgreSQL 免费版有保留期限制，重要数据定时导出：

   ```bash
   pg_dump "postgresql://sm_user:密码@外部主机:5432/smart_materials?sslmode=require" \
     -f smart_materials_backup.sql
   ```

### 8.5 附件与 LDAP 现状

**附件（损耗照片）**

- 后端把文件存到 `UPLOAD_DIR`（容器内默认 `/app/uploads`），目录结构 `类别/年月/uuid.ext`；
- 数据库里只存相对地址（`/uploads/scrap/202611/xxx.png`），写进 `stock_transaction.attachments`（jsonb 数组）；
- Render 免费实例**磁盘是临时的**，重启/重新部署后图片会丢。生产建议：
  1. Render 上挂 Persistent Disk 并把 `UPLOAD_DIR` 指过去；或
  2. 把 `FileStorageService` 换成 S3 / 阿里云 OSS 实现（接口契约不变）。

**LDAP**

- 当前版本**没有**真实 LDAP 绑定校验。`app.ldap.enabled` 与 `VITE_LDAP_ENABLED` 只是开关，
  登录走 `sys_user.ldap_uid` + `password_hash`（BCrypt）本地密码。
- `sys_user.ldap_uid` 字段已预留，二期接统一身份认证时在此处扩展（`AuthService#login` 增加 LDAP bind 分支）。

### 8.6 构建与测试（CI）

仓库自带 `.github/workflows/ci.yml`：

- 前端：`npm ci` → `npm run build`（tsc + vite），产物 `dist/` 作为 artifact；
- 后端：`mvn -B -ntp test` → `mvn -DskipTests package`（测试用内嵌 PostgreSQL 15，不需要外部数据库）。

推送后在 GitHub Actions 里应看到两个 job 全绿。
