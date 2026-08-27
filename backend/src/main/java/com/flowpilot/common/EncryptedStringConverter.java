package com.flowpilot.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 数据库列级加密转换器（AES-256-GCM）。
 *
 * 设计：
 *  - 密钥来自环境变量 FLOWPILOT_DATA_ENCRYPTION_KEY（Base64 编码的 32 字节）；
 *    未配置时为开发明文模式（启动时打印安全警告），生产必须配置；
 *  - 密文格式：v1:<随机IV(Base64)>:<密文(Base64)>，每次写入随机 IV，GCM 自带完整性校验；
 *  - 向后兼容：读旧明文数据（无 v1: 前缀）时原样返回，实现无迁移平滑升级；
 *  - 应用于聊天消息 content/raw_json 等敏感列（@Convert）。
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(EncryptedStringConverter.class);
    private static final String PREFIX = "v1:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKeySpec key;
    private static volatile boolean warned = false;

    /** 从环境变量/系统属性加载密钥（Base64 32 字节 → AES-256） */
    private static SecretKeySpec key() {
        if (key != null) {
            return key;
        }
        synchronized (EncryptedStringConverter.class) {
            if (key != null) {
                return key;
            }
            // 系统属性优先（测试注入用），生产走环境变量
            String raw = System.getProperty("flowpilot.dataEncryptionKey");
            if (raw == null || raw.isBlank()) {
                raw = System.getenv("FLOWPILOT_DATA_ENCRYPTION_KEY");
            }
            if (raw == null || raw.isBlank()) {
                if (!warned) {
                    warned = true;
                    log.warn("【安全警告】未配置 FLOWPILOT_DATA_ENCRYPTION_KEY，聊天记录将以明文存储。"
                            + "生产环境必须配置（生成方式见 docs/09-数据安全与隐私保护.md）");
                }
                return null;
            }
            try {
                byte[] bytes = Base64.getDecoder().decode(raw.trim());
                if (bytes.length != 32) {
                    throw new IllegalArgumentException("密钥长度必须为 32 字节");
                }
                key = new SecretKeySpec(bytes, "AES");
                return key;
            } catch (Exception e) {
                throw new IllegalStateException("FLOWPILOT_DATA_ENCRYPTION_KEY 无效（需 Base64 编码的 32 字节）: "
                        + e.getMessage());
            }
        }
    }

    @Override
    public String convertToDatabaseColumn(String plain) {
        if (plain == null || plain.isEmpty() || key() == null) {
            return plain;
        }
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("数据加密失败，按明文写入（请检查加密配置）: {}", e.getMessage());
            return plain;
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || stored.isEmpty() || !stored.startsWith(PREFIX) || key() == null) {
            return stored;
        }
        try {
            String[] parts = stored.substring(PREFIX.length()).split(":");
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 历史明文数据或密钥变更：原样返回，不中断业务
            log.debug("数据解密失败，按原始值返回: {}", e.getMessage());
            return stored;
        }
    }
}
