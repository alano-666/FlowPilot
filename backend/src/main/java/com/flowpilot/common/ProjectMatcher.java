package com.flowpilot.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目名/客户名模糊匹配工具：供邮件主题、导入文件名等场景做项目归属。
 *
 * 匹配策略（三级）：
 *  1. 全名包含（精确）；
 *  2. 项目名/客户名任意连续 ≥4 字片段出现在文本中（容忍「数据中台建设」vs「杭州云启-数据中台建设项目」这类差异）；
 *  3. 去后缀核心词匹配（如「XX项目」→「XX」）。
 */
public final class ProjectMatcher {

    private ProjectMatcher() {
    }

    /** 文本是否与任一名称匹配 */
    public static boolean matchesAny(String text, String... names) {
        if (text == null) {
            return false;
        }
        String haystack = text.toLowerCase();
        for (String name : names) {
            if (name != null && !name.isBlank() && fuzzyContains(haystack, name.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    static boolean fuzzyContains(String haystack, String name) {
        if (haystack.contains(name)) {
            return true;
        }
        // 连续 ≥4 字片段命中
        for (int len = 6; len >= 4; len--) {
            for (String fragment : fragments(name, len)) {
                if (fragment.length() >= 4 && haystack.contains(fragment)) {
                    return true;
                }
            }
        }
        // 紧凑化匹配：去掉标点分隔符后重试（容忍「智联ERP」vs「智联-erp」差异）
        String compactName = name.replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "");
        String compactHay = haystack.replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "");
        if (!compactName.equals(name) && compactHay.contains(compactName)) {
            return true;
        }
        if (compactName.length() >= 5) {
            for (int len = 6; len >= 4; len--) {
                for (String fragment : fragments(compactName, len)) {
                    if (compactHay.contains(fragment)) {
                        return true;
                    }
                }
            }
        }
        // 去常见后缀后的核心词
        for (String core : cores(name)) {
            if (core.length() >= 3 && haystack.contains(core)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> fragments(String name, int len) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i + len <= name.length(); i++) {
            out.add(name.substring(i, i + len));
        }
        return out;
    }

    private static List<String> cores(String name) {
        List<String> out = new ArrayList<>();
        for (String suffix : new String[]{"项目", "流程", "方案", "计划", "工单", "项目组"}) {
            if (name.endsWith(suffix) && name.length() - suffix.length() >= 3) {
                out.add(name.substring(0, name.length() - suffix.length()));
            }
        }
        return out;
    }
}
