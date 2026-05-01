package com.loopers.interfaces.api.security

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * /api-admin 경로에 대한 LDAP 인증 인터셉터.
 *
 * X-Loopers-Ldap 헤더가 없거나 값이 올바르지 않으면 CoreException(UNAUTHORIZED) 를 던진다.
 * WebMvcConfig 에서 /api-admin 패턴으로 등록된다.
 */
@Component
class AdminAuthInterceptor : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val ldap = request.getHeader(AdminHeader.HEADER_LDAP)
        if (ldap != AdminHeader.LDAP_ADMIN_VALUE) {
            throw CoreException(ErrorType.UNAUTHORIZED, "어드민 인증에 실패했습니다.")
        }
        return true
    }
}
