package com.example.aiteacher.Config;



import com.example.aiteacher.Entity.loginUser;
import com.example.aiteacher.Util.JwtUtil;
import com.example.aiteacher.Util.LoginUserHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 前端登录后，后续请求都将JWT，放置于HTTP请求的Header中，其Header的key为`access-token`。
        String token = request.getHeader("access-token");

        // 校验token是否为空
        if (token == null || token.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或token已过期\",\"data\":null}");
            return false;
        }

        try {
            // 解析该token，如果成功则放行，如果失败，则拦截。
            Claims claims = JwtUtil.parseToken(token);

            Long userId = claims.get("userId", Long.class);//从token中解析出userId
            String username = claims.get("username", String.class);//从token中解析出username

            LoginUserHolder.setLoginUser(new loginUser(userId, username));//将loginUser放入threadlocal中。

            // 放行。
            return true;
        } catch (Exception e) {
            // token解析失败，返回401
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"token无效或已过期\",\"data\":null}");
            return false;
        }
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //释放线程资源。
        LoginUserHolder.clearLoginUser();
    }
}
