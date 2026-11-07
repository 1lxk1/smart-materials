package com.smartmaterials.security;

import com.smartmaterials.config.AppProperties;
import com.smartmaterials.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** JWT 签发与解析（HS256）。 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expireMinutes;

    public JwtService(AppProperties appProperties) {
        String secret = appProperties.getJwt().getSecret();
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 至少需要 32 个字符（当前 " + bytes.length + " 字节）");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireMinutes = appProperties.getJwt().getExpireMinutes();
    }

    public String createToken(SysUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("ldapUid", user.getLdapUid())
                .claim("name", user.getName())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpireMinutes() {
        return expireMinutes;
    }
}
