package com.raccoon.cloud.drone.dispatch.model.knowledge;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Neo4j 查询到的「设备-故障-文档」关联，用于故障关联优先级与可解释引用。
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class DeviceFaultDocRelation {

    /** 设备 ID */
    private Long deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 故障编码 */
    private String faultCode;

    /** 故障名称 */
    private String faultName;

    /** 严重程度（数值越大越严重，来源 Neo4j severity/level） */
    private Double severity;

    /** 关联文档名 */
    private String docName;

    /** 关联文档 URL */
    private String docUrl;
}
