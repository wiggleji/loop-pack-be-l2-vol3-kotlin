package com.loopers.interfaces.api.security

/**
 * 컨트롤러 파라미터에 붙이는 마커 애노테이션.
 * LoginUserArgumentResolver 가 X-Loopers-LoginId / X-Loopers-LoginPw 헤더를 읽어
 * 인증된 User 도메인 객체로 변환해 주입한다.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class LoginUser
