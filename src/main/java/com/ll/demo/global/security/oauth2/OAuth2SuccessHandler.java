package com.ll.demo.global.security.oauth2;

import com.ll.demo.AppConfig;
import com.ll.demo.domain.member.member.entity.Member;
import com.ll.demo.domain.member.member.service.MemberService;
import com.ll.demo.global.security.AuthTokenService;
import com.ll.demo.standard.rq.Rq;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthTokenService authTokenService;
    private final MemberService memberService;
    private final Rq rq;

    @Value("${custom.oauth2.redirect-uri:http://localhost:5173/oauth2/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        Member member = oAuth2User.getMember();

        String accessToken = authTokenService.genToken(member, AppConfig.getAccessTokenExpirationSec());
        String refreshToken = memberService.genRefreshToken(member);

        rq.setCookie(response, "accessToken", accessToken, AppConfig.getAccessTokenExpirationSec());
        rq.setCookie(response, "refreshToken", refreshToken, 60 * 60 * 24 * 30);

        getRedirectStrategy().sendRedirect(request, response, redirectUri);
    }
}
