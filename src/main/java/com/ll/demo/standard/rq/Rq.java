package com.ll.demo.standard.rq;

import com.ll.demo.domain.member.member.entity.Member;
import com.ll.demo.domain.member.member.repository.MemberRepository;
import com.ll.demo.standard.dto.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Arrays;

@Component
@RequestScope
@RequiredArgsConstructor
public class Rq {

    private final HttpServletRequest request;
    private final HttpServletResponse response;   // @RequestScope 이므로 주입 가능
    private final JwtUtil jwtUtil;
    private final MemberRepository memberRepository;

    // 요청 내에서 Member 캐싱 (매번 DB 조회 방지)
    private Member cachedMember = null;
    private boolean memberResolved = false;       // null과 "아직 미조회"를 구분

    // Member 조회

    /**
     * AccessToken 쿠키 → JWT 파싱 → Member 반환
     * 인증 불필요 엔드포인트에서는 null 반환 (예외 없음)
     */
    public Member getMember() {
        if (memberResolved) {
            return cachedMember;
        }
        memberResolved = true;

        String accessToken = getCookieValue("accessToken", null);
        if (accessToken == null) {
            return null;
        }

        try {
            Long memberId = jwtUtil.getMemberIdFromToken(accessToken);
            cachedMember = memberRepository.findById(memberId).orElse(null);
        } catch (Exception e) {
            // 만료·변조된 토큰 → null 처리 (SecurityFilter에서 이미 차단되므로 여기서는 방어용)
            cachedMember = null;
        }

        return cachedMember;
    }

    /**
     * 인증 필수 엔드포인트에서 사용.
     * getMember()가 null이면 IllegalStateException 발생 → GlobalExceptionHandler가 401 응답.
     */
    public Member getMemberOrThrow() {
        Member member = getMember();
        if (member == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
        return member;
    }

    /**
     * 컨트롤러에서 rq.getMemberId() 한 줄로 ID를 꺼낼 수 있는 편의 메서드.
     * getMember()가 null이면 예외 발생.
     */
    public Long getMemberId() {
        return getMemberOrThrow().getId();
    }

    // 쿠키
    public void setCookie(String name, String value, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .path("/")
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * 기존 컨트롤러가 setCookie(response, ...) 로 호출하던 곳과의 하위 호환용.
     * response 파라미터를 무시하고 주입된 response를 사용.
     * @deprecated response 파라미터 제거 후 setCookie(name, value, maxAge) 사용 권장
     */
    @Deprecated
    public void setCookie(HttpServletResponse ignoredResponse, String name, String value, int maxAge) {
        setCookie(name, value, maxAge);
    }

    /** 쿠키 삭제 (maxAge=0, 빈 값) */
    public void deleteCookie(String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public String getCookieValue(String name, String defaultValue) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return defaultValue;
        }
        return Arrays.stream(cookies)
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(defaultValue);
    }

    public String getCurrentUrlPath() {
        return request.getRequestURI();
    }
}