# 시퀀스 다이어그램: Loopers E-Commerce

---

## 1. 회원가입 흐름

### 목적
- 회원가입 시 유효성 검증 순서와 책임 분배 확인
- 에러 케이스별 응답 위치 확인

### 다이어그램

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctrl as UserV1Controller
    participant Facade as AuthFacade
    participant Email as Email VO
    participant Pw as Password VO
    participant Svc as UserService
    participant Repo as UserRepository
    participant DB as Database

    C->>Ctrl: POST /api/v1/users (SignupRequest)
    Ctrl->>Facade: signup(userId, rawPassword, name, birthDate, email)

    rect rgb(255, 245, 230)
        Note over Facade,Pw: Value Object 검증 (Application Layer)
        Facade->>Email: Email(email)
        alt 이메일 형식 오류
            Email-->>Facade: throw CoreException(BAD_REQUEST)
            Facade-->>Ctrl: 에러 전파
            Ctrl-->>C: 400 Bad Request
        end

        Facade->>Pw: Password.create(rawPassword, birthDate)
        Note over Pw: 길이/포맷/생년월일 패턴 검증
        alt 비밀번호 정책 위반
            Pw-->>Facade: throw CoreException(BAD_REQUEST)
            Facade-->>Ctrl: 에러 전파
            Ctrl-->>C: 400 Bad Request
        end
    end

    Facade->>Facade: passwordEncoder.encode(password)

    Facade->>Svc: createUser(userId, encryptedPw, name, birthDate, email)

    rect rgb(230, 245, 255)
        Note over Svc,Repo: Domain Service 검증 + CRUD
        Svc->>Svc: validateUserId(userId)
        alt userId 형식 오류
            Svc-->>Facade: throw CoreException(BAD_REQUEST)
            Facade-->>Ctrl: 에러 전파
            Ctrl-->>C: 400 Bad Request
        end

        Svc->>Svc: validateBirthDate(birthDate)
        alt 생년월일 미래
            Svc-->>Facade: throw CoreException(BAD_REQUEST)
            Facade-->>Ctrl: 에러 전파
            Ctrl-->>C: 400 Bad Request
        end

        Svc->>Repo: existsByUserId(userId)
        Repo->>DB: SELECT EXISTS
        DB-->>Repo: true/false
        Repo-->>Svc: Boolean
        alt userId 중복
            Svc-->>Facade: throw CoreException(CONFLICT)
            Facade-->>Ctrl: 에러 전파
            Ctrl-->>C: 409 Conflict
        end
    end

    Svc->>Repo: save(User)
    Repo->>DB: INSERT
    DB-->>Repo: saved entity
    Repo-->>Svc: User
    Svc-->>Facade: User
    Facade-->>Ctrl: User
    Ctrl-->>C: 200 OK (UserResponse)
```

### 📌 주요 확인 포인트

1. **검증 책임 분리**: AuthFacade에서 VO 검증/암호화, UserService에서 도메인 검증/CRUD
2. **검증 순서**: VO 검증 → 암호화 → 도메인 검증 → 중복 확인 → 저장
3. **비밀번호 암호화**: AuthFacade에서 BCrypt 적용 후 UserService에 암호화된 값 전달

### 설계 의도
- Controller는 DTO 변환만, AuthFacade가 유스케이스 조율
- Value Object가 자가 검증하여 유효하지 않은 상태의 객체 생성 방지
- UserService는 순수 도메인 검증과 CRUD에 집중

---

## 2. 인증 (로그인) 흐름

### 목적
- 헤더 기반 인증 흐름 확인
- 타이밍 공격 방지를 위한 처리 확인

### 다이어그램

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctrl as UserV1Controller
    participant Facade as AuthFacade
    participant Svc as UserService
    participant Repo as UserRepository
    participant PE as PasswordEncoder

    C->>Ctrl: GET /api/v1/users/me<br/>Headers: X-Loopers-LoginId, X-Loopers-LoginPw
    Ctrl->>Facade: authenticate(loginId, loginPw)

    Facade->>Svc: findByUserId(loginId)
    Svc->>Repo: findByUserId(loginId)

    alt 사용자 없음
        Repo-->>Svc: null
        Svc-->>Facade: null

        rect rgb(255, 230, 230)
            Note over Facade,PE: 타이밍 공격 방지
            Facade->>PE: matches(rawPw, dummyHash)
            Note over PE: BCrypt 연산 수행 (응답 시간 균일화)
            PE-->>Facade: false
        end

        Facade-->>Ctrl: throw CoreException(UNAUTHORIZED)<br/>"인증정보가 올바르지 않습니다"
        Ctrl-->>C: 401 Unauthorized
    else 사용자 존재
        Repo-->>Svc: User
        Svc-->>Facade: User
        Facade->>PE: matches(loginPw, user.encryptedPassword)
        alt 비밀번호 불일치
            PE-->>Facade: false
            Facade-->>Ctrl: throw CoreException(UNAUTHORIZED)<br/>"인증정보가 올바르지 않습니다"
            Ctrl-->>C: 401 Unauthorized
        else 비밀번호 일치
            PE-->>Facade: true
            Facade-->>Ctrl: User
            Ctrl-->>C: 200 OK (UserResponse)
        end
    end
```

### 📌 주요 확인 포인트

1. **타이밍 공격 방지**: AuthFacade에서 사용자 미존재 시에도 bcrypt 연산 수행하여 응답 시간 균일화
2. **에러 메시지 통일**: "인증정보가 올바르지 않습니다" (사용자 존재 여부 노출 방지)
3. **헤더 기반 인증**: 매 요청마다 인증 수행 (세션리스)
4. **책임 분리**: AuthFacade가 인증 로직 조율, UserService는 조회만 담당

### 설계 의도
- 보안 강화를 위해 실패 원인을 구분하지 않음
- bcrypt의 constant-time comparison 활용
- 인증 로직이 AuthFacade에 집중되어 다른 Controller에서도 재사용 가능

---

## 3. 주문 생성 흐름

### 목적
- 주문 생성 시 재고 확인/차감 흐름 확인
- 트랜잭션 경계 확인
- 상품 스냅샷 저장 시점 확인

### 다이어그램

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctrl as OrderController
    participant OSvc as OrderService
    participant PSvc as ProductService
    participant ORepo as OrderRepository
    participant PRepo as ProductRepository
    participant DB as Database

    C->>Ctrl: POST /api/v1/orders<br/>{items: [{productId, quantity}]}
    Ctrl->>OSvc: createOrder(userId, items)

    Note over OSvc,DB: 트랜잭션 시작

    loop 각 주문 상품에 대해
        OSvc->>PSvc: getProduct(productId)
        PSvc->>PRepo: findById(productId)
        PRepo->>DB: SELECT

        alt 상품 없음
            DB-->>PRepo: null
            PRepo-->>PSvc: null
            PSvc-->>OSvc: throw CoreException(NOT_FOUND)
            Note over OSvc,DB: 트랜잭션 롤백
            OSvc-->>Ctrl: 에러 전파
            Ctrl-->>C: 404 Not Found
        else 상품 존재
            DB-->>PRepo: ProductModel
            PRepo-->>PSvc: ProductModel
            PSvc-->>OSvc: ProductModel
        end

        OSvc->>OSvc: 재고 확인 (product.stock >= quantity)

        alt 재고 부족
            Note over OSvc,DB: 트랜잭션 롤백
            OSvc-->>Ctrl: throw CoreException(BAD_REQUEST)
            Ctrl-->>C: 400 Bad Request<br/>"재고가 부족합니다"
        end
    end

    Note over OSvc: 모든 상품 검증 완료

    loop 각 주문 상품에 대해
        OSvc->>PRepo: decreaseStock(productId, quantity)
        PRepo->>DB: UPDATE stock = stock - quantity
        Note over PRepo,DB: WHERE stock >= quantity (비관적 체크)
    end

    OSvc->>OSvc: Order 엔티티 생성
    OSvc->>OSvc: OrderItem 생성 (상품 스냅샷 포함)

    OSvc->>ORepo: save(Order with OrderItems)
    ORepo->>DB: INSERT orders, order_items
    DB-->>ORepo: saved

    Note over OSvc,DB: 트랜잭션 커밋

    ORepo-->>OSvc: Order
    OSvc-->>Ctrl: Order
    Ctrl-->>C: 200 OK (OrderResponse)
```

### 📌 주요 확인 포인트

1. **트랜잭션 범위**: 재고 확인 → 차감 → 주문 생성이 하나의 트랜잭션
2. **검증 우선**: 모든 상품 존재/재고 확인 후 차감 시작
3. **스냅샷 저장**: OrderItem에 주문 시점의 상품 정보(이름, 가격) 저장
4. **전체 실패 정책**: 하나라도 실패하면 전체 롤백

### 설계 의도
- 일관성 우선 (부분 주문 미지원)
- 동시성 이슈는 DB 레벨에서 처리 (`stock >= quantity` 조건)

---

## 4. 좋아요 등록/취소 흐름

### 목적
- 좋아요 토글 로직 확인
- 멱등성 처리 확인

### 다이어그램

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctrl as ProductV1Controller
    participant Svc as LikeService
    participant LRepo as LikeRepository
    participant PRepo as ProductRepository
    participant DB as Database

    rect rgb(230, 245, 230)
        Note over C,DB: 좋아요 등록
        C->>Ctrl: POST /api/v1/products/{productId}/likes
        Ctrl->>Svc: addLike(userId, productId)

        Svc->>PRepo: existsById(productId)
        PRepo->>DB: SELECT EXISTS

        alt 상품 없음
            DB-->>PRepo: false
            PRepo-->>Svc: false
            Svc-->>Ctrl: throw CoreException(NOT_FOUND)
            Ctrl-->>C: 404 Not Found
        end

        DB-->>PRepo: true
        PRepo-->>Svc: true

        Svc->>LRepo: findByUserIdAndProductId(userId, productId)
        LRepo->>DB: SELECT

        alt 이미 좋아요 존재 (멱등성)
            DB-->>LRepo: LikeModel
            LRepo-->>Svc: LikeModel
            Svc-->>Ctrl: LikeModel (기존 반환)
            Ctrl-->>C: 200 OK
        else 좋아요 없음
            DB-->>LRepo: null
            LRepo-->>Svc: null
            Svc->>LRepo: save(LikeModel)
            LRepo->>DB: INSERT
            DB-->>LRepo: saved
            LRepo-->>Svc: LikeModel

            Svc->>PRepo: increaseLikeCount(productId)
            PRepo->>DB: UPDATE products SET like_count = like_count + 1

            Svc-->>Ctrl: LikeModel
            Ctrl-->>C: 200 OK
        end
    end

    rect rgb(255, 240, 240)
        Note over C,DB: 좋아요 취소
        C->>Ctrl: DELETE /api/v1/products/{productId}/likes
        Ctrl->>Svc: removeLike(userId, productId)

        Svc->>LRepo: findByUserIdAndProductId(userId, productId)
        LRepo->>DB: SELECT

        alt 좋아요 없음 (멱등성)
            DB-->>LRepo: null
            LRepo-->>Svc: null
            Svc-->>Ctrl: void
            Ctrl-->>C: 200 OK
        else 좋아요 존재
            DB-->>LRepo: LikeModel
            LRepo-->>Svc: LikeModel
            Svc->>LRepo: delete(LikeModel)
            LRepo->>DB: DELETE
            DB-->>LRepo: done

            Svc->>PRepo: decreaseLikeCount(productId)
            PRepo->>DB: UPDATE products SET like_count = like_count - 1<br/>WHERE like_count > 0

            LRepo-->>Svc: void
            Svc-->>Ctrl: void
            Ctrl-->>C: 200 OK
        end
    end
```

### 📌 주요 확인 포인트

1. **멱등성**: 중복 등록/취소 시 에러 대신 200 OK 반환
2. **상품 존재 확인**: 좋아요 전 상품 유효성 검증
3. **유저-상품 유니크**: (userId, productId) 조합으로 중복 방지

### 설계 의도
- 클라이언트 재시도에 안전한 멱등성 설계
- 상품 삭제 시 좋아요 처리는 별도 고려 필요

---

## 5. 어드민 브랜드 삭제 흐름

### 목적
- 브랜드 삭제 시 연관 상품 처리 확인
- Soft Delete vs Hard Delete 결정

### 다이어그램

```mermaid
sequenceDiagram
    autonumber
    participant A as Admin Client
    participant Ctrl as BrandAdminController
    participant BSvc as BrandService
    participant PSvc as ProductService
    participant BRepo as BrandRepository
    participant PRepo as ProductRepository
    participant DB as Database

    A->>Ctrl: DELETE /api-admin/v1/brands/{brandId}<br/>Header: X-Loopers-Ldap

    Note over Ctrl: LDAP 인증 확인

    Ctrl->>BSvc: deleteBrand(brandId)

    BSvc->>BRepo: findById(brandId)
    BRepo->>DB: SELECT

    alt 브랜드 없음
        DB-->>BRepo: null
        BRepo-->>BSvc: null
        BSvc-->>Ctrl: throw CoreException(NOT_FOUND)
        Ctrl-->>A: 404 Not Found
    end

    DB-->>BRepo: BrandModel
    BRepo-->>BSvc: BrandModel

    Note over BSvc,DB: 트랜잭션 시작

    BSvc->>PSvc: deleteProductsByBrandId(brandId)
    PSvc->>PRepo: findAllByBrandId(brandId)
    PRepo->>DB: SELECT
    DB-->>PRepo: List<ProductModel>
    PRepo-->>PSvc: List<ProductModel>

    loop 각 상품에 대해
        PSvc->>PRepo: softDelete(product)
        Note over PRepo: deletedAt = now()
        PRepo->>DB: UPDATE deletedAt
    end

    PSvc-->>BSvc: done

    BSvc->>BRepo: softDelete(brand)
    Note over BRepo: deletedAt = now()
    BRepo->>DB: UPDATE deletedAt

    Note over BSvc,DB: 트랜잭션 커밋

    BRepo-->>BSvc: done
    BSvc-->>Ctrl: void
    Ctrl-->>A: 200 OK
```

### 📌 주요 확인 포인트

1. **Soft Delete**: 브랜드와 상품 모두 deletedAt 업데이트 (복구 가능)
2. **연쇄 처리**: 브랜드 삭제 시 해당 브랜드 상품도 함께 Soft Delete
3. **트랜잭션**: 브랜드-상품 삭제가 하나의 트랜잭션

### 설계 의도
- 실수로 삭제해도 복구 가능
- 기존 주문의 상품 정보는 스냅샷으로 보존되어 있어 영향 없음

---

## 6. 상품 목록 조회 (필터/정렬/페이징)

### 목적
- 조회 조건 처리 흐름 확인
- 성능 고려사항 확인

### 다이어그램

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant Ctrl as ProductController
    participant Svc as ProductService
    participant Repo as ProductRepository
    participant DB as Database

    C->>Ctrl: GET /api/v1/products?brandId=1&sort=latest&page=0&size=20
    Ctrl->>Svc: getProducts(brandId, sort, pageable)

    Svc->>Svc: 정렬 조건 변환
    Note over Svc: latest → createdAt DESC<br/>price_asc → price ASC<br/>likes_desc → likeCount DESC

    Svc->>Repo: findAllByCondition(brandId, sort, pageable)

    alt brandId 있음
        Repo->>DB: SELECT * FROM products<br/>WHERE brand_id = ? AND deleted_at IS NULL<br/>ORDER BY [sort] LIMIT ? OFFSET ?
    else brandId 없음
        Repo->>DB: SELECT * FROM products<br/>WHERE deleted_at IS NULL<br/>ORDER BY [sort] LIMIT ? OFFSET ?
    end

    DB-->>Repo: Page<ProductModel>
    Repo-->>Svc: Page<ProductModel>
    Svc-->>Ctrl: Page<ProductModel>
    Ctrl-->>C: 200 OK (Page<ProductResponse>)
```

### 📌 주요 확인 포인트

1. **Soft Delete 필터**: `deleted_at IS NULL` 조건 항상 포함
2. **인덱스 고려**: (brand_id, deleted_at, created_at) 복합 인덱스 필요
3. **좋아요순 정렬**: likeCount 컬럼 비정규화 또는 서브쿼리

### 설계 의도
- 기본 정렬은 latest (최신순)
- 페이징으로 대량 데이터 처리
- 좋아요순은 성능 고려하여 비정규화 권장

---

## 확장 고려사항

### 주문-결제 분리 (Event-Driven)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant OS as OrderService
    participant MQ as MessageQueue
    participant PS as PaymentService

    C->>OS: 주문 생성
    OS->>OS: 주문 저장 (status: PENDING)
    OS->>MQ: OrderCreatedEvent 발행
    OS-->>C: 202 Accepted (orderId)

    MQ->>PS: OrderCreatedEvent 수신
    PS->>PS: 결제 처리

    alt 결제 성공
        PS->>MQ: PaymentCompletedEvent 발행
        Note over OS: 주문 상태 → PAID
    else 결제 실패
        PS->>MQ: PaymentFailedEvent 발행
        Note over OS: 주문 상태 → FAILED<br/>재고 원복
    end
```

> **현재는 동기 방식**으로 구현하되, 향후 이벤트 기반으로 전환 가능하도록 서비스 경계를 명확히 분리합니다.

---

**문서 작성일**: 2026-02-11
**버전**: 1.0
