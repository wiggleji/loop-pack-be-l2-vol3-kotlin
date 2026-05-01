package com.loopers.interfaces.api.security

import com.loopers.application.auth.AuthFacade
import com.loopers.domain.user.User
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * @LoginUser 파라미터를 인증된 User 도메인 객체로 변환하는 ArgumentResolver.
 *
 * X-Loopers-LoginId / X-Loopers-LoginPw 헤더를 읽어 AuthFacade.authenticate() 를 호출한다.
 * 헤더가 없거나 인증에 실패하면 AuthFacade 에서 CoreException(UNAUTHORIZED) 를 던진다.
 */
@Component
class LoginUserArgumentResolver(
    private val authFacade: AuthFacade,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(LoginUser::class.java) &&
            parameter.parameterType == User::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): User {
        val loginId = webRequest.getHeader(AuthHeader.HEADER_LOGIN_ID) ?: ""
        val loginPw = webRequest.getHeader(AuthHeader.HEADER_LOGIN_PW) ?: ""
        return authFacade.authenticate(loginId, loginPw)
    }
}
