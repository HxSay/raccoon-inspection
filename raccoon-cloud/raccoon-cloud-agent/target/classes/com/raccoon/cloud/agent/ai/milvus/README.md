# Milvus 向量库调试工具

个人 RAG 调试用，与 Ollama/Neo4j/MinIO 测试接口隔离在 `com.raccoon.cloud.agent.ai.milvus` 包。

## 接口（agent 8081，网关 `/api/agent`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ai/milvus/test/insert` | 故障知识入库 |
| POST | `/ai/milvus/test/search` | 相似检索 |
| POST | `/ai/milvus/test/getById` | 按 ID 查询 |

## 配置

- `src/main/resources/agent-milvus-test.yml`：Milvus + Ollama Embedding 默认配置
- 启动时由 `MilvusTestEnvironmentPostProcessor` 自动加载，无需改 `application.yml`
- 若你已有 Milvus 集成配置，以你现有 `spring.ai.vectorstore.milvus` 为准，可只改 yml 中的 `collection-name`、`embedding-dimension`

## 依赖

`pom.xml` 需包含 `spring-ai-starter-vector-store-milvus`（见 `milvus-test-pom-dependencies.xml`）。

## 前端

- 页面：`raccoon-ui/src/views/ai/milvus-test/index.vue`
- API：`raccoon-ui/src/api/agent-milvus-test.ts`
- 访问：`http://localhost:3000/ai/milvus/test`
