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
├── docker-compose.yml           # 로컬 개발용 인프라(MySQL 3308, Redis 6381)
├── docker-compose.prod.yml      # 배포용 전체 스택(단일 EC2, Phase 6)
├── docker-compose.cloudwatch.yml # EC2 전용 로그 오버레이(awslogs 드라이버, Phase 6)
├── .env.prod.example            # 프로덕션 시크릿 템플릿(Phase 6)
│
├── docs/                        # 프로젝트 전반 문서
│   ├── DEVELOPMENT.md           # 로컬 개발 실행 + Playwright/배포 아티팩트 검증 방법론
│   └── DEPLOYMENT.md            # EC2 배포 런북(Phase 6 - IAM/로그/모니터링/백업 포함)
│
├── scripts/                     # EC2에서 cron으로 도는 운영 스크립트(Phase 6)
│   ├── report-health-metric.sh  # 헬스체크·메모리·디스크 → CloudWatch 커스텀 메트릭
│   ├── backup-mysql.sh          # mysqldump → S3
│   └── install-cron.sh          # 위 스크립트들을 멱등하게 crontab 등록
│
├── .github/workflows/            # Phase 6
│   ├── ci.yml                    # 백엔드/퀀트엔진/프론트 빌드+테스트
│   └── deploy.yml                # 태그 푸시 시 GHCR 푸시 → EC2 배포
│
├── backend/                    # Spring Boot 멀티모듈 프로젝트
│   ├── build.gradle            # 루트 빌드 파일
│   ├── settings.gradle         # 모듈 정의 (api, core, common, event)
│   ├── .env.example            # 환경변수 템플릿
│   ├── Dockerfile              # 멀티스테이지(JDK 빌더 → JRE 런타임, Phase 6)
│   ├── api/                    # REST 컨트롤러, Swagger, 전역 예외 핸들러
│   │   └── src/main/java/com/quantlab/
│   │       ├── QuantLabApplication.java
│   │       ├── common/controller/   # HealthCheck
│   │       ├── common/config/       # SwaggerConfig
│   │       ├── common/exception/    # GlobalExceptionHandler
│   │       └── stock/controller/    # StockController
│   │   └── src/main/resources/
│   │       ├── application.yml       # 공통 + dev 기본값
│   │       └── application-prod.yml  # 프로덕션 오버라이드(Phase 6)
│   ├── core/                   # 서비스, 리포지토리, 도메인, DTO
│   │   └── src/main/java/com/quantlab/
│   │       ├── common/              # TimeBaseEntity, Config, Exception
│   │       ├── stock/               # 종목 도메인, 서비스, CSV 적재
│   │       ├── price/               # 시세 도메인, 수집 서비스, 스케줄러
│   │       └── infra/toss/          # 토스증권 API 클라이언트, 토큰 관리
│   ├── common/                 # 공유 유틸 (java-library, Spring 미포함)
│   │   └── src/main/java/com/quantlab/common/exception/ErrorCode.java
│   └── event/                  # Kafka 이벤트 (향후 확장)
│
├── quant-engine/               # Python FastAPI 퀀트 계산 서버
│   ├── main.py
│   ├── requirements.txt
│   ├── Dockerfile              # python:3.11-slim + uvicorn(Phase 6)
│   └── calculator/
│       ├── indicators.py       # RSI, MACD, 볼린저밴드 등
│       └── scorer.py           # 스코어링 & 등급 산출
│
└── frontend/                   # React 19 + TypeScript + Vite + Tailwind CSS
    ├── Dockerfile               # vite build → nginx 정적 서빙(Phase 6)
    ├── nginx.conf                # /api·/ws 리버스 프록시 + SPA 폴백(Phase 6)
    └── src/
        ├── main.tsx             # QueryClientProvider + BrowserRouter + AuthProvider
        ├── App.tsx              # 라우트 + 레이아웃
        ├── pages/                # Login, OAuthCallback, Watchlist, StockDetail, Dashboard
        ├── components/           # layout/, common/, watchlist/, search/, chart/, score/
        ├── hooks/
        │   ├── useStockPriceSocket.ts  # STOMP 다건 구독
        │   └── queries/          # React Query 훅(도메인별)
        ├── realtime/
        │   └── stompClient.ts    # STOMP 클라이언트 싱글턴(참조 카운팅 구독)
        ├── auth/                 # AuthContext, tokenStorage, ProtectedRoute
        ├── api/                  # axios 클라이언트 + 엔드포인트별 래퍼
        ├── types/                # 백엔드 DTO 미러링
        └── config/               # env.ts, oauth.ts(authorize URL 빌더)
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
| Framework | React 19 + TypeScript (Vite) |
| 빌드 도구 | Vite (`@tailwindcss/vite` 플러그인 방식, v3의 tailwind.config.js 불필요) |
| 라우팅 | react-router-dom |
| 차트 | TradingView Lightweight Charts (v5, `addSeries(CandlestickSeries, ...)` API) |
| WebSocket | SockJS + StompJS (`@stomp/stompjs`, `sockjs-client`) |
| HTTP 클라이언트 | axios (요청 인터셉터로 Bearer 부착, 응답 인터셉터로 401 재발급) |
| 상태 관리 | React Query (서버 상태) + Context(인증) + useState (로컬) |
| 스타일 | Tailwind CSS v4 |
| 개발 서버 포트 | **3001 고정**(`vite.config.ts` `strictPort`) - OAuth 리다이렉트 URI가 이 포트를 전제로 함(로컬에 Grafana가 3000번을 점유해 3001로 결정) |

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

## 5. 환경변수

전체 목록과 기본값은 `backend/.env.example`(로컬)·`.env.prod.example`
(배포, `docs/DEPLOYMENT.md` 참고)이 실제 소스이므로 여기서 따로
복제하지 않는다 — 항목이 늘어날 때마다 이 문서를 같이 갱신해야 하는
중복 유지보수 부담을 피하기 위함(이번 문서 정리 중 여기 있던 옛 YAML
스니펫이 JWT·OAuth·CORS 등 절반 이상의 실제 설정을 안 담은 채 방치돼
있던 걸 발견해 제거함).

로컬은 `backend/.env` 파일로 관리하고 `.gitignore`에 반드시 포함(이미
포함돼 있음). 배포용 `.env.prod`도 동일하게 gitignore 대상.

---

## 6. 핵심 기능 & API 명세

### 인증
| Method | URI | 설명 |
|---|---|---|
| POST | `/api/auth/login/{provider}` | 소셜 로그인(구글/카카오/네이버 인가 코드 → 토큰 발급) |
| POST | `/api/auth/reissue` | 리프레시 토큰으로 액세스/리프레시 토큰 재발급 |
| POST | `/api/auth/logout` | 현재 사용자의 리프레시 토큰 무효화 |

### 종목
| Method | URI | 설명 |
|---|---|---|
| GET | `/api/stocks/{stockCode}` | 종목 상세 조회 |
| GET | `/api/stocks/search?q={keyword}` | 종목명/코드 검색(페이징) |

### 관심 종목
| Method | URI | 설명 |
|---|---|---|
| GET | `/api/watchlist` | 관심 종목 목록 |
| POST | `/api/watchlist/{stockCode}` | 관심 종목 등록 |
| DELETE | `/api/watchlist/{stockCode}` | 관심 종목 해제 |

### 시세 & 차트
| Method | URI | 설명 |
|---|---|---|
| GET | `/api/stocks/{stockCode}/price` | 현재가 조회(시세 없으면 `price=null`, 404 아님) |
| GET | `/api/stocks/{stockCode}/chart?period=daily&days={1~365, 기본 90}` | 일봉 차트 데이터(`period`는 현재 `daily`만 지원) |

### 스코어
| Method | URI | 설명 |
|---|---|---|
| GET | `/api/stocks/{stockCode}/score` | 종목 스코어 조회 |
| GET | `/api/dashboard/scores` | 관심 종목 전체 스코어 랭킹 |

### WebSocket
- 엔드포인트: `ws://localhost:8080/ws/stocks` (SockJS)
- 구독 토픽: `/topic/price/{stockCode}`
- 메시지: `{ stockCode, currentPrice, changeRate, timestamp }`
  - `volume`(거래량)은 제외 - 폴링 소스인 Toss 현재가 API가 거래량을
    주지 않고, 이를 얻으려면 더 빡빡한 레이트리밋 그룹(`MARKET_DATA_CHART`)의
    캔들 API를 매 틱마다 추가 호출해야 해 실익 대비 비용이 큼(v1 스코프 제외)
- 토스증권 API가 아직 WebSocket을 지원하지 않아(§4) Spring이 REST
  현재가를 폴링(기본 3초, `REALTIME_PRICE_POLL_INTERVAL_MS`로 조정)해
  STOMP로 브로드캐스트하는 변환 계층으로 구현(`PriceBroadcastScheduler`)

### Python 퀀트 엔진
| Method | URI | 설명 |
|---|---|---|
| POST | `/calculate/score/batch` | 관심 종목 일괄 스코어 계산 |

---

## 7. 기술적 지표 스코어링 로직

> **v2 설계 진행 중.** 추세추종/평균회귀 서브스코어 분리 + AI 코멘트 생성 방식으로
> 고도화 중이며, 상세 공식/임계값은 `quant-engine/docs/SCORING_DESIGN.md`
> (gitignore 처리, 로컬 전용) 참고. 아래는 최초 v1 설계.

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
  - 초기 CSV(`krx-stocks.csv`)가 실제로는 대형주 24종목만 담긴 샘플
    데이터였음(전체 상장사인 것처럼 보였으나 검색에 안 나오는 종목이
    많다는 사용자 리포트로 발견) - KRX 정보데이터시스템(data.krx.co.kr)
    정식 API는 최근 로그인 세션을 요구하도록 바뀌어(`400 LOGOUT`)
    대신 KIND(kind.krx.co.kr)의 공개 상장법인목록 다운로드로 코스피+
    코스닥+코넥스 2,706종목(스팩 등 표준 6자리 코드가 아닌 종목·중복
    행 제외)을 받아 CSV를 교체. 이미 있던 24건은 그대로 두고 나머지만
    INSERT해 기존 관심종목/시세/스코어 이력 보존
  - 신규상장·상장폐지가 생겨도 CSV 재교체 없이 반영되도록
    `StockMasterSyncScheduler`(주 1회, 일요일 03:00) 추가 -
    `StockMasterSyncService.syncStockMaster()`가 KIND 최신 목록과
    DB를 비교해 신규 코드는 등록(`LISTED`), DB에는 있는데 KIND
    목록에서 사라진 코드는 `updateListingStatus(DELISTED)`로 표시(삭제
    아님, 기존 시세/스코어 이력 보존). 수동 트리거는
    `POST /dev/stock-master/sync`(dev 프로필 전용)
  - KIND corpList.do는 문서화된 API가 아니라 "Content-Type:
    application/vnd.ms-excel"을 자칭하는 HTML 테이블 응답이라 jsoup으로
    파싱(`infra/kind` 패키지, Toss 클라이언트와 동일한
    `ExternalApiInvoker`/`ErrorCode` 패턴 재사용)
- [x] 일별 OHLCV 수집 Scheduler (매일 16:00 MON-FRI)
  - 최신 캔들의 거래일이 오늘 날짜와 정확히 일치해야만 저장하는 필터가
    있었는데, 장 마감 직후~자정 사이가 아니면 토스의 "최신" 캔들은
    전날 날짜라 항상 걸러졌음(예외 없이 조용히 no-op, 성공으로 카운트) -
    캔들 자체의 timestamp에서 거래일을 추출해 그 날짜로 저장하도록 수정
  - 토스 429(Rate Limit) 발생 시 `RATE_LIMIT_EXCEEDED`로 구분해 3초
    백오프 후 해당 종목만 1회 재시도(배치 전체는 계속 진행). 종목간
    딜레이는 60ms→150ms로 조정(`MARKET_DATA_CHART` 그룹 스펙 예시
    초당 10건 기준)
  - 로컬 brew Redis가 6379를 선점해 docker-compose 컨테이너 대신 그쪽에
    연결되는 문제 - Redis 포트를 6381로 변경(MySQL 3306 충돌 때와 동일 패턴)
- [x] 종목별 이력 OHLCV 백필 (관심종목 등록 시 자동 트리거)
  - 기존 배치는 "오늘 1건"만 누적해 스코어링에 필요한 200거래일
    이력이 없었음 - 새 인프라 없이 기존 `TossApiClient.getDailyCandles`의
    count/before 커서 페이지네이션을 그대로 재사용해 해결
  - 목표 200일을 살짝 밑도는 상태에서 페이지 하나를 통째로 더 받아
    실제로는 종목당 최대 400건까지 저장될 수 있음(루프 종료 조건이
    페이지 처리 "전"에만 검사되기 때문) - 정확히 자르기보다 단순함을
    택한 의도적 트레이드오프(장기 지표 계산엔 여유분이 오히려 유리)

### ✅ Phase 2 — 도메인 API (완료)
- [x] 소셜 로그인(구글/카카오/네이버) + JWT 인증 (Access/Refresh, Redis 저장)
- [x] 종목 검색 API
  - 검색은 `q`(필수, 공백 불가) + `page`/`size` 페이징(`Slice`) 조합.
    빈/공백 검색어를 막지 않으면 `Containing("")`가 전체 매칭이 돼버려
    `@NotBlank`로 사전 차단
- [x] 관심 종목 CRUD (사용자별 스코핑)
  - 최초 계획은 "단일 사용자, 인증 없음" 전제였는데, 인증 도입을
    Part B(관심 종목) 착수 전에 계획 문서에 먼저 반영 - Watchlist
    유니크 제약을 (user_id, stock_id) 복합으로, 리포지토리/서비스/
    컨트롤러 시그니처 전부에 userId를 반영한 뒤 구현 시작
  - 목록 조회는 `Slice` 페이징이 아니라 `List` 그대로 반환 - 한 사용자의
    관심 종목 규모가 페이징이 필요할 만큼 커지지 않아 YAGNI로 판단
  - 조인은 QueryDSL이 아니라 JPQL `join fetch` 고정 쿼리 하나로 처리 -
    9.8 컨벤션의 QueryDSL 권장은 "복잡한 쿼리"에 한정되고, 이 조인은
    조건 분기 없는 단순 fetch라 QueryDSL 도입이 과함
- [x] 현재가 / 차트 API
  - `PriceController`를 `StockController`와 분리한 신설 컨트롤러로
    둠 - 종목 도메인 컨트롤러에 시세 도메인 의존이 스며드는 것을 방지
  - 시세가 없는 종목은 404가 아니라 200 + `price=null` - 종목 자체는
    존재가 검증됐고 "지금 시세가 없음"은 리소스 부재가 아니라는 판단
  - 차트는 `days`(`@Min(1) @Max(365)`, 기본 90) 파라미터로 조회 기간을
    조절, `period`는 현재 `daily`만 허용(다른 값은 400)

### ✅ Phase 3 — Python 퀀트 엔진 (완료)
- [x] FastAPI 프로젝트 세팅
- [x] 지표 계산 (RSI, MACD, 볼린저밴드, 거래량, 이평) + 스코어링(v2, 추세추종/평균회귀 분리)
- [x] Spring 연동 (Spring -> POST /calculate/score/batch 호출, 점수 영속화, 조회 API)
  - 관심 종목 등록 즉시 + 매일 16:00 배치로 재계산, `score` 테이블에 일별 이력 누적
  - Python 엔진 장애 시 직전 스코어 이력을 그대로 반환(별도 캐시 계층 없이 fallback)
  - 연동 중 겪은 버그 2건(`PythonEngineConfig`/`ScoreBatchApiRequest` 코드
    주석에도 근거 남김):
    - JDK `HttpClient`는 평문(`http://`) 연결에도 기본적으로 HTTP/2
      cleartext(h2c) 업그레이드를 시도하는데, uvicorn(h11)이 이를
      지원하지 않아 POST 바디가 통째로 유실됨(Python에서 "body: Field
      required"로 관측) - `HttpClient`를 HTTP/1.1로 고정해 해결
    - `RestClient` 기본 Jackson 컨버터가 `LocalDate`를 `[2026,7,1]`
      배열로 직렬화해 Python(pydantic) 파싱이 깨짐 - 날짜 필드를
      `String`으로 바꿔 `LocalDate.toString()`으로 직접 포맷해 해결
    - 이후 코드리뷰(멀티에이전트)로 추가 발견한 4건(빈 배치 전체 실패,
      트랜잭션 경계, 배치 저장 항목별 격리, N+1 등)은 `ScorePersistenceService`
      분리를 포함한 별도 커밋들로 수정 완료

### ✅ Phase 4 — WebSocket 실시간 (완료)
- [x] STOMP 세팅 (`/ws/stocks`, SockJS, `/topic` 심플 브로커)
- [x] Toss 현재가 폴링 → STOMP 브로드캐스트
  - 토스증권 API가 WebSocket 미지원이라(§4) REST 폴링(기본 3초) →
    `/topic/price/{stockCode}` 브로드캐스트로 구현. 장중 판별은
    Toss 장 운영 캘린더 API로 공휴일까지 인지
  - 정상 상태에서는 폴링 틱마다 MySQL 쿼리가 발생하지 않도록
    관심종목 코드/전일종가/장운영여부를 각각 인메모리 캐싱
    (`WatchlistedStockCodeCache`: 30초 TTL, `PreviousCloseCache`/
    `MarketCalendarCache`: 캘린더 날짜 기준, 값이 바뀔 때만 재조회)
  - 실서버 검증: 93초 동안 관심종목/전일종가 쿼리가 6회만 발생
    (틱마다 조회했다면 약 31회) - STOMP 클라이언트로 `/topic/price/{code}`
    구독해 3초 간격 브로드캐스트 수신도 직접 확인
- [x] Redis 시세 캐싱 (`PriceCacheStore`) - 브로드캐스트 스냅샷을
  적재하고, 기존 현재가 조회 API도 이를 먼저 조회하는 read-through로 재사용

### ✅ Phase 5 — 프론트엔드 (완료)
- [x] React 초기 세팅 (Vite + React 19 + TS + Tailwind CSS v4, 포트 3001 고정)
  - 인증은 프론트엔드가 OAuth authorize URL을 직접 만들어 브라우저를
    리다이렉트하는 구조(백엔드엔 authorize 엔드포인트가 없음) - code만
    `POST /api/auth/login/{provider}`로 넘기면 백엔드가 서버사이드로
    토큰 교환. CSRF 방지 state는 sessionStorage에 보관 후 콜백에서 대조
  - `/dev/auth/token`(개발 프로필 전용)으로 실제 OAuth 앱 콘솔 등록 없이
    인증 필요 화면 전체를 검증 가능
  - 백엔드에 CORS 설정 전무했던 것을 발견해 선행 커밋으로 추가.
    SockJS의 XHR 핸드셰이크(`/ws/stocks/info`)가 기본적으로
    `withCredentials=true`라 `Access-Control-Allow-Credentials: true`도
    필요(REST 인증 자체는 여전히 쿠키가 아닌 Bearer 헤더 사용, 오리진을
    특정 값 하나로 고정해뒀으므로 안전)
- [x] 관심 종목 리스트 + 실시간 시세 (검색으로 추가, 낙관적 삭제,
  STOMP 클라이언트 싱글턴 + 참조 카운팅 구독으로 종목당 소켓 하나가
  아니라 앱 전체에 소켓 하나만 유지)
- [x] 종목 상세 차트 (Lightweight Charts v5, 기간 선택, 스코어 카드 -
  `ScoreResponse.grade`는 enum 이름이 아니라 한글 표시명으로 내려오므로
  주의)
- [x] 스코어 대시보드 (관심종목 스코어 랭킹, 종합점수 내림차순)

전 구간 Playwright 헤드리스 브라우저 + 실제 백엔드로 실증(로그인/로그아웃,
관심종목 추가·삭제, 실시간 시세 구독 프레임, 차트·스코어 렌더, 대시보드
정렬까지) - 자세한 내용은 각 기능 커밋 메시지 참고.

### 🟡 Phase 6 — 배포 (아티팩트 준비 완료, 실제 EC2 배포는 사용자 진행 필요)
- [x] Docker Compose (단일 EC2 + nginx 리버스 프록시 아키텍처)
  - 애플리케이션 3종 Dockerfile(`backend/Dockerfile`·`quant-engine/Dockerfile`·
    `frontend/Dockerfile`) + `docker-compose.prod.yml` 신규 작성.
    프론트는 nginx가 정적 파일을 서빙하며 `/api`·`/ws`만 backend로
    프록시 - 프론트가 always same-origin(`VITE_API_BASE_URL=""` 빌드
    인자)으로만 요청하게 만들어 브라우저 CORS 자체가 필요 없어지는
    설계(`frontend/nginx.conf`)
  - 로컬에서 `docker-compose.prod.yml`을 실제로 기동해 검증하던 중,
    dev용 `docker-compose.yml`과 같은 디렉터리라 프로젝트명(컨테이너명·
    볼륨명)이 겹쳐 dev MySQL/Redis 컨테이너·볼륨을 그대로 재사용(사실상
    덮어씀)해버리는 문제를 발견 - `docker-compose.prod.yml`에
    `name: quantlab-prod`를 명시하고 컨테이너명도 `quantlab-prod-*`로
    분리해 해결. 로컬 dev 인프라를 실제로 손상시키기 전에 잡은 문제
  - `/api/health` 200, SPA 렌더, 종목 마스터 CSV 자동적재(2706건)까지
    실제 컨테이너 기동으로 확인
- [x] GitHub Actions CI/CD
  - `.github/workflows/ci.yml`: 백엔드(gradle build, MySQL은
    Testcontainers, Redis는 서비스 컨테이너로 6381 매핑 - 기존 로컬
    제약과 동일한 이유), 퀀트엔진(pytest), 프론트(lint+build) 3잡 병렬
  - `.github/workflows/deploy.yml`: 태그 푸시/수동 실행 시 GHCR
    이미지 빌드·푸시 → SSH로 EC2 접속해 compose pull/up
  - 실제 GitHub Actions 실행과 EC2 배포 자체는 이 세션에서 검증
    불가(사용자 AWS 계정·시크릿 필요) - `docs/DEPLOYMENT.md` 런북으로
    안내
- [ ] AWS EC2 배포 — **사용자가 직접 진행해야 하는 부분**(EC2 프로비저닝,
  Elastic IP, IAM 인스턴스 역할, 보안그룹, GitHub Secrets 등록, OAuth
  콘솔 운영 도메인 등록). 절차는 `docs/DEPLOYMENT.md` 참고
- [x] 로그 수집 / 모니터링·알림 / DB 백업 (아티팩트 준비 완료)
  - 로그: `docker-compose.cloudwatch.yml`(신규 오버레이) - 5개 서비스
    모두 `awslogs` 드라이버로 `/quantlab/{서비스명}` 로그 그룹에 전송.
    `docker-compose.prod.yml` 본체에 안 넣은 이유는 `awslogs` 드라이버가
    컨테이너 기동 시점에 AWS 자격증명을 요구해서, 넣었으면 Phase 6에서
    확립해둔 로컬 빌드 검증 경로(`docker compose -f docker-compose.prod.yml
    build`)가 깨졌을 것 - `docker compose -f docker-compose.prod.yml
    -f docker-compose.cloudwatch.yml config`로 로컬에서 머지만 검증(실제
    AWS 전송은 EC2 몫)
  - 모니터링/알림: CloudWatch Agent 대신 `scripts/report-health-metric.sh`
    (헬스체크·메모리·디스크 3개를 커스텀 메트릭 네임스페이스로 전송,
    cron 5분 간격) - Agent는 설치·설정 파일이 필요해 이 규모엔 과함,
    커스텀 메트릭은 계정당 10개까지 상시 무료라 비용 차이도 없음(사용자
    확인 후 채택). SNS 알람 4종(EC2 상태·앱 헬스체크·디스크·메모리)은
    콘솔/CLI 절차만 문서화(실제 생성은 사용자 몫)
  - DB 백업: `scripts/backup-mysql.sh` - 컨테이너 안에서 mysqldump(대상
    DB만) → gzip → S3, 로컬엔 최근 3개만 보관하고 장기 보존은 스크립트가
    아니라 S3 라이프사이클(30일 만료)에 위임. Redis는 캐시일 뿐이라
    (유실돼도 재폴링·재계산되는 파생 데이터) 백업 대상에서 제외
  - `scripts/install-cron.sh`: 위 두 스크립트를 멱등하게(마커 주석으로
    중복 방지) crontab에 등록
  - IAM: 액세스 키 대신 EC2 인스턴스 프로파일(Role)로 S3/CloudWatch
    권한 부여 - 정책 JSON은 `docs/DEPLOYMENT.md` §5

배포 검증 도중 발견한 사이드 버그 1건: `GlobalExceptionHandler`의
catch-all(`@ExceptionHandler(Exception.class)`)이 Spring 6의
`NoResourceFoundException`(매핑되지 않은 경로)까지 삼켜서 모든 404
상황이 500으로 응답되고 있었음 - `/dev/auth/token`이 prod 프로파일에서
정말 404가 되는지 실제로 검증하려다 발견. 전용 핸들러를 추가해 수정
(Phase 6과 무관한 기존 버그지만, 배포 문서에 쓸 검증 문구의 정확성을
위해 실측하다 나온 발견이라 같은 세션에서 수정)

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
- `*Repository extends JpaRepository<...>, *QueryRepository`(QueryDSL 커스텀 조합) 패턴에서, 커스텀 `*QueryRepositoryImpl`은 `@Repository`가 붙어 있어 JPA가 자동 구성하는 리포지토리 프록시와 별개로 그 자체로도 스프링 빈이 된다. 따라서 다른 클래스에서 주입받을 땐 반드시 조합된 구체 타입(`ScoreRepository`, `DailyPriceRepository` 등)을 쓸 것 - `*QueryRepository` 인터페이스를 직접 주입하면 "빈 2개 발견" 에러가 난다
- `TestContainerSupport`는 MySQL만 Testcontainers로 격리하고 **Redis는 격리하지 않는다**(로컬 실제 Redis를 그대로 씀). Redis를 읽는 서비스(예: 현재가 조회의 read-through 캐시)를 다루는 통합 테스트는 관련 캐시/스토어 클래스를 `@MockBean`으로 격리할 것 - 그렇지 않으면 로컬에서 `bootRun`으로 남긴 캐시 값이 테스트 결과에 섞여 간헐적으로 실패한다
- Vite는 webpack과 달리 Node.js 전역을 자동 폴리필하지 않는다. `sockjs-client`처럼 `global`을 참조하는 라이브러리를 그대로 번들하면 브라우저에서 "global is not defined"로 페이지 전체가 깨진다 - `frontend/vite.config.ts`에 `define: { global: 'globalThis' }` 필요(이 문제는 해당 라이브러리를 실제로 번들에 포함시키는 순간에만 드러나므로, import만 추가하고 아직 렌더 경로에 안 걸린 코드에서는 안 잡힐 수 있음에 주의)
- SockJS 클라이언트의 XHR 폴백 트랜스포트(`/ws/stocks/info` 핸드셰이크 등)는 기본적으로 `withCredentials: true`로 요청한다. REST API 인증 자체는 쿠키가 아니라 `Authorization` 헤더를 쓰더라도, 백엔드 CORS 설정에 `allowCredentials(true)`가 없으면 오리진이 일치해도 브라우저가 응답을 차단한다(`backend/api/.../auth/config/SecurityConfig.java`). 허용 오리진을 특정 값 하나로 고정해뒀다면(와일드카드 아님) 안전하게 켤 수 있다

---

## 11. 자주 쓰는 명령어

> 로컬에서 백엔드+프론트엔드를 함께 띄우는 법, 개발용 로그인으로 실제
> OAuth 없이 인증 화면 검증하기, Playwright로 실제 브라우저 동작을
> 검증하는 방법론, **배포 아티팩트(Docker) 로컬 검증**은
> [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) 참고. EC2 실배포
> 절차(Elastic IP, IAM, 로그/모니터링/백업 포함)는
> [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) 참고.

```bash
# 인프라 실행 (MySQL 3308, Redis 6381)
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

# 배포 이미지 로컬 빌드 검증 (AWS 자격증명 불필요, docs/DEVELOPMENT.md §3 참고)
docker compose -f docker-compose.prod.yml build

# 배포 오버레이 머지 결과만 확인(컨테이너 기동 없이)
docker compose -f docker-compose.prod.yml -f docker-compose.cloudwatch.yml config
```

---

## 작업 기록

### 2026-07-08 - 프론트엔드 검증 보강 + 다듬기 + 개발 가이드 문서화

**변경 사항**
- Phase 5(프론트엔드) 완료 후 미검증 상태였던 시나리오를 Playwright
  헤드리스 브라우저로 실제 검증: 401→토큰 재발급→재시도, 재발급 자체
  실패 시 로그인 리다이렉트, 빈 관심종목/빈 대시보드 상태, 검색 결과
  없음, 백엔드 네트워크 장애 시 에러 상태, 375px 모바일 뷰포트 레이아웃
- 검증 중 발견한 버그 4건 수정:
  - React Query 기본 재시도(3회, 지수 백오프)로 장애 시 에러 노출까지
    7.4초 걸리던 것을 전역 retry:1로 낮춰 1.4초로 단축
  - 수동 ResizeObserver 방식이 모바일에서 캔들 차트를 20px 밀려나게
    하던 문제를 lightweight-charts 공식 autoSize 옵션으로 교체
  - 스코어를 반올림 없이 원시 double(예: 43.91018446890717)로 그대로
    렌더링하던 게 스코어 대시보드 테이블이 모바일에서 페이지를 221px
    밀어내던 오버플로우의 실제 원인이었음 - 소수 1자리 반올림 +
    테이블 overflow-x-auto 방어 추가
- 번들 다듬기: lightweight-charts를 종목 상세 페이지에서만 불러오도록
  React.lazy 적용(메인 청크 563KB → 402KB)
- oxlint 경고 해소: AuthContext.tsx를 컨텍스트(context.ts)/Provider
  (AuthContext.tsx)/훅(useAuth.ts) 3개 파일로 분리
- 신규 docs/DEVELOPMENT.md: 로컬 개발 환경 실행법 + Playwright 검증
  방법론 정리(일반 개발 가이드라 gitignore 대상 아님)

**변경 파일**
- `frontend/src/main.tsx` — QueryClient 기본 retry 1로 조정
- `frontend/src/components/chart/CandleChart.tsx` — autoSize 전환
- `frontend/src/utils/scoreFormat.ts`(신규) — 스코어 반올림 헬퍼
- `frontend/src/components/score/ScoreCard.tsx`, `ScoreRankingTable.tsx` — 반올림 적용 + overflow-x-auto
- `frontend/src/pages/WatchlistPage.tsx` — 테이블 overflow-x-auto
- `frontend/src/pages/StockDetailPage.tsx` — CandleChart lazy 로딩
- `frontend/src/auth/context.ts`(신규), `AuthContext.tsx`, `useAuth.ts`(신규) — 파일 분리
- `docs/DEVELOPMENT.md`(신규), `CLAUDE.md` §11 — 문서 링크 + Redis 포트 주석 오타 수정

**결정 사항**
- 되돌릴 수 없는 조작(관심종목 전체 삭제로 빈 상태 확인)은 원래
  stockCode 목록을 먼저 기록해두고 검증 후 API로 원상복구 - dev
  테스트 계정 데이터를 훼손하지 않기 위함
- 검증 스크립트(Playwright)는 frontend/ 안에 임시로 두고 커밋하지
  않음(모듈 해석 문제 + 일회성 도구 성격)

**다음 작업**
- 실제 OAuth 라운드트립(구글/카카오/네이버 콘솔 등록 여부)은 이
  세션에서 검증 불가 - 사용자가 직접 확인 필요
- CLAUDE.md Phase 6(배포)가 마지막 미완 Phase

### 2026-07-09 - Phase 6 배포 아티팩트 준비(컨테이너화 + CI/CD)

**변경 사항**
- 단일 EC2 + nginx 리버스 프록시 아키텍처로 Docker Compose 전체화:
  애플리케이션 3종 Dockerfile, `docker-compose.prod.yml`, nginx
  same-origin 프록시 설정. 실제로 로컬에서 3개 이미지 빌드 + 전체
  스택 기동까지 검증(자세한 내용은 Phase 6 섹션 참고)
- GitHub Actions CI(`ci.yml`: gradle build + pytest + lint/build 3잡
  병렬) + CD(`deploy.yml`: GHCR 푸시 → SSH로 EC2 pull/up) 워크플로 추가
- `docs/DEPLOYMENT.md` 신규: EC2 사전준비부터 최초 기동, GitHub Secrets,
  OAuth 운영 도메인 재등록, 롤백까지 런북 형태로 정리
- 배포 검증 도중 발견한 사이드 버그 수정: `GlobalExceptionHandler`의
  catch-all이 모든 404 상황을 500으로 응답하던 문제(상세는 Phase 6
  섹션 및 해당 fix 커밋 참고)

**변경 파일**
- `backend/Dockerfile`, `quant-engine/Dockerfile`, `frontend/Dockerfile`,
  각 `.dockerignore`, `frontend/nginx.conf` — 컨테이너화
- `docker-compose.prod.yml`, `.env.prod.example`,
  `backend/api/src/main/resources/application-prod.yml`, `.gitignore` — prod 구성
- `backend/.../common/exception/GlobalExceptionHandler.java` +
  신규 테스트 — 404/500 버그 수정
- `.github/workflows/ci.yml`, `.github/workflows/deploy.yml` — CI/CD
- `docs/DEPLOYMENT.md`(신규), `CLAUDE.md` Phase 6 — 문서

**결정 사항**
- `docker-compose.prod.yml`에 `name: quantlab-prod`를 명시해 dev용
  compose와 프로젝트명(컨테이너·볼륨)을 분리 - 로컬 검증 중 실제로
  겹치는 것을 발견하고 나서 수정(Phase 6 섹션에 상세 기록)
- 컨테이너 시크릿은 `.env.prod`(gitignore) 하나로 통일하고, compose
  파일에서 `${VAR}` 치환과 `env_file:` 컨테이너 주입 둘 다 이 파일
  하나를 가리키게 함 - 대신 compose 실행 시 항상
  `--env-file .env.prod`를 명시해야 함을 문서에 남김(docker compose가
  기본으로는 `.env`만 자동 로드하기 때문)
- AWS EC2 실제 프로비저닝·시크릿 등록·배포 실행은 이 세션에서 수행할
  수 없어(계정 접근 불가) 아티팩트+런북 준비까지만 진행 - 사용자가
  직접 진행

**다음 작업**
- AWS EC2 실배포(프로비저닝, GitHub Secrets 등록, 첫 `docker compose up`)는
  사용자가 `docs/DEPLOYMENT.md`를 따라 직접 진행해야 함
- 배포 도메인이 정해지면 OAuth 콘솔 redirect URI 재등록 + TLS(certbot)
  적용 필요(`docs/DEPLOYMENT.md` §6, §7에 안내만 남겨둠)
- 작업 트리에 이 세션 범위 밖의 로컬 미커밋 변경 2건이 남아있음
  (`backend/api/src/main/resources/data/krx-stocks.csv`를 전체 KRX
  종목 2706개로 교체, `docs/DEVELOPMENT.md`에 quant-engine 실행 안내
  보강) - 사용자 확인상 아직 미완성/검토 전이라 이번 세션 커밋에서
  의도적으로 제외함. 다음 세션에서 검토 후 별도 커밋 필요
- 실제 OAuth 라운드트립은 여전히 미검증(위 항목과 별개)

### 2026-07-09 - Phase 6 확장(Elastic IP, 로그/모니터링/백업)

**변경 사항**
- 사용자가 EC2 작업을 세분화해달라고 요청하는 과정에서 런북의 공백
  2건을 발견: Elastic IP 미언급(재시작마다 IP 바뀌어 도메인·OAuth
  redirect URI 깨짐), 운영 필수 3종(모니터링/알림·DB 백업·로그 수집)
  미설계
- `docs/DEPLOYMENT.md` §1에 Elastic IP 할당 단계 추가, §5~§8로 IAM
  인스턴스 역할·로그 수집·모니터링/알림·DB 백업 신규 섹션 삽입(기존
  §5~§8은 §9~§12로 재배치)
- 모니터링 구현 방식은 CloudWatch Agent(공식·풍부하지만 설치/설정
  파일 필요) vs 경량 셸 스크립트+cron(설치 불필요, 비용은 사실상
  동일 - 커스텀 메트릭 계정당 10개 상시 무료) 중 사용자에게 트레이드
  오프를 설명하고 확인받아 경량 스크립트로 결정
- 상세는 Phase 6 섹션 참고

**변경 파일**
- `docker-compose.cloudwatch.yml`(신규) - EC2 전용 로그 오버레이
- `scripts/report-health-metric.sh`, `scripts/backup-mysql.sh`,
  `scripts/install-cron.sh`(신규)
- `.env.prod.example` - AWS_REGION, BACKUP_S3_BUCKET 추가
- `.github/workflows/deploy.yml` - compose 명령에 cloudwatch 오버레이 추가
- `docs/DEPLOYMENT.md` - Elastic IP + 로그/모니터링/백업 4개 섹션 신설
- `CLAUDE.md` Phase 6 - 이번 확장 반영

**결정 사항**
- `docker-compose.cloudwatch.yml`을 본체(`docker-compose.prod.yml`)와
  분리 - `awslogs` 드라이버가 기동 시 AWS 자격증명을 요구해서, 합쳐
  넣으면 Phase 6에서 확립한 로컬 빌드/기동 검증 경로가 AWS 자격증명
  없는 로컬 환경에서 깨진다. `docker compose ... config`로 머지만
  로컬 검증(실제 전송은 EC2에서만 가능해 검증 불가)
- MySQL 백업은 set -e로 실패 시 즉시 중단(반쪽 백업 방지), 헬스/리소스
  지표 스크립트는 set -e 없이 지표 하나 실패해도 나머지는 계속
  보고(무응답과 "지표 자체가 안 옴"을 알람에서 구분 가능하게)
- CloudWatch 알람의 헬스체크 항목은 `treat-missing-data breaching`으로
  설정하도록 문서화 - 지표가 아예 안 올라오는 것(cron 죽음, 인스턴스
  응답 없음)도 정상(OK)이 아니라 알람으로 처리돼야 함

**다음 작업**
- 이 확장분도 §5(IAM 역할)~§8(백업) 전부 사용자가 EC2에서 직접
  프로비저닝·검증해야 함 - AWS 리소스라 이 세션에서 생성 불가
- 나머지 미완 항목(AWS EC2 실배포, OAuth 라운드트립, 세션 범위 밖
  로컬 변경 2건 검토)은 위 항목과 동일하게 유지
