package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @author Ace
 * @date 2025/5/26 12:50
 * 登录拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request , HttpServletResponse response,Object handler) throws Exception {
        //1.获取session
        HttpSession session = request.getSession();
        //2.获取用户信息
        Object user = session.getAttribute("user");
        //3.判断用户是否存在
        if (user == null) {
            //4.用户不存在，拦截
            // 设置状态码为401 Unauthorized
            response.setStatus(401);
            return false;
        }
        //5.存在，把用户信息存入ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        //6.放行请求
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户信息
        UserHolder.removeUser();
    }
}
