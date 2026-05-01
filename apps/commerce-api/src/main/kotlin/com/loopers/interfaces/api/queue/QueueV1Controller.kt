package com.loopers.interfaces.api.queue

import com.loopers.application.queue.QueueFacade
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.security.LoginUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/queue")
class QueueV1Controller(
    private val queueFacade: QueueFacade,
) {

    @PostMapping("/enter")
    fun enter(
        @LoginUser user: User,
    ): ApiResponse<QueueV1Dto.QueuePositionResponse> {
        val entry = queueFacade.enter(user.id)
        return ApiResponse.success(QueueV1Dto.QueuePositionResponse.from(entry))
    }

    @GetMapping("/position")
    fun getPosition(
        @LoginUser user: User,
    ): ApiResponse<QueueV1Dto.QueuePositionResponse> {
        val entry = queueFacade.getPosition(user.id)
        return ApiResponse.success(QueueV1Dto.QueuePositionResponse.from(entry))
    }
}
