# Week 7 멘토링 토론: ApplicationEvent + Outbox + Kafka 하이브리드 구조

## 배경

이번 주차에서 ApplicationEvent → Kafka 전환을 진행하면서,
멘토님이 "ApplicationEvent와 Kafka는 대체 관계가 아니라 역할이 다르다"고 하신 부분이 인상 깊었습니다.

이를 바탕으로 구현을 세 단계로 정리하고, V3(하이브리드)가 실무에서 합리적인 선택인지 의견을 구하고 싶습니다.

## 세 단계 요약

### V1 — ApplicationEvent 단독
- Facade에서 `applicationEventPublisher.publish()` → `@TransactionalEventListener(AFTER_COMMIT)` 핸들러가 부가 로직 처리
- 관심사 분리는 되지만, 서버 죽으면 이벤트 유실. 글로벌 전파 불가
```shell
OrderFacade [@Transactional]
  ├─ 핵심: 재고 차감 + 쿠폰 사용 + 주문 저장
  └─ applicationEventPublisher.publish(OrderPlacedEvent)
       ↓
  @TransactionalEventListener(AFTER_COMMIT) + @Async
  OrderPlacedEventHandler
       ├─ 결제 요청 (PG)
       └─ 행동 로깅 등 부가로직
```

### V2 — Outbox + Kafka 직접 호출 (현재 구현)
- Facade에서 `outboxEventService.save(topic, partitionKey, payload)` 직접 호출
- OutboxRelay가 5초 간격으로 polling → Kafka publish
- At-Least-Once 보장. 하지만 Facade가 topic, partitionKey 등 인프라를 직접 알고 있고, 캐시
  무효화도 Kafka를 경유 (5초 지연).
```shell
OrderFacade [@Transactional]
  ├─ 핵심: 재고 차감 + 쿠폰 사용 + 주문 저장
  └─ outboxEventService.save(                   ← Facade가 outbox를 직접 호출
       topic = "order-events",
       partitionKey = orderId,
       payload = OrderPlacedEvent(...)
     )
       ↓ [TX 커밋 — Outbox에 PENDING 상태로 저장]
OutboxRelay (@Scheduled 5초)
  └─ Kafka publish → markPublished()
       ↓
OrderEventConsumer (commerce-api)     → 결제 요청, 캐시 무효화
OrderEventConsumer (commerce-streamer) → 판매량 집계 (product_metrics)
```

### V3 — 하이브리드 (개선 방향)
- Facade는 `applicationEventPublisher.publish(OrderPlacedEvent)` 만 호출
- 로컬 리스너(`AFTER_COMMIT`): 캐시 무효화 등 JVM 내부 처리 → 즉시 실행
- 글로벌 리스너(`BEFORE_COMMIT`): Outbox 저장 → 같은 TX에 포함 → Kafka로 전파
- Facade가 인프라를 모름. 로컬/글로벌 분리. 인프라 교체 시 리스너만 수정
```shell
OrderFacade [@Transactional]
  ├─ 핵심: 재고 차감 + 쿠폰 사용 + 주문 저장
  └─ applicationEventPublisher.publish(OrderPlacedEvent)   ← 도메인 이벤트만 발행
       ↓

  ┌─ [로컬 리스너] ──────────────────────────────────────────┐
  │ @TransactionalEventListener(AFTER_COMMIT)             │
  │ LocalSideEffectHandler                                │
  │   ├─ 캐시 무효화 (Kafka 불필요)                            │
  │   └─ 기타 JVM 내부 처리                                   │
  └───────────────────────────────────────────────────────┘

  ┌─ [글로벌 리스너] ────────────────────────────────────────┐
  │ @TransactionalEventListener(BEFORE_COMMIT)            │
  │ OutboxEventSaver                                      │
  │   └─ outboxEventService.save(...)  ← 같은 TX에 포함      │
  └───────────────────────────────────────────────────────┘
       ↓ [TX 커밋 — Outbox PENDING + 핵심 데이터 원자적 저장]
OutboxRelay (@Scheduled)
  └─ Kafka publish
       ↓
OrderEventConsumer (commerce-api)     → 결제 요청
OrderEventConsumer (commerce-streamer) → 판매량 집계
```

## 토론하고 싶은 부분

멘토님이 "ApplicationEvent와 Kafka는 역할이 다르다"고 하셨는데, 위 세 버전이 그 차이를 올바르게 표현하고 있는지 확인받고 싶습니다.

제가 이해한 바:
- **ApplicationEvent** = JVM 내부에서 코드 간 결합도를 낮추는 도구 (로컬 이벤트, 관심사 분리)
- **Kafka** = 시스템 간 이벤트를 영속적으로 전달하는 인프라 (글로벌 이벤트, 전달 보장)

이를 기준으로 각 버전을 보면:
- **V1**: ApplicationEvent로 관심사 분리는 달성 → 하지만 전달 보장이 없음
- **V2**: Outbox + Kafka로 전달 보장은 달성 → 하지만 Facade가 인프라를 직접 호출하면서 ApplicationEvent의 역할(관심사 분리)을 잃어버림
- **V3**: ApplicationEvent(관심사 분리) + Outbox + Kafka(전달 보장)를 조합 → 두 역할이 각자의 레이어에서 동작

이 정리가 멘토님이 말씀하신 "역할이 다르다"는 의미와 맞는지, 그리고 실무에서 V3처럼 둘을 조합하는 것이 일반적인지 궁금합니다.
