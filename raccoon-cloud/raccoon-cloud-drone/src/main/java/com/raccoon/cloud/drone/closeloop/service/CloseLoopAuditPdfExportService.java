package com.raccoon.cloud.drone.closeloop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.drone.closeloop.entity.TaskCloseLoopAuditEntity;
import com.raccoon.cloud.drone.planning.integration.SystemCmmsClient;
import com.raccoon.common.dto.planning.WorkOrderAuditDetailDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloseLoopAuditPdfExportService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final SystemCmmsClient systemCmmsClient;

    public void export(List<TaskCloseLoopAuditEntity> rows, OutputStream outputStream) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PdfFonts fonts = loadFonts(document);
            PdfWriter writer = new PdfWriter(document, fonts);
            writer.title("巡检闭环审计导出报告");
            writer.paragraph("导出时间: " + TIME.format(LocalDateTime.now())
                    + "    记录数: " + rows.size()
                    + "    导出范围: 最近闭环审计记录", 10, Color.DARK_GRAY);

            Map<Long, WorkOrderAuditDetailDTO> workOrderCache = new HashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                TaskCloseLoopAuditEntity row = rows.get(i);
                WorkOrderAuditDetailDTO detail = loadWorkOrderDetail(row.getWorkOrderId(), workOrderCache);
                JsonNode report = parseJson(row.getReportJson());
                JsonNode missed = parseJson(row.getMissedItemsJson());
                writeAuditRecord(writer, row, detail, report, missed, i + 1);
            }
            writer.close();
            document.save(outputStream);
        }
    }

    private void writeAuditRecord(PdfWriter writer,
                                  TaskCloseLoopAuditEntity row,
                                  WorkOrderAuditDetailDTO detail,
                                  JsonNode report,
                                  JsonNode missed,
                                  int index) throws IOException {
        writer.section(index + ". 审计记录 - " + value(row.getTaskName(), row.getTaskId()));
        writer.keyValue("审计ID", row.getId());
        writer.keyValue("巡检任务ID", row.getTaskId());
        writer.keyValue("巡检工单ID", row.getWorkOrderId());
        writer.keyValue("闭环状态", statusLabel(row.getCloseStatus()));
        writer.keyValue("完成率", percent(row.getCompletionRate()));
        writer.keyValue("补检任务", value(row.getRescheduleTaskId(), "无"));
        writer.keyValue("补检轮次", row.getRescheduleCount());
        writer.keyValue("闭环时间", row.getCloseTime() == null ? null : TIME.format(row.getCloseTime()));

        writer.subsection("巡检工单");
        if (detail == null) {
            writer.paragraph("未获取到 CMMS 工单详情，仅导出闭环审计库中已记录的工单ID与任务信息。", 9, Color.GRAY);
        } else {
            writer.keyValue("工单编号", detail.getOrderNo());
            writer.keyValue("巡检区域", detail.getArea());
            writer.keyValue("工单状态", detail.getStatus());
            writer.keyValue("优先级", detail.getPriorityCode());
            writer.keyValue("调度任务ID", detail.getDispatchTaskId());
            writer.keyValue("执行终端", join(detail.getTerminalName(), detail.getTerminalId()));
            writer.keyValue("审核截止", detail.getAuditDeadline() == null ? null : TIME.format(detail.getAuditDeadline()));
            writer.keyValue("分配原因", detail.getAssignReason());
            writer.keyValue("工单备注", detail.getRemark());
            writer.keyValue("驳回原因", detail.getRejectReason());
        }

        writer.subsection("巡检内容");
        if (detail == null || detail.getSteps() == null || detail.getSteps().isEmpty()) {
            writer.paragraph("暂无巡检步骤明细。", 9, Color.GRAY);
        } else {
            int count = 0;
            for (WorkOrderAuditDetailDTO.AuditStepItem step : detail.getSteps()) {
                count++;
                writer.paragraph(formatStep(step), 9, Color.BLACK);
                if (count >= 80 && detail.getSteps().size() > count) {
                    writer.paragraph("其余 " + (detail.getSteps().size() - count) + " 条巡检步骤未展开。", 9, Color.GRAY);
                    break;
                }
            }
        }

        writer.subsection("巡检执行结果");
        writer.keyValue("任务名称", text(report, "taskName", row.getTaskName()));
        writer.keyValue("计划航点", text(report, "plannedWaypointCount", null));
        writer.keyValue("完成航点", text(report, "finishedWaypointCount", null));
        writer.keyValue("终端数量", text(report, "terminalCount", null));
        writer.keyValue("漏检设备数", text(report, "missedDeviceCount", null));
        writer.keyValue("数据缺口数", text(report, "dataGapCount", null));
        writer.keyValue("AI异常数", text(report, "defectCount", null));
        writer.keyValue("航程", suffix(text(report, "totalDistanceM", null), " m"));
        writer.keyValue("耗时", suffix(text(report, "durationSec", null), " s"));
        writer.keyValue("报告生成时间", text(report, "generateTime", null));
        writer.paragraph("执行摘要: " + value(row.getReportSummary(), text(report, "executiveSummary", "暂无")), 9, Color.BLACK);

        writer.subsection("未完成项与补检");
        writer.keyValue("漏检设备ID", arrayText(missed, "missedDeviceIds"));
        writer.keyValue("漏检航点数", text(missed, "missedWaypointCount", null));
        writer.keyValue("轨迹完整", boolText(text(missed, "trackComplete", null)));
        writeDataGaps(writer, missed);
        writer.space(8);
    }

    private WorkOrderAuditDetailDTO loadWorkOrderDetail(Long workOrderId, Map<Long, WorkOrderAuditDetailDTO> cache) {
        if (workOrderId == null) {
            return null;
        }
        if (cache.containsKey(workOrderId)) {
            return cache.get(workOrderId);
        }
        try {
            WorkOrderAuditDetailDTO detail = systemCmmsClient.auditDetail(workOrderId);
            cache.put(workOrderId, detail);
            return detail;
        } catch (Exception e) {
            log.warn("[closeloop-audit-pdf] 获取工单详情失败 workOrderId={}: {}", workOrderId, e.getMessage());
            cache.put(workOrderId, null);
            return null;
        }
    }

    private JsonNode parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeDataGaps(PdfWriter writer, JsonNode missed) throws IOException {
        JsonNode gaps = missed == null ? null : missed.get("dataGaps");
        if (gaps == null || !gaps.isArray() || gaps.isEmpty()) {
            writer.paragraph("数据缺口明细: 无", 9, Color.BLACK);
            return;
        }
        writer.paragraph("数据缺口明细:", 9, Color.BLACK);
        int index = 0;
        for (JsonNode gap : gaps) {
            index++;
            writer.paragraph("  " + index + ") 航点 " + text(gap, "waypointIndex", "-")
                    + ", 设备 " + text(gap, "deviceId", "-")
                    + ", 类型 " + text(gap, "requiredType", "-")
                    + ", 说明 " + text(gap, "message", "-"), 9, Color.BLACK);
        }
    }

    private String formatStep(WorkOrderAuditDetailDTO.AuditStepItem step) {
        return "  [" + value(step.getStepOrder(), "-") + "] "
                + "类型: " + value(step.getType(), "-")
                + " | 目标: " + value(step.getTarget(), "-")
                + " | 设备: " + value(step.getDeviceName(), "-")
                + " | 检查项: " + value(step.getCheckItem(), "-")
                + " | 标准: " + value(step.getStandardRange(), "-")
                + (StringUtils.hasText(step.getUnit()) ? " " + step.getUnit() : "")
                + " | 内容: " + value(step.getDescription(), "-");
    }

    private PdfFonts loadFonts(PDDocument document) {
        List<Path> candidates = new ArrayList<>();
        String windir = System.getenv("WINDIR");
        if (StringUtils.hasText(windir)) {
            candidates.add(Path.of(windir, "Fonts", "msyh.ttf"));
            candidates.add(Path.of(windir, "Fonts", "simhei.ttf"));
            candidates.add(Path.of(windir, "Fonts", "simsun.ttc"));
            candidates.add(Path.of(windir, "Fonts", "msyh.ttc"));
        }
        candidates.add(Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"));
        candidates.add(Path.of("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc"));
        candidates.add(Path.of("/usr/share/fonts/truetype/wqy/wqy-microhei.ttc"));

        for (Path path : candidates) {
            if (!Files.isReadable(path)) {
                continue;
            }
            try {
                PDFont font = PDType0Font.load(document, path.toFile());
                return new PdfFonts(font, font, true);
            } catch (Exception e) {
                log.warn("[closeloop-audit-pdf] 字体加载失败 {}: {}", path, e.getMessage());
            }
        }
        return new PdfFonts(
                new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                false);
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null || !node.hasNonNull(field)) {
            return fallback;
        }
        JsonNode value = node.get(field);
        if (value.isNumber()) {
            return value.isFloatingPointNumber()
                    ? String.format(Locale.ROOT, "%.2f", value.asDouble())
                    : value.asText();
        }
        return value.asText();
    }

    private static String arrayText(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return "无";
        }
        JsonNode value = node.get(field);
        if (!value.isArray() || value.isEmpty()) {
            return "无";
        }
        List<String> values = new ArrayList<>();
        value.forEach(item -> values.add(item.asText()));
        return String.join(", ", values);
    }

    private static String percent(Double value) {
        if (value == null) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }

    private static String statusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "-";
        }
        return switch (status) {
            case "COMPLETED" -> "已闭环";
            case "PARTIAL" -> "部分完成/已补检";
            case "MANUAL_REQUIRED" -> "需人工介入";
            case "COLLECTING" -> "结果回收中";
            default -> status;
        };
    }

    private static String suffix(String text, String suffix) {
        return StringUtils.hasText(text) ? text + suffix : null;
    }

    private static String boolText(String text) {
        if ("true".equalsIgnoreCase(text)) {
            return "是";
        }
        if ("false".equalsIgnoreCase(text)) {
            return "否";
        }
        return value(text, "-");
    }

    private static String join(String name, Object id) {
        if (!StringUtils.hasText(name) && id == null) {
            return null;
        }
        if (!StringUtils.hasText(name)) {
            return String.valueOf(id);
        }
        return id == null ? name : name + " / " + id;
    }

    private static String value(Object value, Object fallback) {
        if (value == null) {
            return fallback == null ? "-" : String.valueOf(fallback);
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : String.valueOf(fallback);
    }

    private record PdfFonts(PDFont regular, PDFont bold, boolean unicode) {
    }

    private static class PdfWriter {
        private final PDDocument document;
        private final PdfFonts fonts;
        private final float margin = 48;
        private final float width = PDRectangle.A4.getWidth() - margin * 2;
        private PDPageContentStream content;
        private float y;

        PdfWriter(PDDocument document, PdfFonts fonts) throws IOException {
            this.document = document;
            this.fonts = fonts;
            newPage();
        }

        void title(String text) throws IOException {
            line(text, fonts.bold(), 18, Color.BLACK);
            space(8);
        }

        void section(String text) throws IOException {
            space(12);
            line(text, fonts.bold(), 13, new Color(30, 64, 120));
            space(3);
        }

        void subsection(String text) throws IOException {
            space(8);
            line(text, fonts.bold(), 10.5f, Color.BLACK);
        }

        void keyValue(String key, Object value) throws IOException {
            paragraph(key + ": " + CloseLoopAuditPdfExportService.value(value, "-"), 9, Color.BLACK);
        }

        void paragraph(String text, float size, Color color) throws IOException {
            for (String line : wrap(text, fonts.regular(), size)) {
                line(line, fonts.regular(), size, color);
            }
        }

        void space(float value) throws IOException {
            ensureSpace(value);
            y -= value;
        }

        void close() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }

        private void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - margin;
        }

        private void line(String text, PDFont font, float size, Color color) throws IOException {
            ensureSpace(size + 6);
            content.beginText();
            content.setNonStrokingColor(color);
            content.setFont(font, size);
            content.newLineAtOffset(margin, y);
            content.showText(printable(text));
            content.endText();
            y -= size + 4;
        }

        private void ensureSpace(float required) throws IOException {
            if (y - required < margin) {
                newPage();
            }
        }

        private List<String> wrap(String text, PDFont font, float size) throws IOException {
            String src = printable(value(text, ""));
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < src.length(); i++) {
                char ch = src.charAt(i);
                if (ch == '\r') {
                    continue;
                }
                if (ch == '\n') {
                    lines.add(current.toString());
                    current.setLength(0);
                    continue;
                }
                current.append(ch);
                if (textWidth(current.toString(), font, size) > width) {
                    current.deleteCharAt(current.length() - 1);
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(ch);
                }
            }
            if (current.length() > 0) {
                lines.add(current.toString());
            }
            if (lines.isEmpty()) {
                lines.add("");
            }
            return lines;
        }

        private float textWidth(String text, PDFont font, float size) throws IOException {
            return font.getStringWidth(text) / 1000 * size;
        }

        private String printable(String text) {
            if (fonts.unicode()) {
                return text;
            }
            StringBuilder out = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                out.append(ch <= 255 ? ch : '?');
            }
            return out.toString();
        }
    }
}
