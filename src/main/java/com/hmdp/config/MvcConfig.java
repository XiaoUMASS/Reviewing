package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.util.ArrayList;
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate redisTemplate;

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
        registry.addInterceptor(new LoginInterceptor(redisTemplate))
                .excludePathPatterns(paths);

    }
}
