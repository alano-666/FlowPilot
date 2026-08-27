package com.flowpilot.auth;

import com.flowpilot.config.FlowPilotProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 签发与校验。
 * 密钥兜底：未配置或长度不足 32 字节时自动生成随机密钥并打印安全警告，
 * 保证开箱即用不崩溃（生产环境必须显式配置，见 docs/09）。
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final FlowPilotProperties props;
    private final SecretKey key;

    public JwtService(FlowPilotProperties props) {
        this.props = props;
        String secret = props.getAuth().getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            secret = Base64.getEncoder().encodeToString(random);
            log.warn("【安全警告】JWT 密钥未配置或过短，已使用随机密钥（重启后登录态失效）。"
                    + "生产环境必须设置 FLOWPILOT_AUTH_JWTSECRET（openssl rand -base64 32 生成），见 docs/09。");
        }
        // HS256 需要 >=32 字节密钥
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 Token，claims 携带 userId / username / role */
    public String createToken(Long userId, String username, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + props.getAuth().getTokenExpireHours() * 3600_000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /** 解析 Token，非法或过期抛异常 */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
