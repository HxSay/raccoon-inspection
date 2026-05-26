package com.raccoon.cloud.agent.ai.rag.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切块器：按目标字符数（默认 800）切分，并带轻量重叠以避免切割语义边界。
 * 中文 PDF 切块对 token 不敏感，按字符切更可控、性能更好。
 */
@Component
public class TextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_OVERLAP = 80;

    public List<String> split(String text) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return chunks;
        }
        int size = Math.max(100, chunkSize);
        int ov = Math.max(0, Math.min(overlap, size / 2));
        String normalized = text.replaceAll("[\\t\\f]+", " ").replaceAll(" {2,}", " ");

        int start = 0;
        int len = normalized.length();
        while (start < len) {
            int end = Math.min(len, start + size);
            int safeEnd = findSentenceBoundary(normalized, start, end);
            String chunk = normalized.substring(start, safeEnd).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (safeEnd >= len) {
                break;
            }
            start = Math.max(safeEnd - ov, start + 1);
        }
        return chunks;
    }

    /** 在区间末尾向前找最近的中文/英文句末标点，避免硬切。 */
    private int findSentenceBoundary(String text, int start, int end) {
        if (end >= text.length()) {
            return text.length();
        }
        int lookback = Math.min(end - start, 80);
        for (int i = end; i > end - lookback; i--) {
            char c = text.charAt(i - 1);
            if (c == '。' || c == '！' || c == '？' || c == '\n'
                    || c == '.' || c == '!' || c == '?') {
                return i;
            }
        }
        return end;
    }
}
