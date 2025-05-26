package com.hmdp.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @author Ace
 * @date 2025/5/26 12:50
 * 登录拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 此拦截器不关心 Token 的具体内容，只在意 ThreadLocal 中是否有用户
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request , HttpServletResponse response,Object handler) throws Exception {
        //1.判断是否需要拦截(Tread Local中是否有用户信息)
        if (UserHolder.getUser() == null) {
            //没有，需要拦截，设置状态码为401
            response.setStatus(401);
            return false;
        }
        //2.如果有用户信息，放行请求
        return true;
    }
}
