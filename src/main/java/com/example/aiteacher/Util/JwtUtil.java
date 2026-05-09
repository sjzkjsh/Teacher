package com.example.aiteacher.Util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private static long tokenExpiration = 60 * 60 * 1000L;//令牌过期时长 1h
    private static SecretKey tokenSignKey =
            Keys.hmacShaKeyFor("M0PKKI6pYGVWWfDZw90a0lTpGYX1d4AQ".getBytes());
    //签名秘钥，用字节数组转换成有效秘钥。
    public static String createToken(Long userId, String username){

        String token= Jwts.builder().setSubject("token")
                .setExpiration(new Date(System.currentTimeMillis()+tokenExpiration))//设置过期时间
                .claim("userId", userId)//添加自定义属性，设置id
                .claim("username", username)//添加自定义属性，设置username
                .signWith(tokenSignKey)//添加签名密匙，用于访问token的钥匙
                .compact();
        return token;
    }


    public static Claims parseToken(String token){
        if(token==null){
            throw new RuntimeException("未登录");
        }
        try{
            JwtParser parser = Jwts.parserBuilder().
                    setSigningKey(tokenSignKey).build();
            Jws<Claims> jwsClaims = parser.parseClaimsJws(token);//解析token，得到jws（带有签名的jwt）
            return jwsClaims.getBody();//返回claims（有效载荷）
        }catch (ExpiredJwtException e){
            throw new RuntimeException("token已过期");
        }catch (JwtException e){
            throw new RuntimeException("token违法");
        }
    }

}
