package com.raccoon.cloud.system.cmms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InspectionWorkOrderIdRequest {
    @NotNull
    private Long id;
}
