package com.raccoon.cloud.drone.llm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NlpTaskParseRequest {

    @NotBlank(message = "用户输入不能为空")
    private String userInput;
}
