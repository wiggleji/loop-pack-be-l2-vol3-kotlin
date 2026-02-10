package com.loopers.interfaces.api.security

class AuthHeader(
    val loginId: String,
    val password: String,
) {
    companion object {
        const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"
    }

    /**
     * 보안처리를 위한 toString() override
     * @return [String] `AuthHeader(loginId='${loginId}', password='********')`
     */
    @Override
    override fun toString(): String {
        return "AuthHeader(loginId='$loginId', password='********')"
    }
}
