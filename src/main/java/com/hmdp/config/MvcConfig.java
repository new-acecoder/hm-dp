package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author Ace
 * @date 2025/5/26 13:07
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //token刷新拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
        //登录拦截器
        registry.addInterceptor(new LoginInterceptor())
               .excludePathPatterns(
                       "/user/code",
                       "/user/login",
                       "/blog/hot",
                       "/shop/**",
                       "/shop-type/**",
                       "/upload/**",
                       "/voucher/**"
               ).order(1);
        }
}
