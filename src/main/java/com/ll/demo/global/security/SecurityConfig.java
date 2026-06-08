package com.ll.demo.global.security;

import com.ll.demo.global.rsData.RsData;
import com.ll.demo.global.security.oauth2.OAuth2CookieAuthorizationRequestRepository;
import com.ll.demo.global.security.oauth2.OAuth2FailureHandler;
import com.ll.demo.global.security.oauth2.OAuth2MemberService;
import com.ll.demo.global.security.oauth2.OAuth2SuccessHandler;
import com.ll.demo.standard.dto.util.Ut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
    private final CustomAuthenticationFilter customAuthenticationFilter;
    private final OAuth2MemberService oAuth2MemberService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final OAuth2CookieAuthorizationRequestRepository cookieAuthorizationRequestRepository;

    private final org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

    private final HttpSessionOAuth2AuthorizationRequestRepository authorizationRequestRepository =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    private org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver customAuthorizationRequestResolver(
            org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository) {

        org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver resolver =
                new org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository, "/oauth2/authorization"
                );

        resolver.setAuthorizationRequestCustomizer(builder -> {
            // 빌더 객체에서 빌드된 중간 요청 결과물을 꺼내서 현재 어떤 클라이언트(kakao/google)인지 안전하게 판별합니다.
            org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest request = builder.build();
            String ngrokBase = "https://boastingly-unthirsty-kannon.ngrok-free.dev";

            if (request.getRedirectUri().contains("kakao")) {
                builder.redirectUri(ngrokBase + "/login/oauth2/code/kakao");
            } else if (request.getRedirectUri().contains("google")) {
                builder.redirectUri(ngrokBase + "/login/oauth2/code/google");
            }
        });

        return resolver;
    }

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

                                // OAuth2 소셜 로그인
                                .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                                
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

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                        .sessionFixation(sessionFixation -> sessionFixation.none()) // 세션 고정 보호 임시 비활성화
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
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestRepository(authorizationRequestRepository)
                                // 수정: 주입받은 필드 객체를 인자로 전달
                                .authorizationRequestResolver(customAuthorizationRequestResolver(clientRegistrationRepository))
                        )
                        .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2MemberService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
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
        configuration.addAllowedOrigin("https://quote--me.vercel.app");
        configuration.addAllowedOrigin("https://www.quoteme.site");
        configuration.addAllowedOrigin("https://boastingly-unthirsty-kannon.ngrok-free.dev"); // ngrok url

        // 나머지 허용 설정
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public org.springframework.web.filter.OncePerRequestFilter cookieSameSiteFilter() {
        return new org.springframework.web.filter.OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response,
                    jakarta.servlet.FilterChain filterChain) throws jakarta.servlet.ServletException, java.io.IOException {

                filterChain.doFilter(request, response);

                // 응답 헤더의 Set-Cookie를 찾아 SameSite=None; Secure 속성을 강제로 보완
                java.util.Collection<String> headers = response.getHeaders("Set-Cookie");
                boolean first = true;
                for (String header : headers) {
                    if (header != null && header.contains("JSESSIONID")) {
                        if (!header.contains("SameSite")) {
                            header += "; SameSite=None";
                        }
                        if (!header.contains("Secure")) {
                            header += "; Secure";
                        }
                        if (first) {
                            response.setHeader("Set-Cookie", header);
                            first = false;
                        } else {
                            response.addHeader("Set-Cookie", header);
                        }
                    }
                }
            }
        };
    }

    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}

