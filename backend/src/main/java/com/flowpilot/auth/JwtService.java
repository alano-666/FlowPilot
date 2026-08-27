package com.flowpilot.auth;

import com.flowpilot.config.FlowPilotProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验。
 */
@Service
public class JwtService {

    private final FlowPilotProperties props;
    private final SecretKey key;

    public JwtService(FlowPilotProperties props) {
        this.props = props;
        // HS256 需要 >=32 字节密钥
        this.key = Keys.hmacShaKeyFor(props.getAuth().getJwtSecret().getBytes(StandardCharsets.UTF_8));
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
