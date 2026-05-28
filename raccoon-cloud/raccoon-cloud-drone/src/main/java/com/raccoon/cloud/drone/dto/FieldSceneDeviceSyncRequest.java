package com.raccoon.cloud.drone.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class FieldSceneDeviceSyncRequest {

    @NotNull
    private Long mapId;

    private String sceneType;

    private List<FieldSceneDeviceSyncItem> devices = new java.util.ArrayList<>();
}
