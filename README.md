# PulsePass

PulsePass 是面向高并发活动票务与智能运营探索的个人工程项目。

当前阶段：**Phase 1.3 — Order 基础模型**。已实现活动、票种和库存初始化及订单查询；尚未实现购票或订单创建业务。

## 计划技术路线

计划探索 Java 21、Spring Boot 3、MySQL、Redis 与 Lua、RocketMQ、Prometheus / Grafana，以及 Python / FastAPI、LangGraph、Tool Calling / MCP。以上均为未来技术路线，并非已实现能力。

## 目录结构

```text
PulsePass/
├── .gitignore
├── README.md
└── docs/
    ├── phase-0/
    │   ├── 01-business-model.md
    │   ├── 02-purchase-flow.md
    │   ├── 03-domain-model.md
    │   ├── 04-future-risk-points.md
    │   └── 05-business-rules.md
    └── adr/
        └── 0001-one-order-per-user-per-activity.md
```

## Current Business Rule

- One active ticket order per user per activity.
- Ticket types have independent inventory.
- Core ticket purchasing remains in the design phase.

## Roadmap

1. Phase 0：业务、领域与系统建模。
2. 后续：实现并验证基础购票流程。
3. 后续：研究并发、一致性与可观测性。
4. 后续：探索智能运营与工具调用。

## 当前不实现

本阶段不编写购票业务代码，不实现支付、退款、座位选择、优惠券、会员、推荐、OAuth、微服务或高并发基础设施。

## Phase 1.0 本地运行（Windows PowerShell 7）

需要 JDK 21、已启动的 Docker Desktop（Linux 容器）、Docker Compose 和首次下载依赖的网络。Spring Boot 在 Windows 运行，Compose 仅运行官方 `mysql:8.4` 镜像；该标签固定在 8.4 系列，补丁版本会随镜像更新。它使用 `127.0.0.1:3307` 映射至容器 3306，避免占用 Windows MySQL 5.7 的 3306。命名卷 `pulsepass_mysql_data` 持久化数据，健康检查使用应用账户执行 SQL。本机已验证容器和 MySQL 连接。

首次使用复制 `.env.example` 为 `.env`，将两个占位符换成不同的强密码。已有 `.env` 请保留；不要提交该文件或在日志中展示密码。MySQL 官方镜像只在空数据目录初始化数据库和账户；已有卷不会因更改 `.env` 而自动改密码。不要未经数据审查删除卷。

```powershell
Copy-Item .env.example .env # 仅首次且 .env 不存在时执行，然后编辑密码
docker compose config --quiet
docker compose up -d mysql
docker compose ps
```

确认容器健康后，在运行 Spring Boot 的 PowerShell 中设置同一应用密码（通过提示输入，不要将其写进命令或源码）：

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3307/pulsepass?allowPublicKeyRetrieval=true&sslMode=DISABLED'
$env:DB_USERNAME = 'pulsepass'
$env:DB_PASSWORD = Read-Host 'MySQL application password' -AsSecureString | ConvertFrom-SecureString -AsPlainText
.\mvnw.cmd -version
.\mvnw.cmd test
.\mvnw.cmd -Pmysql-it verify
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

`application-local.yml` 对 URL 和用户名提供上述默认值，密码必须通过 `DB_PASSWORD` 传入。URL 中的公钥获取和禁用 TLS 仅用于本机回环地址开发环境，不适用于远程数据库。普通测试不连接数据库；`mysql-it` 使用真实 MySQL 验证 `SELECT 1` 和当前数据库名。应用启动后执行 `Invoke-RestMethod http://localhost:8080/actuator/health`；如 8080 已被占用，可在启动命令后追加 `"-Dspring-boot.run.arguments=--server.port=18080"` 并访问 18080。

本机已验证 MySQL 8.4、集成测试及健康检查。已临时将 Adoptium JDK 21.0.13 放在 `target/jdk21/`，可将当前会话 `JAVA_HOME` 指向其中的 JDK 目录；`target` 是构建输出，`clean` 会删除该临时 JDK，因此长期使用应单独安装或解压 JDK 21 到安全位置。Spring Boot 3.5.x 已结束开源支持，后续需规划升级；高并发、消息队列与 Agent 均未实现。

## Activity API 与数据库初始化

执行前确认当前 Compose 项目指向预期开发库，且检查该库是否已有 `activity` 表；以下脚本只创建不存在的表，不会更新已有表结构，也未引入 Flyway。不要在未经审查的数据库执行：

```powershell
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -h127.0.0.1 -u pulsepass -D pulsepass -e "SHOW TABLES"'
Get-Content -Raw db/migration/001_create_activity.sql | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -h127.0.0.1 -u pulsepass -D pulsepass'
```

`POST /api/v1/activities` 接收 `name`、`description`、`saleStartTime`、`saleEndTime`、`eventStartTime`，返回 201；`GET /api/v1/activities/{id}` 查询详情；`GET /api/v1/activities?page=1&size=10` 按 ID 降序分页，size 最大 100。请求时间使用带偏移的 ISO 8601 格式（如 `2030-01-01T00:00:00Z`），服务端转换为 UTC，截断至毫秒，`DATETIME(3)` 自身不保存时区，读取时按 UTC 解释。

`mysql-it` 会使用当前配置的真实库，Activity 测试在事务内插入并回滚自己的记录；执行前先确认目标数据库和表。HTTP 手工验证会留下测试活动，应自行识别，不清理其他数据。

## Phase 1.2：票种与初始库存

新增 `POST /api/v1/activities/{activityId}/ticket-types` 和 `GET /api/v1/activities/{activityId}/ticket-types`。仅 DRAFT 活动可创建票种，价格以非负整数分表示；服务端在一个本地事务内创建 ENABLED 票种并初始化独立库存。查询按票种 ID 升序，用 JOIN 一次读取票种与库存。

执行 [002_create_ticket_type_inventory.sql](db/migration/002_create_ticket_type_inventory.sql) 前请确认目标为预期的 `pulsepass` 开发库，已执行 001，并检查两张表是否已存在；`IF NOT EXISTS` 不会校正已有表结构。脚本仅创建表，不清空数据，也不引入 Flyway。

```powershell
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -h127.0.0.1 -u pulsepass -D pulsepass -e "SHOW TABLES"'
Get-Content -Raw db/migration/002_create_ticket_type_inventory.sql | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -h127.0.0.1 -u pulsepass -D pulsepass'
```

外键防止引用不存在的活动或票种，唯一约束保证一个票种至多一条库存，CHECK 保证非负价格、库存及可用库存不大于总库存。必须配合服务层事务创建两条记录：单靠唯一和外键不能保证任意手工插入的票种一定有库存。MySQL 集成测试仅清理自己创建的活动和票种；运行前确认数据库配置，测试不会清空开发库。

## Phase 1.3：订单模型和本地查询

`GET /api/v1/orders/{orderId}` 仅供本地开发演示；目前没有身份认证或订单归属校验，不具备生产环境访问权限控制。ID 必须为正整数；金额使用整数分，`created_at` 按 UTC 毫秒解释。状态仅定义 `PENDING`、`CONFIRMED`、`CANCELLED`，未实现状态机或订单创建。

执行 [003_create_ticket_order.sql](db/migration/003_create_ticket_order.sql) 前，请确认目标是预期的开发库、已应用 001 和 002，并检查 `ticket_order` 是否已存在。`IF NOT EXISTS` 不校正既有表结构；脚本不会删除数据，也不代表已启用 Flyway。

```powershell
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -h127.0.0.1 -u pulsepass -D pulsepass -e "SHOW TABLES"'
Get-Content -Raw db/migration/003_create_ticket_order.sql | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -h127.0.0.1 -u pulsepass -D pulsepass'
```

`user_id` 暂为模拟 ID，没有 User 外键。订单的 `activity_id` 必须与所选票种的活动 ID 一致；两个独立外键无法保证此规则，后续订单创建必须校验。同一用户对同一活动最多一笔有效订单（PENDING 或 CONFIRMED），CANCELLED 不占名额；不能简单使用 `(user_id, activity_id)` 唯一约束，否则会阻止取消后重新购买。并发下有效订单唯一性仍待后续设计和验证。数据库测试使用事务回滚自己的测试数据，不清空开发库。

## Phase 1.4：MySQL 同步购票

`PATCH /api/v1/activities/{activityId}/status` 接收 `{"status":"ON_SALE"}`，只允许尚未结束售票、时间配置有效且有可用票种及对应库存的 DRAFT 活动发布。票种创建与发布通过活动行锁协调。购票使用 `POST /api/v1/orders`，请求包含正整数 `userId`、`activityId`、`ticketTypeId`；数量固定为 1，金额从票种读取。售票时间沿用 Phase 0 的闭区间 `saleStartTime <= now <= saleEndTime`，时间按 UTC 解释。

当前 `userId` 是客户端提供的开发测试身份；发布、购票及订单查询均未实现身份认证、授权或订单归属校验，只可在可信的本地环境演示。成功返回 201；重复购买、库存不足、活动不可售返回 409，其他错误按响应 `code` 区分。

应用 [004_active_order_uniqueness.sql](db/migration/004_active_order_uniqueness.sql) 前先确认正确数据库，检查是否已有等价约束、重复有效订单和非法状态；若存在冲突则停止，不删除历史数据。MySQL DDL 会隐式提交，应用后请检查生成列、唯一索引和状态 CHECK；此脚本不是自动迁移。可先使用以下只读 SQL 检查：

```sql
SELECT user_id, activity_id, COUNT(*) FROM ticket_order
WHERE BINARY status IN ('PENDING', 'CONFIRMED')
GROUP BY user_id, activity_id HAVING COUNT(*) > 1;
SELECT status, COUNT(*) FROM ticket_order
WHERE BINARY status NOT IN ('PENDING', 'CONFIRMED', 'CANCELLED') GROUP BY status;
SHOW CREATE TABLE ticket_order;
```

购票事务使用库存条件更新和数据库有效订单唯一索引；数据库死锁、锁等待等非预期异常不会当作售罄。本轮没有自动重试。Phase 2 的独立错误实现实验见 [并发实验计划](docs/phase-2/01-concurrency-experiment.md)。
