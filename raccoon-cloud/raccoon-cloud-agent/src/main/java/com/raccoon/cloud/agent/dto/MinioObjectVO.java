package com.raccoon.cloud.agent.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class MinioObjectVO {
    private String objectKey;
    private long size;
    private Instant lastModified;
    private String etag;
    private String presignedUrl;
}
