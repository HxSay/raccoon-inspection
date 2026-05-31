package com.raccoon.cloud.drone.dispatch.model.knowledge;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Milvus 语义检索到的巡检规程 / 手册片段。
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class DocumentChunk {

    /** Milvus chunk ID */
    private String chunkId;

    /** 来源文件名 */
    private String fileName;

    /** MinIO 原文 URL */
    private String minioUrl;

    /** 片段摘要内容 */
    private String excerpt;

    /** 相似度得分（越高越相关） */
    private Double score;
}
