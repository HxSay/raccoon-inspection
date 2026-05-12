package com.raccoon.cloud.system.cmms.dto;

import lombok.Data;

@Data
public class CmmsPageRequest {
    private long page = 1;
    private long size = 10;
}
