package com.ll.demo.global.security;

import com.ll.demo.global.rsData.RsData;
import com.ll.demo.standard.dto.util.Ut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
    private final CustomAuthenticationFilter customAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(authorizeHttpRequests ->
                        authorizeHttpRequests
                                // 화이트리스트
                                // 개발도구 
                                .requestMatchers("/", "/favicon.ico", "/error").permitAll()
                                .requestMatchers("/h2-console/**", "/actuator/**").permitAll()
                                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                                // 인증, 로그인, 회원가입 등
                                .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/guest-login").permitAll()
                                .requestMatchers("/api/auth/refresh").permitAll()
                                
                                //.requestMatchers(HttpMethod.POST, "/api/*/members", "/api/*/members/login").permitAll()
                                //.requestMatchers(HttpMethod.GET, "/g/*").permitAll()

                                // 로그아웃은 일단 permitAll
                                .requestMatchers("/api/auth/logout").permitAll()

                                // 이하 모든 api 및 요청은 인증 필수
                                .requestMatchers("/api/**").authenticated()
                                .anyRequest().authenticated()
                        )
                .headers(
                        headers ->
                                headers.frameOptions(
                                        frameOptions ->
                                                frameOptions.sameOrigin()
                                )
                )

                .csrf(
                        csrf ->
                                csrf.disable() // REST API 사용을 위해 CSRF 비활성화
                )
                .formLogin(
                        formLogin ->
                                formLogin
                                        .permitAll()
                )
                .exceptionHandling(
                        exceptionHandling -> exceptionHandling
                                .authenticationEntryPoint(
                                        (request, response, authException) -> {
                                            response.setContentType("application/json;charset=UTF-8");
                                            response.setStatus(403);
                                            response.getWriter().write(
                                                    Ut.json.toString(
                                                            RsData.of("403-1", request.getRequestURI() + ", " + authException.getLocalizedMessage())
                                                    )
                                            );
                                        }
                                )
                )
                .addFilterBefore(customAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();

    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 허용할 주소 (프론트엔드 & 로컬)
        configuration.addAllowedOrigin("http://localhost:5173");
        configuration.addAllowedOrigin("https://quoteme.shop");

        // 나머지 허용 설정
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}