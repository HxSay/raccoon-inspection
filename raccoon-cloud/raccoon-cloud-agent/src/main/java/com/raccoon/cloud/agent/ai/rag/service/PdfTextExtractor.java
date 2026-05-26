package com.raccoon.cloud.agent.ai.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * PDF 文本解析：基于 Apache PDFBox。
 * 后续若需要扫描件识别可在此处扩展 OCR 分支。
 */
@Slf4j
@Component
public class PdfTextExtractor {

    /**
     * 提取整份 PDF 的纯文本。
     *
     * @param file 上传的 PDF
     * @return 纯文本（已规范换行）
     */
    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF 文件不能为空");
        }
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String raw = stripper.getText(document);
            return raw == null ? "" : raw.replace("\r\n", "\n").trim();
        } catch (IOException e) {
            log.error("解析 PDF 文本失败: {}", file.getOriginalFilename(), e);
            throw new IllegalStateException("解析 PDF 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断文本是否过短（疑似扫描件，预留 OCR 入口）。
     */
    public boolean looksLikeScanned(String text, int totalPagesHint) {
        if (text == null) {
            return true;
        }
        return text.length() < Math.max(100, totalPagesHint * 50);
    }
}
