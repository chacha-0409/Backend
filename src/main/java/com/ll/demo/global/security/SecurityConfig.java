package com.ll.demo.global.security;

import com.ll.demo.standard.dto.util.Ut; // 프로젝트 패키지 구조에 맞춰 자동 임포트 확인
import com.ll.demo.global.rsData.RsData; // 프로젝트 패키지 구조에 맞춰 자동 임포트 확인
import com.ll.demo.global.security.oauth2.OAuth2MemberService;
import com.ll.demo.global.security.oauth2.OAuth2SuccessHandler;
import com.ll.demo.global.security.oauth2.OAuth2FailureHandler;
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
    private final OAuth2MemberService oAuth2MemberService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. CORS 설정 적용
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 2. CSRF 비활성화 (REST API 환경)
            .csrf(csrf -> csrf.disable())
            
            // 3. H2 콘솔 iframe 사용을 위한 헤더 설정
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
            )
            
            // 4. 요청별 접근 권한 통제 (화이트리스트 지정)
            .authorizeHttpRequests(auth -> auth
                // 비로그인 허용 화이트리스트
                .requestMatchers("/", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/h2-console/**", "/actuator/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                .requestMatchers("/api/auth/refresh").permitAll()
                .requestMatchers("/oauth2/authorization/**").permitAll()
                .requestMatchers("/api/auth/logout").permitAll()
                
                // 이하 모든 /api/** 요청 및 기타 요청은 인증 필수
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            
            // 5. 폼 로그인 기본 허용
            .formLogin(formLogin -> formLogin.permitAll())
            
            // 6. 예외 핸들러 지정 (인증 실패 시 403-1 커스텀 JSON 응답)
            .exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(403);
                    response.getWriter().write(
                        Ut.json.toString(
                            RsData.of("403-1", "/api/settings/profile, Full authentication is required to access this resource")
                        )
                    );
                })
            )
            
            // 7. OAuth2 소셜 로그인 설정 연동
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2MemberService))
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
            )
            
            // 8. JWT 커스텀 인증 필터 선행 배치
            .addFilterBefore(customAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 크로스 도메인 허용 주소 명세
        configuration.addAllowedOrigin("http://localhost:5173");
        configuration.addAllowedOrigin("https://quoteme.shop");
        configuration.addAllowedOrigin("https://quote--me.vercel.app");
        configuration.addAllowedOrigin("https://www.quoteme.site");
        configuration.addAllowedOrigin("http://3.38.96.140:8070");

        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        configuration.setAllowCredentials(true); // 크로스 도메인 간 쿠키 연동 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
