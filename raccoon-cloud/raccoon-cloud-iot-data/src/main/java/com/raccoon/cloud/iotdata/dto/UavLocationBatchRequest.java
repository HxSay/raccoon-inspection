package com.raccoon.cloud.iotdata.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UavLocationBatchRequest {

    @NotEmpty
    @Valid
    private List<Item> points;

    @Data
    public static class Item {

        @NotNull
        private Long uavId;

        private Long taskId;
        private Long mapId;

        @NotNull
        private BigDecimal longitude;

        @NotNull
        private BigDecimal latitude;

        @NotNull
        private Float height;

        private Float speed;
        private Float battery;

        @NotNull
        private Integer locationMode;

        /** 为空则使用服务端入库时间 */
        private LocalDateTime createTime;
    }
}
