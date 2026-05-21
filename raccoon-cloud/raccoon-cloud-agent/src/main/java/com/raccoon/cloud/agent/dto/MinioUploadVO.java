package com.raccoon.cloud.agent.dto;

import lombok.Data;

@Data
public class MinioUploadVO {
    private String objectKey;
    private String bucketName;
    private long size;
    private String contentType;
    private String presignedUrl;
}
