package com.clanmanager.clanmanager.config;

import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class PasswordChangeRequiredInterceptor implements HandlerInterceptor {

    public static final String MEMBER_HEADER = "X-Clan-Member-Id";

    private final MemberRepository memberRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || request.getRequestURI().startsWith("/api/auth/")) {
            return true;
        }

        String memberIdHeader = request.getHeader(MEMBER_HEADER);
        if (memberIdHeader == null || memberIdHeader.isBlank()) {
            return true;
        }

        Long memberId;
        try {
            memberId = Long.valueOf(memberIdHeader);
        } catch (NumberFormatException exception) {
            return true;
        }

        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null || !Boolean.TRUE.equals(member.getMustChangePassword())) {
            return true;
        }

        String allowedPasswordPath = "/api/members/" + memberId + "/password";
        if ("PATCH".equalsIgnoreCase(request.getMethod()) && allowedPasswordPath.equals(request.getRequestURI())) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"임시 비밀번호를 먼저 변경해 주세요.\"}"
        );
        return false;
    }
}
