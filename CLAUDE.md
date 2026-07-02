# QuantLab — CLAUDE.md

> 이 파일은 Claude Code가 프로젝트 컨텍스트를 유지하기 위한 핵심 문서다.
> 개발 진행 중 변경사항이 생기면 이 파일을 먼저 업데이트할 것.

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|---|---|
| 프로젝트명 | QuantLab |
| 목적 | 국내 주식 기술적 지표 스코어링 + 관심 종목 실시간 모니터링 |
| 범위 | 국내 주식 한정 / 조회·분석만 (주문 기능 없음) |
| 개발자 스택 | Java/Spring 백엔드 개발자, Python 병행 학습 중 |

---

## 2. 디렉토리 구조

```
quantlab/
├── CLAUDE.md                   # 이 파일
├── README.md
├── .gitignore
├── docker-compose.yml
│
├── backend/                    # Spring Boot 멀티모듈 프로젝트
│   ├── build.gradle            # 루트 빌드 파일
│   ├── settings.gradle         # 모듈 정의 (api, core, common, event)
│   ├── .env.example            # 환경변수 템플릿
│   ├── api/                    # REST 컨트롤러, Swagger, 전역 예외 핸들러
│   │   └── src/main/java/com/quantlab/
│   │       ├── QuantLabApplication.java
│   │       ├── common/controller/   # HealthCheck
│   │       ├── common/config/       # SwaggerConfig
│   │       ├── common/exception/    # GlobalExceptionHandler
│   │       └── stock/controller/    # StockController
│   ├── core/                   # 서비스, 리포지토리, 도메인, DTO
│   │   └── src/main/java/com/quantlab/
│   │       ├── common/              # TimeBaseEntity, Config, Exception
│   │       ├── stock/               # 종목 도메인, 서비스, CSV 적재
│   │       ├── price/               # 시세 도메인, 수집 서비스, 스케줄러
│   │       └── infra/kis/           # KIS API 클라이언트, 토큰 관리
│   ├── common/                 # 공유 유틸 (java-library, Spring 미포함)
│   │   └── src/main/java/com/quantlab/common/exception/ErrorCode.java
│   └── event/                  # Kafka 이벤트 (향후 확장)
│
├── quant-engine/               # Python FastAPI 퀀트 계산 서버
│   ├── main.py
│   ├── requirements.txt
│   └── calculator/
│       ├── indicators.py       # RSI, MACD, 볼린저밴드 등
│       └── scorer.py           # 스코어링 & 등급 산출
│
└── frontend/                   # React + TypeScript
    └── src/
        ├── pages/
        ├── components/
        ├── hooks/
        │   └── useWebSocket.ts
        └── api/
```

---

## 3. 기술 스택

### Backend (Spring Boot)
| 항목 | 버전 / 선택 |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.x |
| 빌드 | Gradle |
| ORM | Spring Data JPA |
| DB | MySQL 8.x |
| Cache | Redis 7.x |
| 실시간 | Spring WebSocket + STOMP |
| 스케줄 | Spring Scheduler |
| API 문서 | SpringDoc OpenAPI (Swagger UI) |
| 외부 HTTP | RestClient (Spring 6.1 기본, WebClient 아님) |

### Quant Engine (Python)
| 항목 | 버전 / 선택 |
|---|---|
| Python | 3.11+ |
| Framework | FastAPI |
| 지표 계산 | pandas, numpy, pandas-ta |
| 서버 | uvicorn |

### Frontend (React)
| 항목 | 선택 |
|---|---|
| Framework | React 18 + TypeScript |
| 차트 | TradingView Lightweight Charts |
| WebSocket | SockJS + StompJS |
| 상태 관리 | React Query (서버 상태) + useState (로컬) |
| 스타일 | Tailwind CSS |

---

## 4. 외부 API

### 토스증권 Open API
- **Base URL:** `https://openapi.tossinvest.com`
- **스펙 파일:** `toss-openapi.json` (프로젝트 루트)
- **인증:** OAuth 2.0 Client Credentials (`client_id` + `client_secret`) → access token (Bearer)
  - 토큰 1개만 유효 (재발급 시 이전 토큰 즉시 무효화)
  - 유효시간: 86400초(24h) → Redis 캐싱, 만료 1h 전 자동 갱신
- **주요 엔드포인트:**
  - `POST /oauth2/token` — 토큰 발급 (application/x-www-form-urlencoded)
  - `GET /api/v1/prices?symbols={codes}` — 현재가 (최대 200종목 일괄)
  - `GET /api/v1/candles?symbol={code}&interval=1d` — 일봉 OHLCV (최대 200개)
  - `GET /api/v1/stocks?symbols={codes}` — 종목 기본 정보
  - `GET /api/v1/orderbook?symbol={code}` — 호가
- **Rate Limit 그룹:** `MARKET_DATA` (현재가/호가), `MARKET_DATA_CHART` (캔들), `STOCK` (종목정보)
- **WebSocket:** 추후 지원 예정 (현재 미제공)

---

## 5. 환경변수 (application.yml)

```yaml
toss:
  client-id: ${TOSS_CLIENT_ID}
  client-secret: ${TOSS_CLIENT_SECRET}
  base-url: ${TOSS_BASE_URL:https://openapi.tossinvest.com}

python-engine:
  base-url: ${PYTHON_ENGINE_URL:http://localhost:8000}

spring:
  datasource:
    url: jdbc:mysql://localhost:3308/quantlab?serverTimezone=Asia/Seoul
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
```

`.env` 파일로 로컬 관리, `.gitignore`에 반드시 포함.

---

## 6. 핵심 기능 & API 명세

### 관심 종목
| Method | URI | 설명 |
|---|---|---|
| GET | `/api/stocks/search?q={keyword}` | 종목명/코드 검색 |
| GET | `/api/watchlist` | 관심 종목 목록 |
| POST | `/api/watchlist/{stockCode}` | 관심 종목 등록 |
| DELETE | `/api/watchlist/{stockCode}` | 관심 종목 해제 |

### 시세 & 차트
| Method | URI | 설명 |
|---|---|---|
| GET | `/api/stocks/{stockCode}/price` | 현재가 조회 |
| GET | `/api/stocks/{stockCode}/chart?period=daily` | 일봉 차트 데이터 |

### 스코어
| Method | URI | 설명 |
|---|---|---|
| GET | `/api/stocks/{stockCode}/score` | 종목 스코어 조회 |
| GET | `/api/dashboard/scores` | 관심 종목 전체 스코어 랭킹 |

### WebSocket
- 엔드포인트: `ws://localhost:8080/ws/stocks`
- 구독 토픽: `/topic/price/{stockCode}`
- 메시지: `{ code, currentPrice, changeRate, volume, timestamp }`

### Python 퀀트 엔진
| Method | URI | 설명 |
|---|---|---|
| POST | `/calculate/score/batch` | 관심 종목 일괄 스코어 계산 |

---

## 7. 기술적 지표 스코어링 로직

### 계산 지표 (v1)
| 지표 | 파라미터 | 점수 산출 기준 |
|---|---|---|
| RSI | 14일 | 30↓ 과매도(100점), 70↑ 과매수(0점), 선형보간 |
| MACD | 12/26/9 | 시그널 상향돌파 → BUY(100), 하향돌파 → SELL(0), 유지 → 50 |
| 볼린저밴드 | 20일, 2σ | 하단 근접(100점), 상단 근접(0점) |
| 거래량 비율 | 20일 이평 대비 | 2배 이상(100점), 0.5배 이하(0점) |
| 이동평균 배열 | 5/20/60일 | 정배열 완전(100점), 역배열(0점) |

### 가중 합산 (v1 균등)
```
총점 = RSI×0.2 + MACD×0.2 + BB×0.2 + Volume×0.2 + MA×0.2
등급 = A(80↑), B(60↑), C(40↑), D(40↓)
```

### 실행 시점
- **배치:** 매일 16:00 (장 마감 후) Spring Scheduler → Python 엔진 호출
- **단건:** 관심 종목 등록 시 즉시 계산 (최신 스코어 보여주기 위해)

---

## 8. 개발 Phase 현황

### ✅ Phase 0 — 기획 완료
- 프로젝트 방향, 기술 스택, API 명세, DB 스키마 확정

### ✅ Phase 1 — 데이터 파이프라인 (완료)
- [x] Spring Boot 프로젝트 초기 세팅 (멀티모듈: api, core, common, event)
- [x] 토스증권 API 연동 모듈 (토큰 발급 + 시세/캔들 조회)
- [x] 종목 마스터 적재 (KRX CSV + ApplicationRunner 자동 적재)
- [x] 일별 OHLCV 수집 Scheduler (매일 16:00 MON-FRI)

### ⬜ Phase 2 — 도메인 API
- [ ] 종목 검색 API
- [ ] 관심 종목 CRUD
- [ ] 현재가 / 차트 API

### ⬜ Phase 3 — Python 퀀트 엔진
- [ ] FastAPI 프로젝트 세팅
- [ ] 지표 계산 (RSI, MACD, 볼린저밴드, 거래량, 이평)
- [ ] 스코어링 + Spring 연동

### ⬜ Phase 4 — WebSocket 실시간
- [ ] STOMP 세팅
- [ ] KIS WebSocket 구독 → 브로드캐스트
- [ ] Redis 시세 캐싱

### ⬜ Phase 5 — 프론트엔드
- [ ] React 초기 세팅
- [ ] 관심 종목 리스트 + 실시간 시세
- [ ] 종목 상세 차트
- [ ] 스코어 대시보드

### ⬜ Phase 6 — 배포
- [ ] Docker Compose
- [ ] GitHub Actions CI/CD
- [ ] AWS EC2 배포

---

## 9. 코드 컨벤션

### 9.1 프로젝트 구조 (멀티모듈)

| 모듈 | 역할 | 의존 |
|------|------|------|
| **api** | REST 컨트롤러, Swagger 설정, 글로벌 예외 핸들러 | core, event |
| **core** | 서비스, 리포지토리, 도메인, DTO, Mapper | - |
| **event** | Kafka Producer/Consumer, 이벤트 설정 | core |
| **common** | 공유 유틸, 설정 (java-library, Spring Boot 미포함) | - |

패키지는 Feature 기반으로 구성:
```
com.quantlab/{feature}/
├── controller/    # api 모듈
├── service/       # core 모듈
├── repository/    # core 모듈
├── domain/        # JPA 엔티티, Value Object
├── dto/request/, dto/response/, dto/mapper/
└── exception/     # ErrorCode enum
```

### 9.2 엔티티 (Entity)

- `@Entity` + `@Getter` + `@NoArgsConstructor(access = PROTECTED)`
- `@Builder`는 **private 생성자**에 부착
- 객체 생성은 **정적 팩토리 메서드 `of()`** 로 노출
- 생성자 내부에서 유효성 검증 (`Assert.hasText()`, `Assert.notNull()`)
- **`@Data`, `@Setter`, `@AllArgsConstructor` 사용 금지**
- 관계 매핑: `fetch = LAZY` 기본, `foreignKey = @ForeignKey(NO_CONSTRAINT)`
- ID: `@GeneratedValue(strategy = IDENTITY)`, 컬럼명 `{entity}_id`
- Enum: `@Enumerated(STRING)`
- `TimeBaseEntity` 상속 (`@CreatedDate`, `@LastModifiedDate`)

메서드 순서: 상수 → 필드 → 생성자 → `of()` → 비즈니스 메서드 → private 유틸

### 9.3 DTO

- **Java Record** 사용 (불변)
- Validation: `@NotBlank`, `@NotNull`, `@Min`, `@Max`, `@Pattern` + 한국어 에러 메시지
- 날짜: `@JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")`
- 네이밍: `{Action}{Entity}Request`, `{Entity}{Detail}Response`
- 공통 페이지 응답: `PageResponse<T>` (content, size, hasNext) — `Slice<T>` 기반

### 9.4 Mapper

- 정적 유틸 클래스: `@NoArgsConstructor(access = PRIVATE)`, 모든 메서드 `static`
- 메서드명: `to{Target}` (e.g., `toStock`, `toStockDetailResponse`)

### 9.5 Enum

- `@Getter` + `@RequiredArgsConstructor`, `label` 필드에 한국어 표시명
- `of(String)` 정적 팩토리로 표시명 → enum 변환, 실패 시 `ValidationException`

### 9.6 컨트롤러

- `@RestController` + `@RequiredArgsConstructor` + `@RequestMapping("/api/{resource}")`
- 응답: `ResponseEntity<T>`로 감싸서 반환
- Swagger: `@Tag`, `@Operation`, `@ApiResponse(useReturnTypeSchema = true)`
- 인증: `@JwtAuthorization` (인증 필요), `@NoAuth` (공개 API)
- 요청 검증: `@Valid @RequestBody`

### 9.7 서비스

- `@Service` + `@RequiredArgsConstructor`
- 의존성: `private final` 필드 (생성자 주입만 사용, `@Autowired` 금지)
- 읽기: `@Transactional(readOnly = true)`, 쓰기: `@Transactional`
- 엔티티 조회 실패: `.orElseThrow(() -> new NotFoundException(ErrorCode))`
- DTO 변환은 Mapper에 위임

### 9.8 리포지토리

- JPA: `extends JpaRepository<Entity, ID>`
- 복잡한 쿼리: QueryDSL (`*QueryRepository` 인터페이스 + `*QueryRepositoryImpl` 구현)
- 페이징: `Slice<T>` 사용 (`Page<T>` 대신 — count 쿼리 생략)
- N+1 방지: `fetchJoin()` 활용
- WHERE 절: private 헬퍼 메서드로 분리

### 9.9 예외 처리

- `ErrorCode` 인터페이스 → 도메인별 ErrorCode enum (`{PREFIX}_{NUMBER}`, e.g., `ST_000`)
- 커스텀 예외: `RuntimeException` 상속 (`NotFoundException`, `ValidationException`)
- `@RestControllerAdvice` + `@ExceptionHandler`로 전역 처리
- 에러 응답: `record ErrorResponseTemplate(String message, String code)`

### 9.10 Lombok 규칙

| 사용 | 위치 |
|------|------|
| `@Getter` | Entity, DTO, Enum, Exception |
| `@NoArgsConstructor(access = PROTECTED)` | Entity, Embeddable |
| `@NoArgsConstructor(access = PRIVATE)` | Mapper, Fixture |
| `@RequiredArgsConstructor` | Service, Repository, Controller, Config, Enum |
| `@Builder` | Entity (private 생성자), 일부 DTO |
| `@Slf4j` | Service, Scheduler, Config |

금지: `@Data`, `@Setter`, `@AllArgsConstructor`

### 9.11 이벤트 (Kafka)

- 토픽명: `static final` 상수
- 이벤트 발행: `@TransactionalEventListener(phase = AFTER_COMMIT)`
- 선택적 활성화: `@ConditionalOnProperty`

### 9.12 테스트

- 테스트 계층: `TestContainerSupport` → `DataJpaTestSupport` / `ApiTestSupport` / `ApiTestKafkaSupport`
- `@Tag("integration")` / `@Tag("unit")` 분류
- `@DisplayName("[한국어 설명]")` — 대괄호 포함
- **Given-When-Then** 주석으로 구분
- Fixture: `core/src/testFixtures/`, `final class` + `@NoArgsConstructor(access = PRIVATE)`, 정적 팩토리
- TestContainers로 실제 DB 사용, `DatabaseCleaner`로 격리

### 9.13 기타

- **공통 응답:** `ApiResponse<T>` 래퍼 사용
  ```java
  { "success": true, "data": {}, "message": "" }
  ```
- **한글 로그:** `한글설명: 변수={}` 포맷
  ```java
  log.info("관심종목 등록 완료: stockCode={}, userId={}", stockCode, userId);
  ```
- 날짜: `LocalDateTime` (감사), `LocalDate` (비즈니스). `Instant`, `ZonedDateTime` 미사용
- Null 처리: `Optional<T>` + `.orElseThrow()`, `Objects.equals()`, `@Nullable` 미사용
- 포매팅: 4 spaces, 같은 줄 중괄호, 80-100자 줄 길이
- 상수: `UPPER_SNAKE_CASE` (`private static final`)

---

## 10. 주의사항 / 금지사항

- `.env` 파일 Git 커밋 금지
- Python 엔진 장애 시 Spring에서 fallback 처리 필수 (이전 캐시 스코어 반환)
- OHLCV 수집 배치는 장 마감(15:30) 이후에만 실행
- 토스증권 API Rate Limit은 **초당 토큰 버킷** 방식 (일일 쿼터 없음, `X-RateLimit-Limit`은 초당 burst capacity, 매초 토큰 리필). `MARKET_DATA_CHART` 그룹 초당 한도(스펙 예시 10건) 기준 150ms 딜레이 유지. 429 시 `RATE_LIMIT_EXCEEDED`로 감지해 수 초 백오프 후 재시도(`X-RateLimit-Reset`/`Retry-After` 헤더 참고)

---

## 11. 자주 쓰는 명령어

```bash
# 인프라 실행 (MySQL 3308, Redis 6379)
docker-compose up -d

# 백엔드 실행
cd backend && ./gradlew :api:bootRun

# 백엔드 빌드 (테스트 제외)
cd backend && ./gradlew build -x test

# Python 엔진 실행
cd quant-engine && uvicorn main:app --reload --port 8000

# 프론트 실행
cd frontend && npm run dev

# MySQL 접속
mysql -h 127.0.0.1 -P 3308 -u root -pquantlab quantlab

# Redis 접속
docker exec -it quantlab-redis redis-cli
```
