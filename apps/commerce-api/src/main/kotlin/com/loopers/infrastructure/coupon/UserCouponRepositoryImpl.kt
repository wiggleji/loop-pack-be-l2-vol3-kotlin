package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import org.springframework.stereotype.Repository

@Repository
class UserCouponRepositoryImpl(
    private val userCouponJpaRepository: UserCouponJpaRepository,
) : UserCouponRepository {

    override fun save(userCoupon: UserCoupon): UserCoupon {
        val entity = if (userCoupon.id > 0L) {
            userCouponJpaRepository.getReferenceById(userCoupon.id).apply {
                updateStatus(userCoupon.status)
                userCoupon.usedOrderId?.let { updateUsedOrderId(it) }
            }
        } else {
            UserCouponEntity.from(userCoupon)
        }
        return userCouponJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): UserCoupon? =
        userCouponJpaRepository.findById(id)
            .filter { it.deletedAt == null }
            .map { it.toDomain() }
            .orElse(null)

    override fun findByUserId(userId: Long): List<UserCoupon> =
        userCouponJpaRepository.findAllByUserId(userId).map { it.toDomain() }

    override fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean =
        userCouponJpaRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId)
}
