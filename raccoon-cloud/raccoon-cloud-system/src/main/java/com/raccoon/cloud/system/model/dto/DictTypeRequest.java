package com.raccoon.cloud.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "字典类型请求DTO")
public class DictTypeRequest {

    @Schema(description = "字典类型ID（编辑时必填）")
    private Long id;

    @Schema(description = "字典类型编码")
    @NotBlank(message = "字典类型编码不能为空")
    private String dictTypeCode;

    @Schema(description = "字典类型名称")
    @NotBlank(message = "字典类型名称不能为空")
    private String dictTypeName;

    @Schema(description = "状态：0=禁用，1=启用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "排序号")
    private Integer sort;
}
