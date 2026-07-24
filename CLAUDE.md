# QuantLime — CLAUDE.md

> 이 파일은 Claude Code가 프로젝트 컨텍스트를 유지하기 위한 핵심 문서다.
> 개발 진행 중 변경사항이 생기면 이 파일을 먼저 업데이트할 것.

## 목차

- [1. 프로젝트 개요](#1-프로젝트-개요)
- [2. 디렉토리 구조](#2-디렉토리-구조)
- [3. 기술 스택](#3-기술-스택)
- [4. 외부 API](#4-외부-api)
- [5. 환경변수](#5-환경변수)
- [6. 핵심 기능 & API 명세](#6-핵심-기능-api-명세)
- [7. 기술적 지표 스코어링 로직](#7-기술적-지표-스코어링-로직)
- [8. 개발 Phase 현황](#8-개발-phase-현황) (Phase 0~6은 완료돼 접혀 있음 — 클릭해서 펼치기)
- [9. 코드 컨벤션](#9-코드-컨벤션)
- [10. 주의사항 / 금지사항](#10-주의사항-금지사항)
- [11. 자주 쓰는 명령어](#11-자주-쓰는-명령어)
- [작업 기록](#작업-기록) — 세션별 상세 히스토리는 [`docs/CHANGELOG.md`](docs/CHANGELOG.md)로 분리됨

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|---|---|
| 프로젝트명 | QuantLime |
| 목적 | 국내 주식 기술적 지표 스코어링 + 관심 종목 실시간 모니터링 |
| 범위 | 국내 주식 한정 / 조회·분석만 (주문 기능 없음) |
| 개발자 스택 | Java/Spring 백엔드 개발자, Python 병행 학습 중 |

---

## 2. 디렉토리 구조

<details>
<summary>펼쳐서 보기</summary>

```
quantlime/
├── CLAUDE.md                   # 이 파일
├── README.md
├── .gitignore
├── docker-compose.yml           # 로컬 개발용 인프라(MySQL 3308, Redis 6381)
├── docker-compose.prod.yml      # 배포용 전체 스택(단일 EC2, Phase 6)
├── docker-compose.monitoring.yml # PLG 관측성 스택 오버레이(Prometheus/Grafana/Alertmanager)
├── .env.prod.example            # 프로덕션 시크릿 템플릿(Phase 6)
│
├── docs/                        # 프로젝트 전반 문서
│   ├── DEVELOPMENT.md           # 로컬 개발 실행 + Playwright/배포 아티팩트 검증 방법론
│   ├── DEPLOYMENT.md            # EC2 배포 런북(Phase 6 - PLG 모니터링/백업 포함)
│   ├── ROADMAP.md               # Phase 7 기능 확장 분석
│   └── CHANGELOG.md             # 세션별 작업 기록(CLAUDE.md에서 분리, 2026-07-20)
│
├── scripts/                     # EC2에서 cron으로 도는 운영 스크립트(Phase 6)
│   ├── backup-mysql.sh          # mysqldump → S3
│   └── install-cron.sh          # 위 스크립트를 멱등하게 crontab 등록
│
├── .github/workflows/            # Phase 6
│   ├── ci.yml                    # 백엔드/퀀트엔진/프론트 빌드+테스트
│   └── cd.yml                    # 태그 푸시 시 GHCR 푸시 → EC2 배포
│
├── backend/                    # Spring Boot 멀티모듈 프로젝트
│   ├── build.gradle            # 루트 빌드 파일
│   ├── settings.gradle         # 모듈 정의 (api, core, common, event)
│   ├── .env.example            # 환경변수 템플릿
│   ├── Dockerfile              # 멀티스테이지(JDK 빌더 → JRE 런타임, Phase 6)
│   ├── api/                    # REST 컨트롤러, Swagger, 전역 예외 핸들러
│   │   └── src/main/java/com/quantlime/
│   │       ├── QuantLimeApplication.java
│   │       ├── common/controller/   # HealthCheck
│   │       ├── common/config/       # SwaggerConfig
│   │       ├── common/exception/    # GlobalExceptionHandler
│   │       └── stock/controller/    # StockController
│   │   └── src/main/resources/
│   │       ├── application.yml       # 공통 + dev 기본값
│   │       └── application-prod.yml  # 프로덕션 오버라이드(Phase 6)
│   ├── core/                   # 서비스, 리포지토리, 도메인, DTO
│   │   └── src/main/java/com/quantlime/
│   │       ├── common/              # TimeBaseEntity, Config, Exception
│   │       ├── stock/               # 종목 도메인, 서비스, CSV 적재
│   │       ├── price/               # 시세 도메인, 수집 서비스, 스케줄러
│   │       ├── feed/                # 커뮤니티 피드(글/댓글/좋아요) - videofeed와는 다른 도메인
│   │       ├── videofeed/           # 투자 콘텐츠 요약 피드(유튜브 채널 수집/필터링, Phase 8)
│   │       ├── infra/toss/          # 토스증권 API 클라이언트, 토큰 관리
│   │       └── infra/youtube/       # 유튜브 Data API v3 클라이언트(videofeed 전용)
│   ├── common/                 # 공유 유틸 (java-library, Spring 미포함)
│   │   └── src/main/java/com/quantlime/common/exception/ErrorCode.java
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

</details>

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

### 유튜브 Data API v3 (투자 콘텐츠 요약 피드용, 2026-07-23 신규)
- **Base URL:** `https://www.googleapis.com/youtube/v3`
- **인증:** API 키(`YOUTUBE_API_KEY`, 쿼리 파라미터로 전달)
- **사용 엔드포인트:** `playlistItems.list`(1u), `videos.list`(1u) 둘뿐 -
  **`search.list`(100u)는 절대 쓰지 않는다**(일 쿼터 10,000u를 순식간에
  소모). 채널의 "업로드" 재생목록(`UU` + channelId[2:])을 `playlistItems`로
  훑고, 영상 길이/조회수가 필요할 때만 `videos.list`로 최대 50개씩
  배치 조회
- **채널ID 확보:** 나무위키/vidiq/noxinfluencer 등 웹 검색으로 찾은
  후보는 `channels.list?part=id&forHandle=@핸들`로 재검증 필수(2026-07-23
  세션은 유튜브 도메인 자체가 프록시에서 403이라 직접 재검증 못 함 -
  `ChannelSeedInitializer` 주석 참고)

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
  현재가를 폴링해 STOMP로 브로드캐스트하는 변환 계층으로 구현. 전종목
  폴링·Redis 적재는 `MarketPriceSweepScheduler`(기본 100ms,
  `MARKET_RANKING_POLL_INTERVAL_MS`) 하나가 전담하고, `WatchlistPriceRelayScheduler`
  (기본 3초, `REALTIME_PRICE_POLL_INTERVAL_MS`)는 Toss를 직접 부르지 않고
  그 Redis 스냅샷을 관심종목만 골라 중계한다(2026-07-15/16 재설계 - 이전엔
  두 스케줄러가 각각 Toss를 호출해 관심종목 가격이 중복 조회됐음)

### Python 퀀트 엔진
| Method | URI | 설명 |
|---|---|---|
| POST | `/calculate/score/batch` | 관심 종목 일괄 스코어 계산 |

### 구독 & 결제 (2026-07-22 신규 - 사업자등록 전, 테스트 키 전용)

> 토스페이먼츠 **자동결제(빌링키)만** 지원 - 빌링 API가 카드에만 적용되는
> 제약 때문에 결제수단은 카드로 고정(휴대폰/카카오페이/네이버페이는
> 빌링키 발급 대상이 아님). 장기 플랜(6/12개월)은 카드사 할부
> (`installmentMonths`, 0=일시불/2~12=할부개월) 선택 가능. 프리미엄
> 혜택(기능 게이팅)은 아직 미구현 - `Subscription.status`로 판별 가능한
> 구조만 있음. 상세는 `docs/CHANGELOG.md` 2026-07-22 항목 참고.

| Method | URI | 설명 |
|---|---|---|
| GET | `/api/subscription/plans` | 판매중인 구독 플랜 목록(3/6/12개월) |
| GET | `/api/subscription/me` | 내 구독 상태 + customerKey(카드 등록 위젯용) |
| POST | `/api/subscription/billing-key` | 빌링키 발급 + 즉시 첫 결제(할부 적용) |
| POST | `/api/subscription/cancel` | 자동갱신 해제(현재 주기 종료까지는 계속 이용 가능) |
| GET | `/api/subscription/payments` | 결제 이력 조회 |
| POST | `/api/webhooks/tosspayments` | 토스페이먼츠 웹훅(서명 검증, 인증 불필요) |

### 투자 콘텐츠 요약 피드 - 관리자 (2026-07-23 신규, P0~P2만 구현)

> 유튜브 채널(한국경제TV/런던고라니/주덕) 신규 영상 수집→필터링.
> `com.quantlime.videofeed` 패키지 - 커뮤니티 글/댓글인 `com.quantlime.feed`와는
> 다른 도메인이니 혼동 주의. 정규 실행은 `FeedCollectionScheduler`(하루
> 3회, 07/12/19시)이고 아래는 그 사이 수동 트리거용. `ROLE_ADMIN` 필요.
> 자막(P3)·AI 요약(P4)은 아직 미구현(엔티티 스키마만 준비됨).

| Method | URI | 설명 |
|---|---|---|
| POST | `/api/admin/feed/collect` | 전체 채널 영상 수집 수동 트리거(수집→적재→필터링) |
| POST | `/api/admin/feed/channels/{channelId}/velocity/initialize` | 채널 중앙값 업로드 속도(views/hours) 초기 산정 |

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

<details>
<summary>✅ Phase 0 — 기획 완료</summary>

- 프로젝트 방향, 기술 스택, API 명세, DB 스키마 확정

</details>

<details>
<summary>✅ Phase 1 — 데이터 파이프라인 (완료)</summary>

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

</details>

<details>
<summary>✅ Phase 2 — 도메인 API (완료)</summary>

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

</details>

<details>
<summary>✅ Phase 3 — Python 퀀트 엔진 (완료)</summary>

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

</details>

<details>
<summary>✅ Phase 4 — WebSocket 실시간 (완료)</summary>

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

</details>

<details>
<summary>✅ Phase 5 — 프론트엔드 (완료)</summary>

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

</details>

<details>
<summary>✅ Phase 6 — 배포 (완료, EC2 실배포 완료)</summary>

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
    `name: quantlime-prod`를 명시하고 컨테이너명도 `quantlime-prod-*`로
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
- [x] AWS EC2 배포 — 사용자가 `docs/DEPLOYMENT.md` 런북을 따라 직접
  완료(EC2 프로비저닝, Elastic IP, IAM 인스턴스 역할, 보안그룹, GitHub
  Secrets 등록 등). 이 체크리스트가 실제 배포 완료 이후에도 한동안
  미완료로 표시돼 있었음 - quantlab→quantlime 리네이밍 작업(2026-07-21)
  중 사용자 확인으로 발견해 정정. **문서가 실제 인프라 상태를 못 따라가는
  사례가 실제로 있었으니, Phase 완료 여부는 주기적으로 실제 상태와
  대조할 것**
- [x] 로그 수집 / 모니터링·알림 / DB 백업 (아티팩트 준비 완료)
  - 로그: `docker-compose.cloudwatch.yml`(신규 오버레이) - 5개 서비스
    모두 `awslogs` 드라이버로 `/quantlime/{서비스명}` 로그 그룹에 전송.
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

</details>

### 🔵 Phase 7 — 기능 확장 (기획 중)

토스 HTS 화면 대비 미구현 기능들을 "외부 API 추가/자체 구현으로 가능한가 +
난이도(상/중/하)"로 분석 완료. 상세 매트릭스·근거·재사용 자산은
[`docs/ROADMAP.md`](docs/ROADMAP.md) 참고. 핵심 결론:
- 토스 `prices`는 200종목 벌크지만 거래량 미제공, `candles`는 1종목/호출 →
  **랭킹 난이도는 "거래량이 필요한가"로 갈림**(등락률 실시간=중, 거래대금 실시간=상)
- **AI 요약**은 `quant-engine/calculator/commentary.py`가 이미 Claude Haiku를
  연동 중이라 뉴스 소스만 붙이면 됨(중)
- 제외 결정: 호가 잔량 비율 바, 선물·금 위젯, 주문·보유잔고

착수 후보(난이도순):
- [ ] 호가·체결·상하한가·매수유의 (하, 토스 기존 스펙)
- [ ] 환율·비트코인 위젯 (하)
- [ ] 급등락 실시간 전종목 랭킹 (중, prices 벌크)
- [ ] AI 뉴스 요약 (중, Anthropic 파이프라인 확장)
- [ ] 거래대금 일배치 랭킹 (중, KRX 스크래핑)
- [ ] 해외지수·VIX 위젯 (중, 외부 API)
- [ ] 분봉(1분봉) 차트 (하~중, 토스 API는 이미 지원 - 페이지네이션·저장
  설계가 남아 2026-07-14 세션에서 TODO로 보류. 상세는
  `docs/ROADMAP.md` §8 참고)

### 🔵 Phase 8 — 투자 콘텐츠 요약 피드 (진행 중, P0~P2 구현 완료)

지정 유튜브 채널의 신규 영상을 수집→필터링→(향후)자막→AI 요약→종목
태깅해 피드로 노출하는 모듈. `com.quantlime.videofeed` 패키지(커뮤니티
피드인 `com.quantlime.feed`와는 다른 도메인 - 패키지명 충돌을 피하려고
분리함). 상세 결정 사항은 `docs/CHANGELOG.md` 2026-07-23 항목 참고.

- [x] P0 스키마 + 채널 시드 - Flyway/Liquibase가 이 프로젝트에 전혀
  없어(ddl-auto=update만 사용) 원본 스펙의 Postgres+Flyway SQL을 그대로
  쓰지 않고 JPA 엔티티(`Channel`/`Video`/`Transcript`/`Summary`/
  `VideoTicker`)로 번역. 채널 3개는 `ChannelSeedInitializer`
  (`ApplicationRunner`, 기존 `StockMasterInitializer`와 동일 패턴)로 시딩
- [x] P1 영상 수집 - `infra/youtube/YoutubeApiClient`(playlistItems.list/
  videos.list만 사용, search.list 금지) + `YoutubeVideoCollector`(I/O
  전용) + `VideoPersistService`(external_video_id 존재 확인으로 멱등
  upsert, 짧은 트랜잭션)
- [x] P2 필터링 - `VideoFilterService`(제목 제외/포함, 최소 길이,
  업로드 6시간 유예 후 velocity 판정, max_per_run 컷),
  `ChannelVelocityInitializationService`(채널별 최근 30개 중앙값)
- [x] 관리자 수동 트리거 API 2종 + `FeedCollectionScheduler`(하루 3회,
  Redisson 대신 기존 `spring-data-redis` SETNX 기반 `RedisLockService`로
  분산락 - 새 라이브러리 추가 없이 동일 목적 달성)
- [ ] **채널ID 3개 재검증 필요** - 웹 검색으로 찾았지만 유튜브 도메인이
  이 세션 프록시에서 403이라 직접 재검증 못 함(`ChannelSeedInitializer`
  주석 참고). `channels.list?forHandle=`로 확인 전 운영 투입 금지
- [ ] `YOUTUBE_API_KEY` 발급 후 `POST /api/admin/feed/collect` 실행해
  `video` 테이블 적재 실증(아직 미검증 - Docker/Testcontainers 없는
  세션이라 유닛 테스트만 통과 확인함)
- [ ] P3 자막 수집 (FastAPI `/transcribe`, youtube-transcript-api, 청킹 전략)
- [ ] P4 AI 구조화 요약 + 종목 태깅(§6 요약 JSON 스키마, `caveat` 필수)
- [ ] P6 프론트 피드 노출
- [ ] P7 텔레그램 확장(`platform='TELEGRAM'`)

#### 📋 다음 세션(로컬) 실행 가이드

원격 세션은 유튜브 도메인이 프록시에서 막혀 있고 Docker도 없어 실제
API 호출/DB 적재를 검증하지 못했다. 로컬 환경에서 아래 순서로 이어서
진행할 것.

1. **YouTube Data API 키 발급**: Google Cloud Console → 프로젝트
   생성/선택 → "YouTube Data API v3" 활성화 → 사용자 인증 정보에서
   API 키 발급
2. `backend/.env`에 `YOUTUBE_API_KEY=발급받은키` 추가(`.gitignore`
   대상이라 커밋 안 됨)
3. **채널ID 3개 재검증(운영 투입 전 필수)** - 브라우저나 curl로
   `https://www.googleapis.com/youtube/v3/channels?part=id&forHandle=@핸들&key=API_KEY`
   호출해 `ChannelSeedInitializer`에 심어둔 값과 일치하는지 확인
   (@hkwowtv, 런던고라니 핸들, 주덕 핸들 각각). 다르면 그 파일의 값을
   고치고 이미 DB에 잘못 시딩됐다면 `channel` 테이블에서 해당 행도
   같이 수정
4. 로컬 인프라+백엔드 기동: `docker-compose up -d` →
   `cd backend && ./gradlew :api:bootRun`
5. **ROLE_ADMIN 토큰 확보** - 이 프로젝트엔 아직 "관리자로 승격"하는
   API가 없다(`User.role`은 가입 시 `USER`로 고정, 변경 메서드 없음).
   로컬에서만 아래처럼 우회할 것(운영에서는 절대 이렇게 하지 말 것):
   1. `POST /dev/auth/token` 한 번 호출해 `dev-test-user` 계정을 생성
   2. `UPDATE users SET role='ADMIN' WHERE provider_id='dev-test-user';`로
      DB에서 직접 role 변경(JWT는 발급 시점 role을 그대로 굽기 때문에
      DB만 바꿔서는 기존 토큰에 반영 안 됨)
   3. `POST /dev/auth/token`을 다시 호출해 ADMIN role이 반영된 새
      액세스 토큰 발급받기(`findOrCreate`가 매번 DB를 다시 조회하므로
      바뀐 role이 그대로 실림)
6. **median_velocity 먼저 채우기** - 채널 3개 각각에 대해
   `POST /api/admin/feed/channels/{channelId}/velocity/initialize`
   호출(`Authorization: Bearer <5번 토큰>`). 안 하고 바로 6번을 돌리면
   velocity 배수가 0이 아닌 한국경제TV는 `VideoFilterService`가
   "median_velocity 미산정"으로 판단해 검사 없이 그냥 통과시킨다(코드
   주석 참고) - 실제 필터링 동작을 제대로 보려면 먼저 산정해둘 것
7. `POST /api/admin/feed/collect` 호출 → 응답(`CollectResult` 배열)의
   채널별 `discoveredCount`/`success` 확인 → DB에서
   `SELECT * FROM video ORDER BY created_at DESC LIMIT 20;`로 실제
   적재와 `status`(DISCOVERED/FILTERED_OUT/PENDING_REVIEW/SELECTED)
   분포 확인
8. 여기까지 확인되면 이 체크리스트의 "YOUTUBE_API_KEY 발급 후 ... 실증"
   항목을 완료로 바꾸고, P3(자막 수집) 설계로 넘어갈 것

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
com.quantlime/{feature}/
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

### 9.14 프론트엔드 디자인 규칙

> 2026-07-16 세션에서 홈/종목상세/지표설정/피드 전반을 다듬으며 확정한
> 규칙. "세련되고 깔끔하고 일관성 있게"라는 요청을 이후 세션에서도 같은
> 기준으로 재현하기 위해 정리해둔다.

- **강조색(파란색 accent)을 쓰지 않는다 - 버튼·선택 상태는 전부 블랙
  (`bg-gray-900`/`text-gray-900`) 기준으로 통일한다.** 이전엔 홈
  리디자인 때 넣은 `--color-accent`(#3752ff, Tailwind `bg-accent` 등)를
  버튼·활성 탭·링크에 두루 썼는데, 화면마다 파란색 톤이 미묘하게 달라
  보이고 상승/하락을 나타내는 빨강/파랑(§7 국내 주식 관례)과도 시각적으로
  섞여 헷갈렸다 - 사용자 피드백으로 전부 제거(`index.css`의
  `--color-accent` 정의 자체도 삭제). Primary 버튼은 `bg-gray-900
  text-white hover:bg-gray-800`, Secondary는 `border border-gray-200
  text-gray-700 hover:bg-gray-50`, 텍스트 링크는 `text-gray-700
  hover:underline`로 통일한다.
- **모서리 둥근 정도(border-radius)는 홈 실시간 랭킹의 전체/국내/해외
  필터 박스를 기준으로 통일한다 - 바깥 래퍼(`bg-gray-100 p-1`)는
  `rounded-xl`, 그 안의 개별 버튼은 `rounded-lg`.** 2026-07-23 세션에서
  버튼·박스류 전반에 `rounded-md`(등급 배지, 헤더 네비, 캔들 봉/주/월
  선택기 등)와 `rounded-full` 알약형(헤더 검색창, 로그인 CTA)이 뒤섞여
  있던 걸 이 기준 하나로 통일 - 화면마다 모서리가 미묘하게 달라 보이던
  문제를 없앴다. 아바타·토글 스위치 손잡이·펄스 점처럼 "네모난 박스"가
  아니라 원래부터 원형이어야 하는 요소는 예외(`rounded-full` 유지).
- **"선택됨"은 글자색이 아니라 배경색으로 표시한다.** 사이드패널 탭,
  피드 주제별 커뮤니티 목록, 관심 그룹 목록 등 "지금 뭐가 선택돼 있는지"
  보여줘야 하는 곳은 `bg-gray-100 text-gray-900`(선택) vs
  `text-gray-500/600`(비선택) 패턴을 쓴다 - 파란 텍스트로 선택 상태를
  나타내던 방식(`text-accent`)을 배경색 방식으로 교체.
- **실제로 없는 데이터를 숫자로 꾸며내지 않는다.** 랭킹 테이블의
  "거래대금"처럼 실데이터 소스가 아직 없는 컬럼은 예시 숫자를 채우는
  대신 "-"로 비워두거나 "아직 준비 중이에요" 같은 정직한 안내를 보여준다
  (피드 글의 좋아요/댓글 수도 기능이 없어 항상 0). "예시 데이터입니다"
  캡션으로 얼버무리기보다, 컬럼/필터 단위로 실데이터인지 아닌지를
  명확히 하는 쪽을 우선한다.
- **부가 정보(시총/PER 같은 라벨류)는 작게, 자리 안 차지하게.** 카드나
  별도 섹션으로 만들지 말고 `text-xs text-gray-500` 수준의 한 줄 나열
  (`라벨 값 · 라벨 값 · ...`)로 압축한다 - 화면의 주인공(가격, 차트,
  스코어)을 가리지 않는 선에서만 노출.
- **삼각형(▲▼) 같은 방향 기호는 배경/텍스트 색상만으로 충분히 구분되면
  생략한다.** 상승 빨강 · 하락 파랑 색상 규칙(§7)이 이미 방향을
  전달하므로, 기호를 추가로 붙이면 중복이고 좁은 카드에서는 오히려
  번잡해 보인다.
- **차트/카드류에 등락 방향을 배경색으로도 은은하게 반영한다.** 지수
  카드처럼 상승/하락이 있는 요소는 `border-red-100 bg-red-50/60`
  (상승) / `border-blue-100 bg-blue-50/60`(하락)처럼 옅은 톤의 배경+
  테두리를 얹어 텍스트 대비를 해치지 않는 선에서 시각적 신호를 준다.
- **실시간성이 있는 요소는 은은한 펄스 점(`animate-ping` 조합)으로
  "지금 갱신 중"임을 알린다.** 차트 안에 펄스를 찍기보다(일봉처럼
  "진행 중" 개념이 맞지 않는 차트도 있음) 상태 라벨("장중" 등) 옆에
  독립된 작은 점을 붙이는 편이 재사용하기 쉽고 의미도 분명하다.
- **리퀴드 글래스(반투명 유리) 실험은 진행했다가 2026-07-20 최종 피드백으로
  코드에서는 전부 제거하고 방법론만 문서로 남겼다.** 검색 모달·관심 그룹
  모달 3종에 먼저 적용(2026-07-19) → 판단 기준(정적 패널 vs 잦은 리사이즈)에
  따라 안전한 후보 7곳(ColorWidthChip, LoginModal/ProfileMenu,
  AddToWatchlistGroupPicker, FeedComposeModal/FeedbackModal,
  IndicatorSettingsModal/IndicatorDetailView)으로 적용 범위를 재조정
  (2026-07-19~20) → 최종적으로 전부 원복(`bg-white shadow-2xl` 등 원래
  스타일로 되돌림), `LiquidGlassDefs.tsx`/`utils/browserSupport.ts` 삭제,
  `index.css`의 `.liquid-glass-refract` 규칙 제거(2026-07-20). "안전한
  후보"라는 판단이 "반드시 적용해야 한다"는 뜻은 아니었음 - 시각적으로
  원래 스타일을 선호한다는 게 최종 결론. 판단 기준·4-layer 구조·Safari
  폴백 한계·성능 체크 방법 등 구현 방법론 자체는 재사용을 위해 전역
  `~/.claude/CLAUDE.md` "리퀴드 글래스(Liquid Glass) 구현 가이드"에만
  남겨뒀다 - **이 프로젝트 코드에는 관련 클래스/컴포넌트가 없으니, 다음에
  다시 요청받으면 그 문서를 참고해 처음부터 구현할 것(과거에 있던 파일
  경로를 찾지 말 것)**

---

## 10. 주의사항 / 금지사항

- `.env` 파일 Git 커밋 금지
- 토스페이먼츠는 사업자등록 전에는 **테스트 키만** 쓸 수 있다(실결제 불가). 라이브 전환 시 `TOSS_PAYMENTS_CLIENT_KEY`/`TOSS_PAYMENTS_SECRET_KEY`만 교체하면 되도록 설계돼 있음(§6 구독 & 결제 참고). 빌링키(카드 자동결제 권한 토큰) DB 컬럼은 `BillingKeyConverter`(AES-GCM)로 암호화되는데, `SUBSCRIPTION_BILLING_KEY_ENCRYPTION_KEY`가 비어 있으면 평문으로 통과된다 - 운영 배포 전 반드시 채울 것(`openssl rand -base64 32`). 웹훅 서명 헤더명(`TossPayments-Webhook-Signature`)은 2026-07-22 세션에서 문서 접근이 막혀 확정하지 못한 추정값 - 실제 연동 시 `TossWebhookVerifier`/`PaymentWebhookController` 재확인 필요
- Python 엔진 장애 시 Spring에서 fallback 처리 필수 (이전 캐시 스코어 반환)
- OHLCV 수집 배치는 장 마감(15:30) 이후에만 실행
- 토스증권 API Rate Limit은 **초당 토큰 버킷** 방식 (일일 쿼터 없음, `X-RateLimit-Limit`은 초당 burst capacity, 매초 토큰 리필). `MARKET_DATA_CHART` 그룹 초당 한도(스펙 예시 10건) 기준 150ms 딜레이 유지. 429 시 `RATE_LIMIT_EXCEEDED`로 감지해 수 초 백오프 후 재시도(`X-RateLimit-Reset`/`Retry-After` 헤더 참고)
- `*Repository extends JpaRepository<...>, *QueryRepository`(QueryDSL 커스텀 조합) 패턴에서, 커스텀 `*QueryRepositoryImpl`은 `@Repository`가 붙어 있어 JPA가 자동 구성하는 리포지토리 프록시와 별개로 그 자체로도 스프링 빈이 된다. 따라서 다른 클래스에서 주입받을 땐 반드시 조합된 구체 타입(`ScoreRepository`, `DailyPriceRepository` 등)을 쓸 것 - `*QueryRepository` 인터페이스를 직접 주입하면 "빈 2개 발견" 에러가 난다
- `TestContainerSupport`는 MySQL과 Redis 둘 다 Testcontainers로 격리한다(2026-07-13 세션에서 Redis 추가 - 이전엔 로컬 실제 Redis를 공유해 `bootRun` 잔여 캐시가 테스트에 섞이는 문제가 있었음). 다만 `@EnableScheduling`이 테스트 프로파일에서도 켜져 있어 `MarketPriceSweepScheduler`/`WatchlistPriceRelayScheduler` 등 백그라운드 스케줄러가 같은 컨테이너에 비동기로 값을 채울 수 있다 - 캐시 미스/특정 상태를 결정적으로 검증해야 하는 테스트(`PriceControllerTest` 등)는 여전히 관련 캐시/스토어 클래스를 `@MockBean`으로 스텁할 것
- 로컬에서 Testcontainers 통합 테스트가 "Could not find a valid Docker environment"로 실패했던 이슈는 2026-07-13 세션에서 근본 원인 규명 + 해결 완료(최신 Docker Desktop의 Engine API `MinAPIVersion`과 Testcontainers 1.20.4가 번들한 docker-java 클라이언트 간 버전 비호환 - `backend/build.gradle`의 `testcontainers-bom`을 1.21.4로 올려 해결, 여러 세션에 걸쳐 "원인 불명 환경 문제"/"버전 비호환 추정"으로만 남아있던 이슈). 같은 증상이 Docker Desktop 업데이트 후 재발하면 `docs/DEVELOPMENT.md` §1 "Testcontainers가 ... 실패" 참고(머신 전역 `~/.testcontainers.properties` 핀은 처음엔 원인으로 오판했던 별개 항목이니 혼동 주의)
- Vite는 webpack과 달리 Node.js 전역을 자동 폴리필하지 않는다. `sockjs-client`처럼 `global`을 참조하는 라이브러리를 그대로 번들하면 브라우저에서 "global is not defined"로 페이지 전체가 깨진다 - `frontend/vite.config.ts`에 `define: { global: 'globalThis' }` 필요(이 문제는 해당 라이브러리를 실제로 번들에 포함시키는 순간에만 드러나므로, import만 추가하고 아직 렌더 경로에 안 걸린 코드에서는 안 잡힐 수 있음에 주의)
- SockJS 클라이언트의 XHR 폴백 트랜스포트(`/ws/stocks/info` 핸드셰이크 등)는 기본적으로 `withCredentials: true`로 요청한다. REST API 인증 자체는 쿠키가 아니라 `Authorization` 헤더를 쓰더라도, 백엔드 CORS 설정에 `allowCredentials(true)`가 없으면 오리진이 일치해도 브라우저가 응답을 차단한다(`backend/api/.../auth/config/SecurityConfig.java`). 허용 오리진을 특정 값 하나로 고정해뒀다면(와일드카드 아님) 안전하게 켤 수 있다
- **디자인(UI)이나 백엔드 로직을 수정할 때, 요청받은 범위를 벗어나 기존 코드의 구조·스타일·네이밍을 임의로 리팩터링하지 않는다.** 세션이 반복되며 관련 없는 기존 코드가 목적 없이 크게 바뀌어버리는 문제가 실제로 있었음(사용자 피드백, 2026-07-13) - 새 기능/수정은 기존 컴포넌트·패턴을 최대한 재사용하고 꼭 필요한 파일만 건드릴 것. "더 낫다"는 이유만으로 관련 없는 파일의 포매팅·순서·네이밍을 바꾸지 말고, 정말 필요한 리팩터링이면 별도로 제안해 승인받은 뒤 진행할 것(끼워넣기 금지). 전역 규칙은 `~/.claude/CLAUDE.md` "기존 코드 변경 범위 원칙" 참고
- **Claude Code가 로컬 검증을 위해 백엔드를 띄울 땐 기본 8080이 아니라 8081
  포트를 쓴다(2026-07-20 피드백 - 이전엔 8080에 이미 떠 있는 프로세스를
  전부 "내가 정리해야 할 stale 프로세스"로 간주하고 죽인 뒤 재시작했는데,
  그중 일부가 실제로는 사용자가 계속 띄워두고 쓰던 라이브 세션이었을
  가능성이 있음 - 사용자 3001 프론트 세션을 건드리지 않는 것과 동일한
  이유로 백엔드도 분리).** `SERVER_PORT=8081 APP_CORS_ALLOWED_ORIGIN=http://localhost:3002
  nohup ./gradlew :api:bootRun &`처럼 띄우고(Spring Boot는 `SERVER_PORT`
  환경변수가 `application.yml`의 `server.port: 8080` 하드코딩보다 우선순위가
  높아 별도 코드 수정 없이 오버라이드된다), 검증용 프론트엔드는
  `VITE_API_BASE_URL=http://localhost:8081 npm run dev -- --port 3002`로
  띄워 이 8081 백엔드를 바라보게 한다(`frontend/src/config/env.ts` 기본값이
  `http://localhost:8080`이라 반드시 오버라이드해야 함). **종료는 절대
  `pkill -f "QuantLimeApplication"`처럼 프로세스명 패턴을 쓰지 않는다** -
  이 패턴은 포트를 구분하지 않아 사용자의 8080 세션이 실행 중이면 그것까지
  함께 죽일 수 있다. 대신 `lsof -ti:8081 | xargs -r kill -9`로 8081에
  바인딩된 PID만 정확히 찾아서 종료할 것(전역 `~/.claude/CLAUDE.md` "로컬
  개발 서버 종료 원칙" 참고). 사용자가 "8080 써도 돼"처럼 명시적으로
  허용한 경우만 예외
- **Claude Code가 로컬 검증을 위해 프론트엔드 개발 서버를 띄울 땐 기본 3001이 아니라 3002 포트를 쓴다.** 사용자가 로컬에서 3001번으로 자신의 개발 서버를 계속 띄워두고 보는데, Claude가 검증용으로 3001을 반복해서 껐다 켰다 하면 사용자의 세션이 자꾸 끊긴다(2026-07-17 피드백). `npm run dev -- --port 3002`처럼 CLI 플래그로 오버라이드할 것 - `vite.config.ts`의 `strictPort: true`/기본값 3001 자체는 OAuth 리다이렉트 URI가 그 포트를 전제로 하므로(§3) 건드리지 않는다. 검증이 끝나면 3002 프로세스만 종료하고 사용자의 3001 세션은 절대 건드리지 않는다

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
mysql -h 127.0.0.1 -P 3308 -u root -pquantlime quantlime

# Redis 접속
docker exec -it quantlime-redis redis-cli

# 배포 이미지 로컬 빌드 검증 (AWS 자격증명 불필요, docs/DEVELOPMENT.md §3 참고)
docker compose -f docker-compose.prod.yml build

# 배포 오버레이 머지 결과만 확인(컨테이너 기동 없이)
docker compose -f docker-compose.prod.yml -f docker-compose.cloudwatch.yml config

# 모니터링 스택(Prometheus/Grafana/Alertmanager) 로컬 기동 - AWS 자격증명
# 불필요, cloudwatch 오버레이와 달리 실제로 up까지 검증 가능
# (docs/DEVELOPMENT.md, docs/DEPLOYMENT.md §13 참고)
docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.yml --env-file .env.prod up -d
```

---

## 작업 기록

세션별 상세 변경 히스토리는 [`docs/CHANGELOG.md`](docs/CHANGELOG.md) 참고 -
세션마다 계속 추가되는 성격이라 이 파일(참조 문서)과 분리해뒀다(2026-07-20).
