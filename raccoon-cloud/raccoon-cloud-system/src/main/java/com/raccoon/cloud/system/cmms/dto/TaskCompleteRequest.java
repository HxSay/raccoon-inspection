package com.raccoon.cloud.system.cmms.dto;

import lombok.Data;

import java.util.List;

@Data
public class TaskCompleteRequest {
    private Long taskId;
    private List<RecordLine> records;

    @Data
    public static class RecordLine {
        private Long pointId;
        private String checkValue;
        private Integer isNormal;
        /** JSON 数组字符串 */
        private String imageUrls;
    }
}
