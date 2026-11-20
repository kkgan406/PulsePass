# MySQL 并发实验

结果与环境见 [Phase 2.1 报告](../docs/experiments/phase-2-1-mysql-concurrency.md)。实验专用入口仅在唯一的 `benchmark` profile 下注册，并强制绑定 127.0.0.1、连接本机 `pulsepass_bench`；不要在联网服务或正式数据库启用。A 是刻意错误的对照，绝不能用于正式购票。

## 一次性准备

1. 启动本项目 MySQL 8.4 容器，确认正式数据与数据卷不受影响。检查 `pulsepass_bench` 是否已存在；如果已存在且归属不明，先审查，不要覆盖。
2. 在确认的实例中创建 `pulsepass_bench`，仅向现有应用账户授予该库权限。下列语句需要在容器内以管理员执行，密码由容器环境提供，不写入命令：

```powershell
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -u root -e "CREATE DATABASE IF NOT EXISTS pulsepass_bench CHARACTER SET utf8mb4; GRANT ALL PRIVILEGES ON pulsepass_bench.* TO \"pulsepass\"@\"%\";"'
```

3. 使用 JDK 21，设置 `DB_PASSWORD` 为本机应用账户的密码。设置 `JAVA_HOME` 和 `MAVEN_USER_HOME`；若使用本仓库忽略的 `target/jdk21`，不要执行会删除该目录的 `clean`。默认 `local` 的 JDBC URL 不应指向实验库。
4. 运行 opt-in E1；它只在 `pulsepass_bench` 创建 `lab_inventory` 和 `lab_order`，并按每轮 UUID 删除自己的记录。普通测试默认跳过此测试。

```powershell
.\mvnw.cmd -Pmysql-it '-Dit.test=MysqlRaceExperimentIT' '-Dpulsepass.bench=true' verify
```

5. 准备 Grafana Labs 官方 k6 Windows 可执行文件至 `target/k6-unpacked/k6/k6.exe`；可用 Windows 包管理器下载 `GrafanaLabs.k6` 并校验安装包，再解压到上述忽略目录。脚本不会将安装包或结果提交。

## 运行 E2

使用 Java 21 构建后，在独立终端运行：

```powershell
.\mvnw.cmd -DskipTests package
java -jar target/pulsepass-0.0.1-SNAPSHOT.jar --spring.profiles.active=benchmark
```

确认 `127.0.0.1:18081` 监听且不是 `0.0.0.0`。使用另一个 PowerShell 在仓库根目录运行：

```powershell
.\benchmarks\scripts\run.ps1 -DurationSeconds 5 -InitialStock 100000
```

默认按 A/B/C、不同用户/同一用户和 1/10/50/100 VU 顺序运行；每次运行生成全新 UUID。可用 `-VusList`、`-Strategies`、`-Modes` 调整。结果写入已忽略的 `benchmarks/results/`，脚本保留每轮汇总和 run ID，并查询数据库核验。运行不同参数前另存需要保留的汇总 CSV；单轮原始文件名包含库存和时长，但相同参数重复执行仍会覆盖。若容器或 k6 不可用脚本报错，不伪造数据。

数据库中会保留 E2 的实验行供复查；若需要清理，只能在确认目标为 `pulsepass_bench` 后，按特定 run ID 先删除 `lab_order` 再删除对应 `lab_inventory`，不要清空正式开发库，也不要使用 `docker compose down -v`。

## Phase 2.2 正式购票链路与固定到达率

[实验报告](../docs/experiments/phase-2-2-mysql-baseline.md) 中的 B0/B1 使用同一个 `benchmark` profile，但调用正式 `/api/v1/orders` 而非简化实验入口。先检查 `pulsepass_bench` 中 `activity`、`ticket_type`、`inventory`、`ticket_order` 是否存在且确属实验；在空实验库中按 001–004 顺序执行迁移。004 前必须检查重复有效订单、非法状态和已有索引；不要对已有表盲目重复执行 ALTER。不要将 URL 指向 `pulsepass`。

Java 21 启动实验进程时，附加 `--management.endpoints.web.exposure.include=health,metrics --spring.datasource.hikari.maximum-pool-size=10 --spring.datasource.hikari.connection-timeout=30000`；管理指标只在本机实验进程暴露。设置好 `DB_PASSWORD` 和 `JAVA_HOME`，然后运行：

```powershell
python benchmarks/scripts/baseline.py --group b0 --rates 10,25,50,100 --modes s1,s2-distinct,s2-same --repeat 3 --duration 5 --warmup 3
```

每次生成新时间戳目录和新活动/票种，不覆盖之前的 Phase 2.1 结果。每 iteration 发一次 HTTP 请求；`s1` 初始库存 100000，`s2-*` 初始库存 10。脚本保存 k6 原始点、摘要、每轮 UUID 和数据库审计，并对每个 201 响应 ID 查库；当一致性检查失败时停止。默认脚本不会删除实验数据。MySQL 全局锁计数可能包含其他连接；HTTP 的通用 500 无法独立识别死锁，报告不得将它误作售罄。
