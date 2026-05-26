package com.raccoon.cloud.agent.ai.rag.config;

import com.raccoon.cloud.agent.ai.milvus.config.MilvusTestEmbeddingConfiguration;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 专用 Milvus 向量库：使用独立集合 rag_documents，与 milvus_test 互不影响。
 * 不通过 Spring AI 自动装配获取，避免被 Nacos 配置改写为 vector_store。
 */
@Slf4j
@Configuration
public class RagVectorStoreConfiguration {

    public static final String BEAN_NAME = "ragMilvusVectorStore";

    @Getter
    private MilvusVectorStore vectorStore;

    @Getter
    private final String collectionName;

    @Getter
    private final String databaseName;

    @Getter
    private final int dimension;

    private final MilvusServiceClient milvusServiceClient;
    private final EmbeddingModel embeddingModel;

    public RagVectorStoreConfiguration(
            MilvusServiceClient milvusServiceClient,
            @Qualifier(MilvusTestEmbeddingConfiguration.BEAN_NAME) EmbeddingModel embeddingModel,
            @Value("${raccoon.rag.database-name:default}") String databaseName,
            @Value("${raccoon.rag.collection-name:rag_documents}") String collectionName,
            @Value("${raccoon.rag.embedding-dimension:0}") int dimensionOverride) {
        this.milvusServiceClient = milvusServiceClient;
        this.embeddingModel = embeddingModel;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
        int dim = dimensionOverride > 0 ? dimensionOverride : embeddingModel.dimensions();
        this.dimension = dim > 0 ? dim : 768;
    }

    @PostConstruct
    public void init() {
        log.info("RAG VectorStore 初始化：database={}, collection={}, dimension={}",
                databaseName, collectionName, dimension);
        this.vectorStore = MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
                .databaseName(databaseName)
                .collectionName(collectionName)
                .embeddingDimension(dimension)
                .initializeSchema(true)
                .indexType(IndexType.IVF_FLAT)
                .metricType(MetricType.COSINE)
                .build();
        try {
            this.vectorStore.afterPropertiesSet();
        } catch (Exception e) {
            log.error("RAG VectorStore 初始化失败", e);
            throw new IllegalStateException("RAG VectorStore 初始化失败: " + e.getMessage(), e);
        }
    }
}
