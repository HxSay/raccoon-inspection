package com.raccoon.cloud.drone.llm.service;

import com.raccoon.cloud.drone.llm.config.LlmTaskParseProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 输入预处理与校验：清洗、敏感词、长度、意图关键词。
 */
@Slf4j
@Service
public class LlmInputPreprocessService {

    private static final List<String> INTENT_KEYWORDS = List.of(
            "巡检", "检查", "排查", "复巡", "复查", "看一下", "检测"
    );

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("(?i)password\\s*[:=]"),
            Pattern.compile("密码\\s*[:=]"),
            Pattern.compile("(?i)token\\s*[:=]")
    );

    @Autowired
    private LlmTaskParseProperties properties;

    /**
     * @return 清洗后的文本
     */
    public String preprocess(String rawInput) {
        if (!StringUtils.hasText(rawInput)) {
            throw new IllegalArgumentException("输入不能为空");
        }
        String text = rawInput.trim();
        text = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        text = text.replaceAll("\\s+", " ");
        if (text.length() > properties.getMaxInputLength()) {
            throw new IllegalArgumentException("输入长度超过限制 " + properties.getMaxInputLength());
        }
        for (Pattern p : SENSITIVE_PATTERNS) {
            if (p.matcher(text).find()) {
                throw new IllegalArgumentException("输入包含敏感信息，请重新描述巡检任务");
            }
        }
        if (!containsIntentKeyword(text)) {
            throw new IllegalArgumentException("未识别到巡检意图，请包含：巡检/检查/排查/复巡/复查/看一下/检测");
        }
        log.debug("输入预处理完成 length={}", text.length());
        return text;
    }

    private boolean containsIntentKeyword(String text) {
        for (String kw : INTENT_KEYWORDS) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
