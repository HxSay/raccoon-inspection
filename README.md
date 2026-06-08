# Raccoon Inspection

Raccoon Inspection 是一个面向巡检业务的多端、多服务项目，覆盖 PC 管理端、移动端、无人机/巡检机器人三维仿真端，以及基于 Spring Cloud 的后端微服务。当前代码重点包含工单流转、巡检任务规划、无人机调度、故障处理、闭环处置、RAG 知识增强、三维场景联动等能力。

## 技术栈

后端：

- Java 17
- Spring Boot 3.2.0
- Spring Cloud 2023.0.0
- Spring Cloud Alibaba 2023.0.1.0
- MyBatis-Plus 3.5.8
- Spring AI 1.0.0
- Nacos、MySQL、Redis
- 可选集成：Ollama、Milvus、Neo4j、MinIO

前端：

- Vue 3
- TypeScript
- Vite 5
- Pinia
- Vue Router
- Element Plus
- Vant
- Three.js

## 项目结构

```text
raccoon-inspection/
├── raccoon-common/          # 后端公共模块：统一结果、公共模型、工具类等
├── raccoon-cloud/           # 云端微服务聚合工程
│   ├── raccoon-cloud-gateway/    # API 网关
│   ├── raccoon-cloud-agent/      # AI Agent、RAG、Milvus/Neo4j/MinIO 集成
│   ├── raccoon-cloud-knowledge/  # 知识服务
│   ├── raccoon-cloud-data/       # 数据服务
│   ├── raccoon-cloud-workflow/   # 工作流服务
│   ├── raccoon-cloud-device/     # 设备服务
│   ├── raccoon-cloud-ai-model/   # AI 模型服务
│   ├── raccoon-cloud-system/     # 系统、认证、CMMS、工单服务
│   ├── raccoon-cloud-drone/      # 无人机、巡检机器人、调度、故障、闭环服务
│   └── raccoon-cloud-iot-data/   # IoT 位置、遥测、多模态数据服务
├── raccoon-edge/            # 边缘端模块聚合工程
│   ├── raccoon-edge-navigation/
│   ├── raccoon-edge-collection/
│   ├── raccoon-edge-inference/
│   └── raccoon-edge-comm/
├── raccoon-ui/              # PC 管理端
├── raccoon-mobile/          # 移动端
├── raccoon-drone-sim/       # 无人机/巡检机器人三维仿真端
├── scripts/                 # 本地启动、停止、Milvus 工具脚本
├── dev-start.bat            # Windows 一键启动入口
├── dev-stop.bat             # Windows 一键停止入口
└── pom.xml                  # Maven 根工程
```

## 环境要求

基础环境：

- JDK 17
- Maven 3.8+
- Node.js 18+ 和 npm
- MySQL 8.x
- Redis 6+
- Nacos 2.x，默认地址 `localhost:8848`

AI/RAG 相关能力按需准备：

- Ollama：默认地址 `http://localhost:11434`
- 聊天模型：默认配置使用 `qwen2:7b`
- Embedding 模型：Milvus 测试配置使用 `nomic-embed-text`
- Milvus：默认地址 `localhost:19530`
- Neo4j：默认 Bolt 地址 `bolt://127.0.0.1:7687`
- MinIO：默认地址 `http://127.0.0.1:9000`

## 数据库

本地配置默认使用 MySQL `root/root`。不同服务使用不同库名，启动前请按需要创建数据库并执行对应 SQL：

```sql
CREATE DATABASE IF NOT EXISTS `hxsay-agent-sys` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `hxsay_agent_drone` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `hxsay_agent_iot` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `raccoon_inspection` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

主要 SQL 脚本位置：

- `raccoon-cloud/raccoon-cloud-system/src/main/resources/db/`
- `raccoon-cloud/raccoon-cloud-drone/src/main/resources/db/`
- `raccoon-cloud/raccoon-cloud-iot-data/src/main/resources/db/`

如果本地账号、密码、端口不同，请修改各服务的 `src/main/resources/application.yml`。

## 快速启动

Windows 下推荐使用根目录脚本：

```powershell
.\dev-start.bat
```

或直接调用 PowerShell 脚本：

```powershell
.\scripts\dev-start.ps1
```

常用参数：

```powershell
# 只启动核心服务：system、agent、drone、iot-data，以及前端
.\scripts\dev-start.ps1 -Profile Core

# 启动前先编译
.\scripts\dev-start.ps1 -Compile

# 不启动 PC 前端
.\scripts\dev-start.ps1 -NoFrontend

# 不启动移动端
.\scripts\dev-start.ps1 -NoMobile

# 不启动三维仿真端
.\scripts\dev-start.ps1 -NoDroneSim

# 每个服务打开独立 PowerShell 窗口
.\scripts\dev-start.ps1 -ShowWindows
```

启动脚本会先释放项目使用的端口，再启动服务。后台启动时日志输出到：

```text
scripts/logs/
```

停止本地开发环境：

```powershell
.\dev-stop.bat
```

或：

```powershell
.\scripts\dev-stop.ps1
```

## 服务端口

| 服务 | 模块 | 端口 |
| --- | --- | --- |
| API Gateway | `raccoon-cloud-gateway` | `8080` |
| Agent | `raccoon-cloud-agent` | `8081` |
| Knowledge | `raccoon-cloud-knowledge` | `8082` |
| Data | `raccoon-cloud-data` | `8083` |
| Workflow | `raccoon-cloud-workflow` | `8084` |
| Device | `raccoon-cloud-device` | `8085` |
| AI Model | `raccoon-cloud-ai-model` | `8086` |
| System | `raccoon-cloud-system` | `8087` |
| Drone | `raccoon-cloud-drone` | `8091` |
| IoT Data | `raccoon-cloud-iot-data` | `8092` |
| PC 管理端 | `raccoon-ui` | `3000` |
| 移动端 | `raccoon-mobile` | `5174` |
| 三维仿真端 | `raccoon-drone-sim` | `3010` |

常用访问地址：

- PC 管理端：`http://localhost:3000`
- 移动端：`http://localhost:5174`
- 三维仿真端独立访问：`http://localhost:3010`
- PC 端内嵌仿真页：`http://localhost:3000/sim/drone`
- 网关：`http://localhost:8080`

## 手动构建

后端整体构建：

```powershell
mvn clean package -DskipTests
```

构建指定后端模块及其依赖：

```powershell
mvn -pl raccoon-cloud/raccoon-cloud-drone -am package -DskipTests
```

PC 管理端：

```powershell
cd raccoon-ui
npm install
npm run dev
npm run build
```

移动端：

```powershell
cd raccoon-mobile
npm install
npm run dev
npm run build
```

三维仿真端：

```powershell
cd raccoon-drone-sim
npm install
npm run dev
npm run build
```

根目录也提供了仿真端快捷 npm 脚本：

```powershell
npm run install:sim
npm run dev
npm run build
```

## 主要功能模块

- 系统与认证：登录、用户、角色、字典、SSO、权限相关能力。
- CMMS 与工单：巡检工单、审核、任务绑定、工单流转。
- 无人机与机器人：设备档案、航线规划、巡检任务、运行状态、遥测数据。
- 智能调度：任务解析、路径规划、拍照点位、终端能力评估、任务分配审计。
- 故障处置：故障事件上报、分级、响应方案、复检调度、闭环处理。
- AI/RAG：本地 LLM 解析、知识增强、Milvus 向量检索、Neo4j 拓扑知识、MinIO 文档存储。
- 三维仿真：变电站/热电厂场景、无人机、机器狗、塔杆、航线、火情/隐患、多模态采集模拟。

## 前端代理

开发环境下，PC 端 `raccoon-ui` 的 Vite 代理主要指向本地后端服务：

- `/api/drone` -> `http://localhost:8091`
- `/api/iot-data` -> `http://localhost:8092`
- `/api/agent` -> `http://localhost:8081`
- `/api` -> `http://localhost:8087`
- `/sim-drone` -> `http://localhost:3010`

移动端 `raccoon-mobile` 默认将 `/api` 代理到 `http://localhost:8087`。

## Milvus 工具

连接本地 Milvus：

```powershell
.\scripts\milvus-connect.ps1
```

指定地址：

```powershell
.\scripts\milvus-connect.ps1 -Uri http://127.0.0.1:19530
```

Milvus 测试相关配置位于：

```text
raccoon-cloud/raccoon-cloud-agent/src/main/resources/agent-milvus-test.yml
scripts/milvus-schema-example.json
```

## 开发注意事项

- 根工程是 Maven 聚合工程，本身没有 Spring Boot main 方法；请启动具体子模块。
- `dev-start.ps1` 会安装 `raccoon-common` 到本地 Maven 仓库，避免子服务启动时找不到公共类。
- Nacos、MySQL、Redis、MinIO、Ollama、Neo4j、Milvus 等外部服务不会被启动脚本自动拉起。
- 仓库中存在 `target/`、`dist/`、`node_modules/` 等生成目录，日常提交应避免提交构建产物。
- 部分历史文件存在编码损坏迹象；新增文档建议统一使用 UTF-8。
