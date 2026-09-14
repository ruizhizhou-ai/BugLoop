/**
 * 本文件注册登录校验拦截器，把未登录请求统一交给全局异常处理器转换为 40101。
 */
package com.wjfz.bugloop.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置，登录与注册接口开放访问，其余业务接口一律要求登录。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    private static final String[] PUBLIC_PATHS = {"/api/auth/login", "/api/auth/register"};

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_PATHS);
    }
}
