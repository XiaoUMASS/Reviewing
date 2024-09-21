package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        ArrayList<String> paths = new ArrayList<>();
        paths.add("/user/code");
        paths.add("/user/login");
        paths.add("blog/hot");
        paths.add("/shop/**");
        paths.add("/shop-type/**");
        paths.add("/voucher/**");
        //注册拦截器
        //LoginInterceptor拦截登陆用户才能执行的请求
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(paths).order(1);
        //RefreshTokenInterceptor拦截所有请求，将有效的登陆用户存入ThreadLocal中，放行所有请求，
        //主要目的是刷新Token，让非登陆用户也可以使用部分功能，本拦截器先执行（order越小越优先）
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
    }
}
