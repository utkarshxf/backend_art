package com.basic.JWTSecurity.auth.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Serves only the reviewed static export, never an API or arbitrary SPA fallback. */
@Configuration
public class AdminUiConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/admin-ui", "/admin-ui/login/");
        registry.addRedirectViewController("/admin-ui/", "/admin-ui/login/");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/admin-ui/_next/static/**")
                .addResourceLocations("classpath:/static/admin-ui/_next/static/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        registry.addResourceHandler("/admin-ui/**")
                .addResourceLocations("classpath:/static/admin-ui/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        if (path.endsWith("/")) return super.getResource(path + "index.html", location);
                        Resource resource = super.getResource(path, location);
                        if (resource != null) return resource;
                        String last = path.substring(path.lastIndexOf('/') + 1);
                        return last.contains(".") ? null : super.getResource(path + "/index.html", location);
                    }
                });
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        final String policy;
        try {
            policy = new ClassPathResource("admin-ui-csp.txt").getContentAsString(StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Bundled admin UI CSP is missing", e);
        }
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("Content-Security-Policy", policy);
                response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                response.setHeader("X-Content-Type-Options", "nosniff");
                return true;
            }
        }).addPathPatterns("/admin-ui", "/admin-ui/**");
    }
}
