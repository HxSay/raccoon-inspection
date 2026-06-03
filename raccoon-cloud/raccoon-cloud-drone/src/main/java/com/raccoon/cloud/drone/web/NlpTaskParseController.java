package com.raccoon.cloud.drone.web;

import com.raccoon.cloud.drone.llm.dto.NlpTaskParseRequest;
import com.raccoon.cloud.drone.llm.dto.NlpTaskParseResponse;
import com.raccoon.cloud.drone.llm.service.NlpTaskParseFacadeService;
import com.raccoon.common.result.HxResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自然语言巡检任务解析 API。
 */
@RestController
@RequestMapping("/nlp/task-parse")
public class NlpTaskParseController {

    @Autowired
    private NlpTaskParseFacadeService nlpTaskParseFacadeService;

    @PostMapping
    public HxResult<NlpTaskParseResponse> parse(
            @Valid @RequestBody NlpTaskParseRequest request,
            @RequestParam(required = false, defaultValue = "false") boolean ruleOnly) {
        if (ruleOnly) {
            return HxResult.success(nlpTaskParseFacadeService.parseRuleOnly(request.getUserInput()));
        }
        return HxResult.success(nlpTaskParseFacadeService.parse(request.getUserInput()));
    }
}
