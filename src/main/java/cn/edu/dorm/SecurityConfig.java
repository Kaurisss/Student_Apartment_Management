package cn.edu.dorm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.HttpStatus;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/dashboard", "/repairs", "/housing", "/students", "/dormitories", "/beds", "/users", "/api/csrf", "/api/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().authenticated())
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")))
            .formLogin(f -> f.loginPage("/").loginProcessingUrl("/api/login")
                .successHandler((request,response,auth) -> response.setStatus(204))
                .failureHandler((request,response,error) -> { response.setStatus(401); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"message\":\"账号或密码错误\"}"); }).permitAll())
            .logout(l -> l.logoutUrl("/api/logout").logoutSuccessHandler((request,response,auth) -> response.setStatus(204))).build();
    }
    @Bean UserDetailsService users(JdbcTemplate jdbc) {
        return username -> jdbc.query("SELECT username,password_hash,role FROM `user` WHERE username=?", rs -> {
            if (!rs.next()) throw new UsernameNotFoundException("用户不存在");
            return User.withUsername(rs.getString(1)).password(rs.getString(2)).roles(rs.getString(3)).build();
        }, username);
    }
    @Bean PasswordEncoder passwordEncoder() { return new CoursePasswordEncoder(); }

    // 与作业2中的 pbkdf2_sha256$迭代次数$盐值$Base64 哈希格式兼容。
    static class CoursePasswordEncoder implements PasswordEncoder {
        private final SecureRandom random = new SecureRandom();
        @Override public String encode(CharSequence raw) {
            byte[] salt = new byte[18]; random.nextBytes(salt);
            String saltText = Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
            return "pbkdf2_sha256$120000$" + saltText + "$" + derive(raw, saltText, 120000);
        }
        @Override public boolean matches(CharSequence raw, String encoded) {
            try {
                String[] parts = encoded.split("\\$", -1);
                if (parts.length != 4 || !parts[0].equals("pbkdf2_sha256")) return false;
                int iterations = Integer.parseInt(parts[1]);
                if (iterations < 10000 || iterations > 1000000) return false;
                return MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]),
                    Base64.getDecoder().decode(derive(raw, parts[2], iterations)));
            } catch (RuntimeException e) { return false; }
        }
        private String derive(CharSequence password, String salt, int iterations) {
            PBEKeySpec spec = new PBEKeySpec(password.toString().toCharArray(), salt.getBytes(StandardCharsets.UTF_8), iterations, 256);
            try { return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); }
            catch (Exception e) { throw new IllegalStateException("无法计算密码哈希", e); }
            finally { spec.clearPassword(); }
        }
    }
}
