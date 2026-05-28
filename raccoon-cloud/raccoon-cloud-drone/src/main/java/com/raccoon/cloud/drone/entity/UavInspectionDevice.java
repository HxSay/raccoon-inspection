package com.raccoon.cloud.drone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("uav_inspection_device")
public class UavInspectionDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mapId;
    private String deviceName;
    /** TOWER / TRANSFORMER / VALVE / CUSTOM 等 */
    private String deviceType;

    private Double longitude;
    private Double latitude;
    private Double height;

    private Double sceneX;
    private Double sceneY;
    private Double sceneZ;

    private String sceneType;
    /** 仿真编辑器 editorId 或 builtin-tower-N */
    private String sceneObjectId;

    private String locationDesc;

    /** BUILTIN / SCENE_SYNC / MANUAL */
    private String syncSource;

    private Integer status;
    private String remark;
}
