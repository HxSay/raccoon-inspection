package com.raccoon.cloud.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "字典数据查询请求DTO")
public class DictDataQueryRequest {

    @Schema(description = "页码")
    private Integer page = 1;

    @Schema(description = "每页数量")
    private Integer size = 10;

    @Schema(description = "字典类型编码")
    private String dictTypeCode;

    @Schema(description = "字典显示名称")
    private String dictLabel;

    @Schema(description = "状态")
    private Integer status;
}
