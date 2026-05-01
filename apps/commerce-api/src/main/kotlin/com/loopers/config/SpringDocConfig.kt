package com.loopers.config

import com.loopers.interfaces.api.security.LoginUser
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Configuration

@Configuration
class SpringDocConfig {
    init {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginUser::class.java)
    }
}
