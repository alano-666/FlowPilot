package com.flowpilot.common;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 列级加密转换器测试：加密往返、随机 IV、明文兼容、密钥缺失回退。
 */
class EncryptedStringConverterTest {

    private final EncryptedStringConverter converter = new EncryptedStringConverter();

    @BeforeAll
    static void injectKey() {
        // 注入测试密钥（Base64 编码的 32 字节）
        byte[] keyBytes = "flowpilot-test-key-32bytes-long!".getBytes();
        System.setProperty("flowpilot.dataEncryptionKey", Base64.getEncoder().encodeToString(keyBytes));
    }

    @Test
    void roundTrip() {
        String plain = "客户IT回复：策略已生效，包含敏感信息 13812345678";
        String stored = converter.convertToDatabaseColumn(plain);
        assertNotEquals(plain, stored);
        assertTrue(stored.startsWith("v1:"));
        assertEquals(plain, converter.convertToEntityAttribute(stored));
    }

    @Test
    void randomIvEachTime() {
        String a = converter.convertToDatabaseColumn("同一内容");
        String b = converter.convertToDatabaseColumn("同一内容");
        assertNotEquals(a, b, "每次加密应使用随机 IV");
        assertEquals("同一内容", converter.convertToEntityAttribute(a));
        assertEquals("同一内容", converter.convertToEntityAttribute(b));
    }

    @Test
    void plaintextPassThrough() {
        // 历史明文数据（无 v1: 前缀）原样返回，兼容旧库升级
        assertEquals("旧明文数据", converter.convertToEntityAttribute("旧明文数据"));
        assertEquals("", converter.convertToEntityAttribute(""));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void tamperedCiphertextFallsBack() {
        String stored = converter.convertToDatabaseColumn("机密内容");
        // 篡改密文 → GCM 校验失败 → 原样返回（不崩溃、不泄露错误）
        String tampered = stored.substring(0, stored.length() - 2) + "xx";
        assertEquals(tampered, converter.convertToEntityAttribute(tampered));
    }

    @Test
    void chineseAndEmojiRoundTrip() {
        String plain = "📧 邮件主题：验收单已签署 ✅ 客户：上海某某科技";
        String stored = converter.convertToDatabaseColumn(plain);
        assertEquals(plain, converter.convertToEntityAttribute(stored));
    }
}
