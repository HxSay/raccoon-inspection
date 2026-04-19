package com.raccoon.cloud.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "字典数据请求DTO")
public class DictDataRequest {

    @Schema(description = "字典数据ID（编辑时必填）")
    private Long id;

    @Schema(description = "字典类型编码")
    @NotBlank(message = "字典类型编码不能为空")
    private String dictTypeCode;

    @Schema(description = "字典显示名称")
    @NotBlank(message = "字典显示名称不能为空")
    private String dictLabel;

    @Schema(description = "字典存储值")
    @NotBlank(message = "字典存储值不能为空")
    private String dictValue;

    @Schema(description = "扩展字段")
    private String dictExtend;

    @Schema(description = "状态：0=禁用，1=启用")
    private Integer status;

    @Schema(description = "排序号")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;
}
