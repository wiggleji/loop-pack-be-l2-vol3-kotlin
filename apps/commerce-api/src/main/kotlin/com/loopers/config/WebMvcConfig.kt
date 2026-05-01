package com.loopers.config

import com.loopers.interfaces.api.security.AdminAuthInterceptor
import com.loopers.interfaces.api.security.LoginUserArgumentResolver
import com.loopers.interfaces.api.security.QueueEntryInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val loginUserArgumentResolver: LoginUserArgumentResolver,
    private val adminAuthInterceptor: AdminAuthInterceptor,
    private val queueEntryInterceptor: QueueEntryInterceptor,
) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(loginUserArgumentResolver)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns("/api-admin/**")

        registry.addInterceptor(queueEntryInterceptor)
            .addPathPatterns("/api/v1/orders")
    }
}
