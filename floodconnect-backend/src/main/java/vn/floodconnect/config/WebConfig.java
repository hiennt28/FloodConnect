package vn.floodconnect.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import vn.floodconnect.security.AdminActiveInterceptor;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Path;

/**
 * Phục vụ tệp tải lên tại /files/**. Khi chuyển sang Cloudinary chỉ cần thay FileStorageService.
 * Chặn Admin bị vô hiệu hóa ở mọi API /api/admin/** (AdminActiveInterceptor).
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.storage.dir}") private String dir;
    private final AdminActiveInterceptor adminActive;

    @Override
    public void addInterceptors(InterceptorRegistry r) {
        r.addInterceptor(adminActive).addPathPatterns("/api/admin/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry r) {
        r.addResourceHandler("/files/**").addResourceLocations(Path.of(dir).toAbsolutePath().toUri().toString());
    }
}
