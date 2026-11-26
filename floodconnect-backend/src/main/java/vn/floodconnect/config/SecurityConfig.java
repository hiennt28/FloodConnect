package vn.floodconnect.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.io.IOException;
import java.util.List;
import vn.floodconnect.security.AuthRateLimitFilter;
import vn.floodconnect.security.JwtAuthFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtFilter;
    private final AuthRateLimitFilter rateLimitFilter;
    @Value("${app.cors.allowed-origins}") private List<String> origins;

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> json(res, 401, "UNAUTHORIZED", "Chưa đăng nhập hoặc phiên đã hết hạn"))
                        .accessDeniedHandler((req, res, ex) -> json(res, 403, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này")))
                .authorizeHttpRequests(a -> a
                        // callback giả lập xác nhận chính người ủng hộ mới được gọi; các endpoint VNPay khác do cổng
                        // thanh toán/trình duyệt gọi thẳng, không kèm JWT, nên vẫn phải permitAll
                        .requestMatchers(HttpMethod.POST, "/api/payments/callback").authenticated()
                        // Swagger chỉ tồn tại khi SWAGGER_ENABLED=true (mặc định tắt, đường dẫn trả 404)
                        .requestMatchers("/api/auth/**", "/files/**", "/ws/**", "/error", "/api/payments/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // trang giới thiệu: chỉ con số tổng hợp, không có dữ liệu cá nhân (PublicStatsController)
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        // địa giới phường/xã (ranh giới hành chính công khai, không có dữ liệu cá nhân): khách đăng ký tổ chức
                        // cần nó để đặt ghim trụ sở và tự điền Tỉnh, Phường/Xã
                        .requestMatchers(HttpMethod.GET, "/api/geo/**").permitAll()
                        // khách được xem bảng tin, bài viết, bình luận, hồ sơ công khai
                        .requestMatchers(HttpMethod.GET, "/api/users/me", "/api/users/me/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*", "/api/posts/*/comments",
                                "/api/comments/*", "/api/comments/*/replies", "/api/users/search", "/api/users/*", "/api/users/*/posts").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/org/**").hasRole("RESCUE_ORG")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // Giới hạn tần suất các API xác thực theo IP, chạy trước khi đọc JWT (sau bộ lọc CORS nên lỗi 429 vẫn đọc được từ trình duyệt)
                .addFilterBefore(rateLimitFilter, JwtAuthFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(origins);
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }

    private static void json(HttpServletResponse res, int status, String code, String msg) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\",\"message\":\"" + msg + "\"}");
    }
}