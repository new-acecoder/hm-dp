package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author Ace
 * @date 2025/5/26 13:07
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加登录拦截器
       registry.addInterceptor(new LoginInterceptor())
               .excludePathPatterns(
                       "/user/code",
                       "/user/login",
                       "/blog/hot",
                       "/shop/**",
                       "/shop-type/**",
                       "/upload/**",
                       "/voucher/**"
               );
    }
}
