package com.loopers.interfaces.api.security

import com.loopers.application.auth.AuthFacade
import com.loopers.domain.queue.QueueService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * POST /api/v1/orders 요청에 대해 입장 토큰(X-Entry-Token)을 검증하는 인터셉터.
 *
 * 1. X-Loopers-LoginId / LoginPw 헤더로 유저를 인증하여 userId를 확보한다.
 * 2. X-Entry-Token 헤더의 토큰이 해당 userId에 발급된 것인지 Redis에서 검증한다.
 * 3. 검증 통과 시 userId를 request attribute에 저장하여 하위 레이어에서 재사용한다.
 *
 * WebMvcConfig 에서 POST /api/v1/orders 경로로 등록된다.
 */
@Component
class QueueEntryInterceptor(
    private val authFacade: AuthFacade,
    private val queueService: QueueService,
) : HandlerInterceptor {

    companion object {
        const val HEADER_ENTRY_TOKEN = "X-Entry-Token"
        const val ATTR_VERIFIED_USER_ID = "queueVerifiedUserId"
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (request.method != "POST") return true

        // 1. 인증 — userId 확보
        val loginId = request.getHeader(AuthHeader.HEADER_LOGIN_ID)
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 없습니다.")
        val loginPw = request.getHeader(AuthHeader.HEADER_LOGIN_PW) ?: ""

        val user = authFacade.authenticate(loginId, loginPw)

        // 2. 입장 토큰 검증
        val token = request.getHeader(HEADER_ENTRY_TOKEN)
            ?: throw CoreException(ErrorType.BAD_REQUEST, "입장 토큰이 필요합니다. 대기열을 통해 토큰을 발급받으세요.")

        if (!queueService.validateToken(user.id, token)) {
            throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 입장 토큰입니다. 대기열을 통해 토큰을 발급받으세요.")
        }

        // 3. 검증된 userId를 request attribute에 저장 (중복 인증 방지용)
        request.setAttribute(ATTR_VERIFIED_USER_ID, user.id)

        return true
    }
}
