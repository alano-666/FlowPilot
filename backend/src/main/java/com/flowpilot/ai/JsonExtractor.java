package com.flowpilot.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.common.BizException;

/**
 * 宽松 JSON 提取与修复器：用于不执行服务端 Schema 校验的第三方网关。
 * 处理常见输出噪声：```json 代码块包裹、前后缀文字、尾部逗号、单引号、字段缺失等。
 */
public final class JsonExtractor {

    private static final ObjectMapper LENIENT = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonExtractor() {
    }

    /**
     * 从模型输出文本中提取 JSON 并反序列化为目标类型。
     * 解析失败时自动尝试「截断修复」（输出被 max_tokens 切断时补齐括号，救回已生成数据）。
     * @param raw 模型原始输出
     * @param schema 目标类型（Jackson 反序列化，忽略未知字段）
     */
    public static <T> T extract(String raw, Class<T> schema, String taskName) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(50010, "AI " + taskName + " 返回内容为空");
        }
        String text = raw.trim()
                .replaceFirst("^```(json|JSON)?\\s*", "")
                .replaceFirst("\\s*```$", "");
        // 路径一：常规提取（完整 JSON）
        String json = repair(extractJsonObject(text));
        try {
            return LENIENT.readValue(json, schema);
        } catch (Exception ignored) {
            // 路径二：输出被 max_tokens 截断——从第一个 { 取到文本末尾，栈式补齐括号
            int start = text.indexOf('{');
            if (start >= 0) {
                String truncated = repair(repairTruncated(text.substring(start)));
                if (truncated != null) {
                    try {
                        return LENIENT.readValue(truncated, schema);
                    } catch (Exception ignored2) {
                        // 补齐后仍失败，走下方报错
                    }
                }
            }
            throw new BizException(50010, "AI " + taskName + " 返回的 JSON 无法解析；原始输出片段: "
                    + snippet(raw, 200));
        }
    }

    /**
     * 截断修复：逐字符扫描（跳过字符串内部），栈式补齐缺失的 } ] 括号。
     * 返回 null 表示无法修复。
     */
    static String repairTruncated(String json) {
        StringBuilder sb = new StringBuilder(json);
        java.util.Deque<Character> stack = new java.util.ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> stack.push('}');
                case '[' -> stack.push(']');
                case '}', ']' -> {
                    if (!stack.isEmpty() && stack.peek() == c) {
                        stack.pop();
                    } else {
                        return null; // 括号错位，无法修复
                    }
                }
                default -> { }
            }
        }
        if (inString) {
            sb.append('"'); // 字符串未闭合：补引号
        }
        while (!stack.isEmpty()) {
            sb.append(stack.pop());
        }
        return sb.toString();
    }

    /** 提取第一个完整的 {...} 块 */
    static String extractJsonObject(String text) {
        String t = text.trim()
                .replaceFirst("^```(json|JSON)?\\s*", "")
                .replaceFirst("\\s*```$", "");
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return t;
        }
        return t.substring(start, end + 1);
    }

    /** 轻量修复：单引号归一化、尾部逗号、控制字符 */
    static String repair(String json) {
        String s = normalizeQuotes(json);
        // 去掉字符串外部的尾部逗号（",}" 与 ",]"）
        s = s.replaceAll(",\\s*([}\\]])", "$1");
        // 移除非法控制字符（保留 \n \t）
        s = s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        return s;
    }

    /**
     * 单引号归一化：将双引号字符串之外的 '…' 转换为 "…"（状态机逐字符处理，
     * 双引号字符串内部内容原样保留，避免误伤转义与引号内文本）。
     */
    static String normalizeQuotes(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inDouble = false;
        boolean inSingle = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inDouble) {
                sb.append(c);
                if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                    inDouble = false;
                }
                continue;
            }
            if (inSingle) {
                if (c == '\'') {
                    inSingle = false;
                    sb.append('"');
                } else {
                    sb.append(c);
                }
                continue;
            }
            if (c == '"') {
                inDouble = true;
                sb.append(c);
            } else if (c == '\'') {
                inSingle = true;
                sb.append('"');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 校验是否为合法 JSON 对象 */
    public static boolean isValidObject(String text) {
        try {
            JsonNode node = LENIENT.readTree(extractJsonObject(text));
            return node.isObject() || node.isArray();
        } catch (Exception e) {
            return false;
        }
    }

    private static String snippet(String s, int len) {
        return s.length() <= len ? s : s.substring(0, len) + "…";
    }
}
