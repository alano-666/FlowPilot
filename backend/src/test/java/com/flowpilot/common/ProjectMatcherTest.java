package com.flowpilot.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 项目模糊匹配测试。
 */
class ProjectMatcherTest {

    @Test
    void exactMatch() {
        assertTrue(ProjectMatcher.matchesAny("上海某某科技远程安装 周报",
                "上海某某科技远程安装", "上海某某科技"));
    }

    @Test
    void fragmentMatchToleratesPrefixSuffix() {
        // 「数据中台建设」是「杭州云启-数据中台建设项目」的连续片段
        assertTrue(ProjectMatcher.matchesAny("来自 数据中台建设 项目组的周报",
                "杭州云启-数据中台建设项目"));
    }

    @Test
    void suffixStrippedCoreMatch() {
        assertTrue(ProjectMatcher.matchesAny("关于智联ERP的更新",
                "深圳智联-ERP需求更新V2.3"));
    }

    @Test
    void noMatch() {
        assertFalse(ProjectMatcher.matchesAny("完全无关的营销邮件",
                "杭州云启-数据中台建设项目"));
        assertFalse(ProjectMatcher.matchesAny(null, "任意项目"));
        assertFalse(ProjectMatcher.matchesAny("文本", (String) null));
    }

    @Test
    void tooShortNeedleNoMatch() {
        // 完整短名称的精确命中是合理的
        assertTrue(ProjectMatcher.matchesAny("项目", "项目"));
        // 但通用词不能靠片段误判（名字叫「杭州项目组」不代表文本「项目」就指它）
        assertFalse(ProjectMatcher.matchesAny("项目", "杭州项目组"));
    }
}
