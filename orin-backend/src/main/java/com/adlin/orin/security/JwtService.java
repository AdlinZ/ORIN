package com.adlin.orin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT服务 - 生成和验证JWT Token
 */
@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret:orin-secret-key-change-this-in-production-environment}")
    private String secret;

    @Value("${jwt.expiration:86400000}") // 默认24小时
    private Long expiration;

    /**
     * 生成JWT Token
     */
    public String generateToken(String userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        return createToken(claims, userId);
    }

    /**
     * 生成带额外声明的Token
     */
    public String generateToken(String userId, String username, Map<String, Object> extraClaims) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("userId", userId);
        claims.put("username", username);
        return createToken(claims, userId);
    }

    /**
     * 创建Token
     */
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 从Token中提取用户ID
     */
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * 从Token中提取用户名
     */
    public String extractUsername(String token) {
        return extractClaim(token, claims -> claims.get("username", String.class));
    }

    /**
     * 从Token中提取过期时间
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * 从Token中提取声明
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 提取所有声明
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 检查Token是否过期
     */
    public Boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 验证Token
     */
    public Boolean validateToken(String token, String userId) {
        try {
            final String extractedUserId = extractUserId(token);
            return (extractedUserId.equals(userId) && !isTokenExpired(token));
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 验证Token（不检查用户ID）
     */
    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 刷新Token
     */
    public String refreshToken(String token) {
        try {
            final Claims claims = extractAllClaims(token);
            String userId = claims.getSubject();
            String username = claims.get("username", String.class);

            // 移除时间相关的声明
            claims.remove("iat");
            claims.remove("exp");

            return generateToken(userId, username, claims);
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            throw new RuntimeException("Invalid token for refresh");
        }
    }
}
