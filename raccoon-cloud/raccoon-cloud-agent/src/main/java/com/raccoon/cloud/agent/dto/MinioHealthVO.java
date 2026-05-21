package com.raccoon.cloud.agent.dto;

import lombok.Data;

@Data
public class MinioHealthVO {
    private boolean reachable;
    private String endpoint;
    private String bucketName;
    private boolean bucketExists;
    private int urlExpireDays;
    private String message;
}
