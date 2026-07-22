# QuantLime 로드맵 — HTS 미구현 기능 타당성 분석

## 개요
토스 HTS 웹 화면 기능 중 **토스 Open API만으로는 불가능**한 것들을, 외부 API
추가 또는 자체 구현으로 만들 수 있는지 난이도(상/중/하)·근거와 함께 정리한다.
기획 스코프 결정을 위한 자료다.

### 사용자 결정으로 제외된 항목 (분석 대상 아님)
- 호가 잔량 비율 바 (토스 거래비율 근사 대체)
- 선물·금 현물 위젯
- 주문/보유잔고/조건주문
- 통화($/원) 토글 — 실거래가 전부 KRW라 실질 가치 낮음(환율 "위젯"은 #1로 별개 유지)
- 종목 상세 슬라이드오버 — 이미 완전한 `/stocks/:code` 페이지가 있어 중복 구현
- 설정 페이지 — 무엇을 설정할지부터 정의 안 됨, 범위 미정 상태로 보류

### 재사용 가능한 기존 자산 (난이도 하향 근거)
- `common/util/ExternalApiInvoker.java` — 외부호출 에러매핑 공용 유틸
- `infra/kind/` (Client/Config/Properties/dto/exception 5파일) — 비-토스 외부소스 스크래핑/조회 템플릿. jsoup 존재
- `infra/toss/` — RestClient 빈 + `@ConfigurationProperties` 와이어링, rate-limit 특수매핑
- `infra/python/PythonEngineClient.java` + `quant-engine/` — Python 컴퓨트 잡 추가 템플릿
- 스케줄러 3종: `OhlcvCollectorScheduler`(전종목 배치+페이싱+백오프), `WatchlistPriceRelayScheduler`(Redis→STOMP 중계), `StockMasterSyncScheduler`(주간 배치)
- **`quant-engine/calculator/commentary.py`** — 이미 Anthropic `claude-haiku-4-5` 연동 + 규칙기반 폴백 구현됨 (AI 요약 절반 존재)

### 토스 스펙 확정 사실 (랭킹 난이도를 가르는 근거)
- `/api/v1/prices`(현재가): **1호출당 최대 200종목 일괄**. 반환 = `symbol/timestamp/lastPrice/currency` — **거래량·거래대금 없음**.
- `/api/v1/candles`(일봉): 거래량 제공. 그러나 **1호출당 1종목**. 별도 `MARKET_DATA_CHART` 그룹.
- Rate limit = **초당 토큰 버킷**(일일 쿼터 아님). 그룹별 초당 N개(스펙 예시 10). 요청 1건=토큰 1개. 소진 시 `429`+`Retry-After`.
- 토큰 효율: `prices` 1토큰=200종목 vs `candles` 1토큰=1종목. → **거래량이 필요한 랭킹은 candles(1:1)에 갇힌다.**

## 우선순위

아래 표가 유일한 우선순위 기준이다(과거엔 "요약 매트릭스"와 "권장
우선순위"가 서로 다른 기준·순서로 나뉘어 있었는데, 2026-07-12에 하나로
통합했다). **#**는 아래 "상세 분석" 섹션 번호와 대응한다.

| 순위 | # | 기능 | 상태 | 난이도 | 비고 |
|---|---|---|---|---|---|
| 1 | 1 | 환율·비트코인 위젯 | ✅ **완료**(2026-07-12) | 하 | `TossApiClient.getExchangeRate` + `infra/upbit/` |
| 2 | 5 | 호가/체결/상하한가/매수유의/해외장운영 | 미착수 | 하 | 토스 API에 이미 존재, 클라이언트 메서드 추가만 |
| 3 | 7 | 의견 피드백 모달 | 미착수 | 하 | 순수 자체 구현, `Feedback` 엔티티 하나 + 등록 API |
| 4 | 2a | 급등락(등락률) 실시간 전종목 랭킹 | ✅ **완료**(2026-07-12) | 중 | `MarketPriceSweepScheduler`(100ms 주기) |
| 5 | 2b | 거래대금/거래량 랭킹 (일배치) | 미착수 | 중 | KRX/네이버 일별 데이터 스크래핑 → 장마감 후 1배치 |
| 6 | 1 | 코스피·코스닥 국내지수 | 미착수 | 중 | 토스에 지수 심볼 없음 → 네이버금융 비공식 or KRX 스크래핑 |
| 7 | 1 | 해외지수·VIX | 미착수 | 중 | 외부 API 신규 연동(Finnhub 등) |
| 8 | 3 | AI 요약 ("왜 올랐을까"/뉴스) | 미착수 | 중 | 뉴스 API 신규 + 기존 Anthropic 파이프라인 확장 |
| 9 | 6 | 관심종목 다중 그룹 관리 | 미착수 | 중 | 기존 `Watchlist` 스키마·API 계약 변경이 선행돼야 함 |
| 10 | 4 | 커뮤니티 (댓글/투자의견) | 미착수 | 중(범위 큼) | 기술 난이도는 낮지만 CRUD 범위(글/댓글/좋아요)가 큼 |
| 11 | 2c | 거래대금/거래량 실시간 전종목 랭킹 | 미착수 | 상(조건부) | candles 1:1 병목 → 외부소스 우회 or 범위축소 필요 |
| 12 | 8 | 분봉(1분봉) 차트 | TODO(보류) | 하~중 | 토스 API가 이미 지원(`interval=1m`), 다만 페이지네이션·저장 설계가 필요해 이번 스코프에서 보류 |

제외 결정된 항목(통화 토글, 종목 상세 슬라이드오버, 설정 페이지, 호가
잔량 비율 바, 선물·금 위젯, 주문/보유잔고)은 위 표에 없다 - 맨 위
"사용자 결정으로 제외된 항목" 참고.

## 상세 분석

### 1. 지수/시세 위젯 — 하~중
- **환율(하)**: 토스 `/api/v1/exchange-rate` 미구현 상태로 존재 → `TossApiClient` 메서드 추가.
- **비트코인(하)**: Upbit/CoinGecko 무료 공개 API, 인증 불필요.
- **해외지수·VIX(중)**: Finnhub(무료 60req/min) 또는 Twelve Data(무료 800req/day) 신규 연동 + 캐싱. `infra/` 5파일 템플릿 재사용.
- **국내지수 코스피/코스닥(중)**: 토스에 지수 심볼 없음 → 네이버금융 비공식 or KRX 스크래핑.

### 2. 전종목 랭킹 — 지표별로 난이도 갈림
난이도는 **필요한 데이터를 어느 API가 주느냐**로 결정된다.

**2a. 급등락(등락률) 실시간 랭킹 — 중 (가능)**
- 등락률 = 현재가 vs 전일종가. `prices`가 벌크(200종목/호출) → 전종목 2,706개 = 14호출/스윕. 초당 10건이면 1스윕 ≈ 1.4초 → 3~5초 주기 갱신 = 사실상 실시간.
- 전일종가 2,706개는 장 시작 전 하루 1회만 확보(틱당 비용 아님). `WatchlistPriceRelayScheduler`(당시 `PriceBroadcastScheduler`) 패턴 확장.

**2b. 거래대금/거래량 랭킹 (일배치) — 중 (가능)**
- 장마감 후 KRX/네이버 전종목 일별 시세 1회 스크래핑 → 랭킹 저장 → 조회 API. HTS도 "어제 기준"이라 일배치로 충분. `infra/kind`+`OhlcvCollectorScheduler` 템플릿.

**2c. 거래대금/거래량 실시간 랭킹 — 상 (조건부)**
- **벽의 정체**: 거래량은 `prices`가 안 주고 `candles`(1종목/호출)에만 있음. 전종목 실시간 = 토큰 2,706개 → 초당 10개면 1스윕 ≈ 270초(4.5분). "실시간" 불가 + `MARKET_DATA_CHART` 그룹 상시 점유 시 기존 일봉수집·백필이 굶음.
- **우회책**: ① 외부소스(네이버금융 시세 리스트, 다종목 거래대금 일괄) 스크래핑 ② 범위 축소(상위 시총 N종목만) ③ 하이브리드(일배치 후보군 → 장중 실시간 갱신).

### 3. AI 요약 — 중
- **이미 있는 것**: `commentary.py`가 Anthropic `claude-haiku-4-5` 연동 + 폴백. `PythonEngineClient`↔FastAPI 패턴 확립.
- **추가 필요**: 뉴스 소스 — 네이버 검색(뉴스) API(무료, 일 25,000건) 또는 RSS.
- **"뉴스 3개 요약"(중)**: 뉴스 API 연동 + Anthropic 호출부 확장 + FastAPI 엔드포인트 + Java DTO.
- **"왜 올랐을까"(중~상)**: 급변 감지 + 뉴스 인과 매칭 + 요약. 병목은 매칭 품질·환각·비용.

### 4. 커뮤니티 — 중 (기술 하, 범위 중)
- 외부 API 불필요, 순수 자체 CRUD. 기존 컨벤션 그대로. 좋아요/신고/모더레이션까지 가면 공수·운영부담 증가.

### 5. 호가/체결/상하한가/매수유의/해외장운영 — 하
- 토스 스펙에 이미 존재(`/api/v1/orderbook`, `/trades`, `/price-limits`, `/stocks/{symbol}/warnings`, `/market-calendar/US`). `TossApiClient` 메서드 + DTO 추가만.

### 6. 관심종목 다중 그룹 관리 — 중
- 현재 `Watchlist`는 (user_id, stock_id) 평면 목록 - 그룹 개념 자체가 없음(§6, §8 Phase 2 결정 참고: 애초에 "규모가 커지지 않을 것"이라 YAGNI로 평면 유지).
- 그룹 CRUD(`WatchGroup` 엔티티, user당 1:N) + `Watchlist.group_id` FK 추가 + 그룹 간 종목 이동/정렬 API가 필요. 기술 난이도는 낮지만(순수 CRUD) 기존 관심종목 스키마·API 계약을 건드리는 변경이라 마이그레이션 설계가 선행돼야 함.

### 7. 의견 피드백 — 하
- 별점(1~5) + 코멘트 텍스트를 저장하는 단순 엔티티 하나(`Feedback`)와 등록 API 하나로 충분. 노출/집계용 관리자 화면은 이번 스코프 밖.
- 홈 리디자인 세션(2026-07-12)에서 "제출해도 아무 데도 안 가는 가짜 CTA"라 판단해 프론트에서 뺐던 항목 - 백엔드가 실제로 저장하기 시작하면 그대로 복원 가능.

### 8. 분봉(1분봉) 차트 — 하~중 (TODO, 보류)
- 종목 상세 차트에 일봉/주봉/월봉 외 분봉 옵션을 추가하는 건인데, 2026-07-14
  세션에서 사용자 요청으로 지금 당장 구현하지 않고 TODO로만 남겨둔다.
- 토스 API(`GET /api/v1/candles`, `toss-openapi.json`)는 `interval` 파라미터로
  이미 `1m`(1분봉)을 지원한다 - 외부 소스 연동이 필요한 다른 항목들과 달리
  "불가능해서 보류"가 아니라 "설계가 더 필요해서 보류"인 케이스.
- 구현 시 고려해야 할 것들:
  - **호출당 최대 200개(count) 제약**: 1분봉 200개는 약 3.3시간 분량이라,
    "오늘 하루 전체 분봉"을 보여주려면 `before` 커서로 페이지네이션 필요
    (`DailyPriceService.backfillHistoryIfNeeded`가 쓰는 것과 동일한 패턴).
  - **Rate Limit 그룹 경합**: 분봉도 일봉과 같은 `MARKET_DATA_CHART` 그룹을
    쓴다 - 기존 `OhlcvCollectorScheduler`(일봉 배치)·백필과 예산을 나눠 써야
    하고, 여러 사용자가 동시에 여러 종목 분봉을 보면 상시 폴링이 그룹을
    독점할 수 있다.
  - **저장 여부 설계**: 일봉처럼 `daily_price` 테이블에 영속 저장할지,
    아니면 조회 시점에만 토스를 직접 호출해 즉시 렌더링하고 저장하지
    않을지 결정 필요 - 분봉은 데이터량이 커서(종목당 하루 약 390개, 코스피만
    ~2,700종목) 전종목 상시 수집은 비용 대비 실익이 낮고, "현재 보고 있는
    종목만 온디맨드 조회"가 더 현실적인 선택지로 보인다.
  - **프론트 차트**: `chartInterval`에 `'1m'` 옵션 추가 + 백엔드
    `GET /api/stocks/{code}/chart` API에 `period=minute` 같은 파라미터 확장
    (현재는 `period=daily`만 지원, `PriceController` `@Pattern` 검증 포함).

### 9. 검색모달 "지금 뜨는 산업" — 중 (TODO, 보류)
- 검색 오버레이(`SearchOverlay.tsx`)에 예시 데이터로 있던 섹션을
  2026-07-17 세션에서 제거(가짜 등락률을 보여주는 것보다 없는 편이 낫다는
  판단, §7 "없는 데이터를 숫자로 꾸며내지 않는다" 원칙과 동일). 실데이터로
  복원하려면 산업(섹터)별 등락률 집계가 필요한데, 지금 `MarketRankingCache`가
  들고 있는 전종목 스냅샷(`MarketPriceSweepScheduler`)에 종목별 `sector`
  필드는 이미 있으므로(`StockDetailResponse.sector` 등) 새 외부 API 없이도
  섹터별 그룹핑 + 평균 등락률 계산만 추가하면 구현 가능해 보인다(정확한
  집계 방식은 미검토 - 단순 평균/거래대금 가중 등 설계 필요).

## 진행 현황

### ✅ 환율·비트코인 위젯 (2026-07-12 완료)
- `TossApiClient.getExchangeRate(base, quote)` 추가(`/api/v1/exchange-rate`, `MARKET_INFO`
  그룹이라 시세 폴링과 예산이 분리됨) + `infra/upbit/`(`UpbitApiClient` 등 5파일,
  `infra/kind/` 템플릿 그대로 재사용, 인증 불필요한 공개 티커 API).
- `MarketIndexCache`가 20초 TTL로 두 응답을 합쳐 캐싱(`MarketCalendarCache`와 동일한
  단순 TTL 캐시 패턴) → `GET /api/market/indices`.
- 프론트 `MarketIndexRow`가 달러 환율·비트코인만 실데이터로 표시하고 나머지 5개
  지수(코스피/코스닥/나스닥/S&P500/필라델피아반도체)는 여전히 예시 데이터 - 캡션에
  "환율·비트코인은 실시간, 나머지는 예시 데이터"로 명시.

### ✅ 급등락(등락률) 실시간 전종목 랭킹 (2026-07-12 완료, 2026-07-15/16 재설계)
- `MarketPriceSweepScheduler`(구 `MarketRankingScheduler`)가 전 상장 종목
  (`AllListedStockCache`, 10분 TTL)을 200개씩 청크로 조회해 등락률을 계산하고
  `MarketRankingCache`(메모리, Redis 아님)에 적재하는 동시에, 심볼별 시세를
  `PriceCacheStore`(Redis)에도 적재하는 유일한 가격 조회 파이프라인이 됐다 -
  원래는 관심종목용 스케줄러가 별도로 Toss를 호출해 관심종목이면서 전종목이기도
  한 종목이 중복 조회되던 걸, 이 스케줄러 하나로 통합(`WatchlistPriceRelayScheduler`,
  구 `PriceBroadcastScheduler`는 이제 Toss를 호출하지 않고 이 Redis 캐시만 읽어
  STOMP로 중계). 청크 딜레이 120ms, 기본 주기 100ms(`market-ranking.poll-interval-ms`).
- 청크 하나가 429 등으로 실패해도 그 청크만 스킵하고 다음 틱에 재시도 - 전체 스윕을
  막지 않음.
- `GET /api/market/ranking?sort=gainers|losers&limit=`로 조회. 거래대금/거래량은
  여전히 미구현(2a만 완료, 2b/2c는 아직) - 프론트 `RankingTable`이 급상승/급하락
  탭에서만 실데이터(5개 컬럼: 순위/종목/현재가/등락률/산업)를 쓰고, 거래대금/거래량
  탭은 기존 예시 데이터(7개 컬럼)를 그대로 유지.
- 장이 닫혀 있으면(`MarketCalendarCache` 재사용) 빈 배열을 반환 - 프론트가
  "장이 열려 있지 않거나 아직 데이터가 준비되지 않았습니다"로 정직하게 표시.
