package com.raccoon.cloud.drone.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeoPoint {

    @NotNull
    private Double longitude;

    @NotNull
    private Double latitude;

    @NotNull
    private Double height;
}
