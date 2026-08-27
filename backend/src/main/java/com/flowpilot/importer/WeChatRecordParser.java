package com.flowpilot.importer;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信聊天记录解析器（PRD 3.2 微信导入）。
 *
 * 支持格式（自动识别，无需用户选择）：
 *  1. 微信电脑版「复制聊天记录」格式：
 *       2026-08-27 10:30:15 张三
 *       消息内容（可多行）
 *       2026-08-27 10:31:02 李四: 好的
 *  2. 带分隔符变体：2026-08-27 10:30:15 张三: 内容 / [2026-08-27 10:30:15] 张三 内容
 *  3. CSV 导出（含表头自动识别列：时间/发送者/内容，任意列序）
 *  4. 年月日中文日期、无年份日期（按当前年）、仅时间（按当天）
 */
@Component
public class WeChatRecordParser {

    public record ParsedMessage(LocalDateTime sentAt, String sender, String content, boolean isImage) {
    }

    public record ParseResult(List<ParsedMessage> messages, String format, List<String> warnings) {
    }

    // 时间戳前缀：2026-08-27 10:30:15 / 2026/8/27 10:30 / 2026年8月27日 10:30 / 08-27 10:30:15 / 10:30:15
    private static final Pattern TIME_LINE = Pattern.compile(
            "^\\s*\\[?(\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}日?|\\d{1,2}[-/月]\\d{1,2}日?|\\d{1,2}:\\d{2}(?::\\d{2})?)"
                    + "[ T\\s]+(\\d{1,2}:\\d{2}(?::\\d{2})?)?\\]?[\\s:：|\\-]*(.+)$");

    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm"),
            DateTimeFormatter.ofPattern("yyyy年M月d日 H:mm"),
            DateTimeFormatter.ofPattern("yyyy年M月d日 H:mm:ss"));

    private static final Pattern SENDER_SPLIT = Pattern.compile("^([^:：]{1,30})[:：]\\s*(.*)$", Pattern.DOTALL);

    /** 解析入口：按内容自动选择 CSV 或复制格式 */
    public ParseResult parse(String fileName, String content) {
        String text = normalize(content);
        List<String> warnings = new ArrayList<>();
        if (isCsv(text)) {
            return parseCsv(text, warnings);
        }
        ParseResult r = parseCopyFormat(text, warnings);
        if (r.messages().isEmpty()) {
            warnings.add("未识别到聊天记录结构，请确认文件为微信复制/导出格式（示例见 docs/06）");
        }
        return new ParseResult(r.messages(), "TXT(复制格式)", warnings);
    }

    private boolean isCsv(String text) {
        String firstLine = text.lines().findFirst().orElse("").toLowerCase();
        boolean headerish = firstLine.contains(",") && (firstLine.contains("时间") || firstLine.contains("日期")
                || firstLine.contains("time") || firstLine.contains("date") || firstLine.contains("发送")
                || firstLine.contains("昵称") || firstLine.contains("内容") || firstLine.contains("消息")
                || firstLine.contains("sender") || firstLine.contains("content"));
        // 首行无表头但逗号分隔且列数 >=3 也按 CSV 处理
        return headerish || (firstLine.contains(",") && firstLine.split(",", -1).length >= 3
                && firstLine.matches(".*\\d{4}.*"));
    }

    /** CSV 解析：按表头自动映射列，无表头时按 [时间,发送者,内容] 位置解析 */
    private ParseResult parseCsv(String text, List<String> warnings) {
        List<String[]> rows = splitCsv(text);
        if (rows.isEmpty()) {
            return new ParseResult(List.of(), "CSV", warnings);
        }
        List<ParsedMessage> messages = new ArrayList<>();
        int timeCol = -1;
        int senderCol = -1;
        int contentCol = -1;
        String[] header = rows.get(0);
        String headerLine = String.join(",", header).toLowerCase();
        if (headerLine.contains("时间") || headerLine.contains("日期") || headerLine.contains("time") || headerLine.contains("date")) {
            for (int i = 0; i < header.length; i++) {
                String h = header[i].toLowerCase();
                if (timeCol < 0 && (h.contains("时间") || h.contains("日期") || h.contains("time") || h.contains("date"))) {
                    timeCol = i;
                } else if (senderCol < 0 && (h.contains("发送") || h.contains("昵称") || h.contains("sender")
                        || h.contains("name") || h.contains("用户"))) {
                    senderCol = i;
                } else if (contentCol < 0 && (h.contains("内容") || h.contains("消息") || h.contains("content")
                        || h.contains("message") || h.contains("text"))) {
                    contentCol = i;
                }
            }
        } else {
            timeCol = 0;
            senderCol = 1;
            contentCol = 2;
        }
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length < 3) {
                continue;
            }
            String timeStr = cell(row, timeCol);
            String sender = cell(row, senderCol);
            String contentText = cell(row, contentCol);
            LocalDateTime time = parseTime(timeStr);
            if (time == null) {
                warnings.add("第 " + (r + 1) + " 行时间无法解析: " + timeStr);
                continue;
            }
            if (contentText.isBlank()) {
                continue;
            }
            messages.add(new ParsedMessage(time, sender.isBlank() ? "未知" : sender, contentText, false));
        }
        return new ParseResult(messages, "CSV", warnings);
    }

    /** 复制格式解析：时间戳开头的行作为新消息，其后直到下一条时间戳的行为消息内容 */
    private ParseResult parseCopyFormat(String text, List<String> warnings) {
        List<ParsedMessage> messages = new ArrayList<>();
        String[] lines = text.split("\n");
        LocalDateTime currentTime = null;
        String currentSender = "未知";
        StringBuilder currentContent = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher m = TIME_LINE.matcher(line);
            if (m.matches()) {
                // 上一条消息落库
                if (currentTime != null && currentContent.length() > 0) {
                    messages.add(new ParsedMessage(currentTime, currentSender,
                            currentContent.toString().trim(), false));
                }
                String datePart = m.group(1);
                String timePart = m.group(2);
                String rest = m.group(3).trim();
                LocalDateTime t = parseTime(datePart + (timePart == null ? "" : " " + timePart));
                if (t == null) {
                    t = parseTime(datePart);
                }
                if (t == null) {
                    warnings.add("时间无法解析: " + line);
                    // 保留上一消息上下文，把本行并入内容
                    currentContent.append(line).append('\n');
                    continue;
                }
                currentTime = t;
                // 拆分发送者与内容：优先 "张三: xxx"，否则整段视为发送者、内容待后续行
                Matcher sm = SENDER_SPLIT.matcher(rest);
                if (sm.matches() && sm.group(2).trim().length() > 0) {
                    currentSender = sm.group(1).trim();
                    currentContent = new StringBuilder(sm.group(2));
                } else {
                    currentSender = rest;
                    currentContent = new StringBuilder();
                }
            } else if (currentTime != null) {
                currentContent.append(line).append('\n');
            }
            // 无时间戳且无上一条消息的行（文件头说明等）忽略
        }
        if (currentTime != null && currentContent.length() > 0) {
            messages.add(new ParsedMessage(currentTime, currentSender,
                    currentContent.toString().trim(), false));
        }
        return new ParseResult(messages, "TXT(复制格式)", warnings);
    }

    /** 时间解析：依次尝试各格式；缺少年份按当前年、缺少日期按当天 */
    public static LocalDateTime parseTime(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        for (DateTimeFormatter f : TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(t, f);
            } catch (Exception ignored) {
                // 尝试下一格式
            }
        }
        // 无年份日期（如 08-27 10:30:15）：补当前年份后重试
        String year = String.valueOf(LocalDate.now().getYear());
        for (String candidate : List.of(year + "-" + t, year + "/" + t, year + "年" + t)) {
            for (DateTimeFormatter f : TIME_FORMATTERS) {
                try {
                    return LocalDateTime.parse(candidate, f);
                } catch (Exception ignored) {
                    // 尝试下一格式
                }
            }
        }
        // 仅时间 HH:mm / HH:mm:ss → 当天
        try {
            return LocalDateTime.of(LocalDate.now(), LocalTime.parse(t));
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 简单 CSV 切分（支持双引号包裹与转义） */
    static List<String[]> splitCsv(String text) {
        List<String[]> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                current.add(field.toString().trim());
                field.setLength(0);
            } else if (c == '\n') {
                current.add(field.toString().trim());
                field.setLength(0);
                rows.add(current.toArray(new String[0]));
                current = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        current.add(field.toString().trim());
        if (!current.isEmpty()) {
            rows.add(current.toArray(new String[0]));
        }
        return rows;
    }

    private String cell(String[] row, int col) {
        return col >= 0 && col < row.length ? row[col] : "";
    }

    private String normalize(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n')
                .replace("﻿", "").trim();
    }
}
