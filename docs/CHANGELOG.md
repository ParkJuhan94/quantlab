# QuantLab — 작업 기록 (세션별 변경 히스토리)

> `CLAUDE.md`에서 분리된 문서(2026-07-20). 세션이 쌓일수록 무한정
> 늘어나는 히스토리라, 참조 문서(구조/컨벤션/현재 Phase 현황)와 분리해
> `CLAUDE.md`를 가볍게 유지하기 위함 — 각 세션이 무엇을·왜 했는지,
> 어떤 트레이드오프를 골랐는지의 원본 기록. 최신 항목이 아래쪽에 쌓인다.
> 프로젝트 구조·컨벤션·현재 진행 상황은 [`../CLAUDE.md`](../CLAUDE.md) 참고.

---

<details>
<summary>2026-07-08 - 프론트엔드 검증 보강 + 다듬기 + 개발 가이드 문서화</summary>

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

</details>

<details>
<summary>2026-07-09 - Phase 6 배포 아티팩트 준비(컨테이너화 + CI/CD)</summary>

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

</details>

<details>
<summary>2026-07-09 - Phase 6 확장(Elastic IP, 로그/모니터링/백업)</summary>

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

</details>

<details>
<summary>2026-07-11 - 네이버 로그인 닉네임 필수 오류 수정 (+ 카카오 방어 로직 동기화)</summary>

**변경 사항**
- 실서버 로그에서 네이버 로그인 시 `IllegalArgumentException: 닉네임은 필수입니다.`
  발생을 확인 - `User.validateUser`의 `Assert.hasText(nickname, ...)`에서
  터진 것으로, 원인은 `NaverOAuthClient`가 네이버 응답의 `nickname` 필드를
  fallback 없이 그대로 `OAuthUserInfo`에 넘기고 있었기 때문(네이버 콘솔에서
  닉네임이 선택 동의 항목이라 값이 안 올 수 있음)
- 구글은 `name` 클레임이 사실상 항상 오는 `profile` 스코프라 문제가 없었고,
  카카오는 이미 `DEFAULT_NICKNAME` fallback이 있었던 것과 비교해 네이버만
  누락돼 있었음을 확인 후 네이버에도 동일한 fallback(`"네이버사용자"`) 추가
- 겸사겸사 카카오의 fallback 조건도 `nickname != null` → `StringUtils.hasText(nickname)`으로
  강화(카카오가 빈 문자열을 내려주는 경우까지 방어)
- 중간에 "구글처럼 이름을 항상 받아오면 되지 않냐"는 아이디어로 카카오/네이버
  모두 닉네임 대신 실명(`이름`) 필드를 쓰도록 한 차례 변경했으나, 카카오
  디벨로퍼스 콘솔에서 `이름` 동의 항목 자체가 사업자 심사 없이는 추가가
  안 된다는 걸 사용자가 콘솔에서 직접 확인 - 다시 닉네임 기반으로 전량 되돌림
  (구글은 애초에 이름/닉네임이 분리된 필드가 아니라 되돌릴 대상이 없었음)

**변경 파일**
- `backend/core/src/main/java/com/quantlab/infra/oauth/NaverOAuthClient.java` - `DEFAULT_NICKNAME` fallback 추가
- `backend/core/src/main/java/com/quantlab/infra/oauth/KakaoOAuthClient.java` - null 체크 → `StringUtils.hasText` 강화
- `backend/core/src/test/java/com/quantlab/infra/oauth/{Kakao,Naver}OAuthClientTest.java` - fallback 검증 테스트 추가

**결정 사항**
- 카카오/네이버의 "닉네임" 동의 항목은 콘솔에서 필수 동의로 바꾸면 항상
  값이 오게 만들 수 있지만(구글의 `profile` 스코프와 동일한 효과), 그와
  별개로 코드의 fallback은 유지하기로 함 - 콘솔 설정 드리프트나 기존
  선택 동의 가입자의 재로그인 케이스에 대한 안전망 비용이 거의 0

**다음 작업**
- 카카오/네이버 콘솔에서 "닉네임" 동의 항목을 필수 동의로 전환할지는
  사용자가 판단 필요(실명/이름 동의는 카카오 기준 사업자 심사가 필요해 보류)
- 세션 시작 시점부터 있던 미커밋 변경 다수(`OAuthProperties.java`,
  `GoogleOAuthClientTest.java`, `application.yml`, `.env.example` 2건,
  `docs/DEPLOYMENT.md`, `frontend/README.md`, 프론트 차트 지표 관련
  3개 파일)는 이번 세션 범위 밖이라 그대로 둠 - 다음 세션에서 검토 필요

</details>

<details>
<summary>2026-07-11 - 종목 로고 표시(네이버 금융 비공식 정적 경로 연동)</summary>

**변경 사항**
- 토스증권 Open API 스펙(`toss-openapi.json`)에 로고/이미지 필드가 전혀
  없어 기업 로고 조회가 불가능함을 확인. 대안으로 네이버 금융 모바일 API
  (`m.stock.naver.com/api/stock/{code}/basic`)의 JSON 응답을 실제로
  호출해 `itemLogoUrl`/`itemLogoPngUrl` 필드에서 정적 로고 경로 패턴
  (`ssl.pstatic.net/imgstock/fn/real/logo/png/stock/Stock{종목코드}.png`)을
  발견 - 대형주(삼성전자·카카오)뿐 아니라 소형주 5종목을 무작위 표본
  추출해 curl로 200 응답을 재검증한 뒤 채택
- 이 경로는 종목 코드만으로 결정적으로 생성 가능해 별도 API 호출/캐싱
  계층 없이 `StockMapper`에서 문자열 포맷팅만으로 `StockDetailResponse.
  logoUrl`을 채움
- 프론트엔드에 `StockLogo` 공용 컴포넌트를 추가해 검색 결과 목록과 종목
  상세 페이지 헤더에 적용 - 이미지 로드 실패 시(`onError`) 종목명 첫
  글자 원형 placeholder로 조용히 대체

**변경 파일**
- `backend/core/src/main/java/com/quantlab/stock/dto/response/StockDetailResponse.java` - `logoUrl` 필드 추가
- `backend/core/src/main/java/com/quantlab/stock/dto/mapper/StockMapper.java` - 종목 코드 기반 로고 URL 생성
- `frontend/src/types/stock.ts` - `logoUrl` 타입 반영
- `frontend/src/components/common/StockLogo.tsx`(신규) - 로드 실패 폴백 포함 공용 로고 컴포넌트
- `frontend/src/components/search/SearchResultItem.tsx`, `frontend/src/pages/StockDetailPage.tsx` - 로고 적용

**결정 사항**
- 네이버가 공식 문서화한 계약이 아닌 비공식 정적 경로이므로, 백엔드에서
  존재 여부를 사전 검증(HEAD 요청 등)하거나 캐싱하지 않고 프론트 `onError`
  폴백에만 의존하기로 함 - 서버 왕복을 늘리지 않으면서, 경로가 막히거나
  개별 종목에 로고가 없어도 화면이 깨지지 않도록 하는 선에서 방어 비용을
  최소화
- 관심종목 목록·스코어 대시보드 랭킹에도 로고를 넣을 수 있지만, 이번엔
  요청받은 범위(종목 상세/검색)로 한정 - 확장은 동일 패턴 재사용으로 쉬움

**다음 작업**
- 사용자가 증권사 계좌 연동(보유종목/수익률 표시) 가능 여부를 문의 -
  토스증권 현재 연동은 사용자별 위임이 아닌 앱 단위 Client Credentials라
  개인 보유잔고 조회 불가. 한국투자증권(KIS) Open API가 개인 발급 가능해
  가장 현실적인 시작점이라고 답변만 하고 실제 구현은 아직 착수 안 함 -
  사용자가 증권사를 확정하면 다음 세션에서 설계 착수
- 세션 시작 시점부터 있던 미커밋 변경 다수(`OAuthProperties.java`,
  `GoogleOAuthClientTest.java`, `application.yml`, `.env.example` 2건,
  `docs/DEPLOYMENT.md`, `frontend/README.md`, `CandleChart.tsx`,
  `config/oauth.ts`, `IndicatorControls.tsx`, `utils/indicators.ts`)는
  여전히 이번 세션 범위 밖이라 그대로 둠 - 다음 세션에서 검토 필요

</details>

<details>
<summary>2026-07-12 - 홈 화면 리디자인(claude.ai/design 시안 "Quantlab 토스증권 UI 리디자인" 반영)</summary>

**변경 사항**
- claude.ai/design 프로젝트에서 가져온 시안(`Quantlab Screener.dc.html`,
  turn t3/옵션 2c "홈")을 기준으로 `/`(기존 `WatchlistPage`)를 새
  `HomePage`로 교체 - 검색 오버레이, 주요 지수 위젯, 실시간 시세
  랭킹 테이블, 접이식 우측 패널(관심/최근 본/실시간 탭)로 구성.
  같은 시안의 피드(커뮤니티 게시판, turn t5) 화면은 사용자 확인 후
  이번 범위에서 제외 - `/feed`는 시안 자체가 쓰던 "준비 중" 플레이스홀더
  패턴을 그대로 재사용
- 시안에는 있지만 실제 백엔드가 없는 기능(전종목 실시간 랭킹·시장 지수는
  `docs/ROADMAP.md` Phase 7 기획 중, 관심종목 그룹·최근 본·AI 코멘트
  배너 등은 아예 계획에도 없음)을 어떻게 다룰지 사용자에게 먼저 확인:
  "백엔드 없는 기능은 프론트 목/로컬 상태로" 결정
  - 실시간 시세 랭킹 테이블·주요 지수 위젯: `frontend/src/mock/marketMock.ts`에
    예시 숫자로 구현하되, 종목 코드는 실제 DB에 있는 종목(삼성전자·
    SK하이닉스 등 8종)을 그대로 써서 로고·클릭 시 상세 이동·관심종목
    등록/해제·실시간 소켓 시세는 전부 진짜로 동작함 - 등락률/거래대금/
    시가총액만 화면용 예시 숫자. UI에도 "예시 데이터" 캡션을 노출해
    실데이터로 오인되지 않게 함
  - 최근 본 종목: 실제 기능으로 구현(`localStorage` 기반,
    `StockDetailPage` 방문 시 자동 기록) - 백엔드 없이도 정직하게 만들
    수 있는 기능이라 목이 아니라 진짜로 동작하게 함
  - 관심종목 다중 그룹 관리(그룹 생성/이름변경/삭제), "AI 요약" 인사이트
    배너, 의견 피드백 모달, 설정 아이콘, 환율/원화 표시 토글, 종목 상세
    슬라이드오버(시안은 차트/스코어/공시를 패널로 재구현했으나 실제
    앱엔 이미 완전한 `/stocks/:code` 페이지가 있음)는 전부 제외 - 저장도
    안 되는데 저장되는 것처럼 보이는 가짜 관리 UI, 실제로 아무 데도
    전송되지 않는 피드백 폼처럼 사용자를 속이는 형태가 되거나, 이미
    있는 기능을 목적 없이 중복 구현하게 되는 경우라 판단
- Tailwind v4 `@theme`에 `--color-accent`(#3752ff)·`--font-logo`
  (Space Grotesk) 추가, `AppHeader`를 로고 마크 + 검색 트리거("/"
  단축키) + 홈/피드 내비게이션으로 재작성

**변경 파일**
- `frontend/src/pages/HomePage.tsx`(신규), `frontend/src/pages/
  FeedPlaceholderPage.tsx`(신규) - `WatchlistPage.tsx` 대체(삭제)
- `frontend/src/components/home/{MarketIndexRow,RankingTable,
  HomeSidePanel}.tsx`(신규)
- `frontend/src/components/search/SearchOverlay.tsx`(신규) - 기존
  `useStockSearch`/`useDebouncedValue`/`SearchResultItem` 재사용
- `frontend/src/mock/marketMock.ts`(신규), `frontend/src/storage/
  {searchHistoryStorage,recentlyViewedStorage}.ts`(신규,
  `tokenStorage.ts` 패턴 재사용), `frontend/src/utils/stockLogo.ts`(신규)
- `frontend/src/components/layout/AppHeader.tsx`, `frontend/src/App.tsx`
  (라우트: `/` → `HomePage`, `/feed` 추가), `frontend/src/pages/
  StockDetailPage.tsx`(최근 본 기록 추가), `frontend/index.html`,
  `frontend/src/index.css`

**결정 사항**
- `/`, `/feed`를 기존과 동일하게 `ProtectedRoute` 하위에 유지(비로그인
  홈 공개 열람으로 바꾸지 않음) - 관심종목 조회가 401 재발급 실패 시
  로그인으로 리다이렉트시키는 기존 인터셉터 로직과 충돌해 별도 인증
  구조 변경이 필요해지므로, 이번 범위(홈 화면 UI) 밖의 결정으로 보고
  현행 유지
- 실시간 랭킹 테이블에 있던 "해외" 스코프 필터·통화($/원) 토글은
  구현하지 않음 - QuantLab은 "국내 주식 한정"(§1)이 제품 범위이고
  시안의 해외종목 예시는 이 제품엔 애초에 해당하지 않는 개념이라
  판단. 다만 나스닥/S&P500 등 해외 지수 "위젯"은 `docs/ROADMAP.md` #1이
  이미 정보성 표시로 계획하고 있어 그대로 유지
- 실제 백엔드로 로컬 검증: `docker-compose` 인프라 + 백엔드 `bootRun` +
  프론트 `npm run dev` 기동 후 Playwright로 검색→종목 상세 이동,
  랭킹 테이블 하트 클릭→실제 관심종목 등록/해제, 최근 본 탭 자동 기록,
  패널 접기/펼치기, `/feed`·`/dashboard` 라우팅까지 실제 브라우저로 확인
  (테스트 중 등록한 NAVER는 검증 후 원상복구)

**다음 작업**
- 피드(커뮤니티 게시판) 화면은 여전히 미착수 - `docs/ROADMAP.md` #4
  (기술 난이도 하, 범위는 중) 참고해 다음 세션에서 별도로 설계 필요
- `docs/ROADMAP.md`가 분석한 실제 기능들(전종목 실시간 랭킹, 지수
  위젯, AI 요약)은 여전히 미착수 - 이번 세션은 그 자리를 UI로 먼저
  채운 것이고, "권장 우선순위" 순서대로 착수하면 이번에 심어둔 예시
  데이터를 실데이터로 교체하는 흐름이 됨
- 세션 시작 시점부터 있던 미커밋 변경(`OAuthProperties.java`,
  `GoogleOAuthClientTest.java`, `application.yml`, `.env.example` 2건,
  `docs/DEPLOYMENT.md`, `frontend/README.md`, `config/oauth.ts`,
  `IndicatorControls.tsx`, `utils/indicators.ts`)는 여전히 이번 세션
  범위 밖이라 그대로 둠 - 다음 세션에서 검토 필요

</details>

<details>
<summary>2026-07-12 - 홈 화면 목(mock) 데이터 중 2건을 실데이터로 전환(환율·비트코인 위젯, 급등락 실시간 랭킹)</summary>

**변경 사항**
- 직전 세션(홈 리디자인)에서 예시 데이터로 남겨뒀던 항목들을
  `docs/ROADMAP.md` 난이도 기준으로 재우선순위화한 뒤, 사용자가 "환율·
  비트코인 위젯"과 "급등락 실시간 랭킹" 둘 다 진행하기로 결정 - 두
  기능 모두 실제 백엔드를 새로 구현
- **환율·비트코인**: `TossApiClient.getExchangeRate(base, quote)` 추가
  (`GET /api/v1/exchange-rate`, Rate Limits Group이 시세 폴링과 분리된
  `MARKET_INFO`라 `PriceBroadcastScheduler` 예산과 안 겹침) + Upbit
  공개 티커 API 신규 연동(`infra/upbit/`, `infra/kind/` 5파일 템플릿
  그대로 재사용 - 인증 불필요한 공개 REST API라 KIND보다 단순).
  `MarketIndexCache`가 20초 TTL로 두 응답을 합쳐 캐싱(`MarketCalendarCache`와
  동일한 단순 TTL 캐시 패턴) → `GET /api/market/indices`
- **급등락 실시간 랭킹**: `MarketRankingScheduler` 신규 - 전 상장 종목
  (`AllListedStockCache`, 10분 TTL, `StockRepository.findByListingStatus`
  재사용)을 200개씩 청크로 `getCurrentPrices` 호출해 전일종가
  (`PreviousCloseCache` 재사용) 대비 등락률을 계산, `MarketRankingCache`
  (메모리 스냅샷, Redis 아님 - 단일 인스턴스 배치 결과라 재시작 시
  초기화돼도 다음 틱에 다시 채워짐)에 적재. `PriceBroadcastScheduler`와
  동일한 Toss `MARKET_DATA` Rate Limit 예산을 나눠 쓰므로 주기를 5초로
  분리(`market-ranking.poll-interval-ms`), 청크 하나가 429 등으로
  실패해도 그 청크만 스킵하고 다음 틱에 재시도(전체 스윕은 안 막음).
  `GET /api/market/ranking?sort=gainers|losers&limit=`
- 프론트 `RankingTable`을 급상승/급하락 탭에서만 실데이터(5개 컬럼 -
  거래대금/시가총액은 백엔드가 아직 없어 제외)로 전환하고, 거래대금/
  거래량 탭은 기존 예시 데이터(7개 컬럼)를 그대로 유지 - 데이터 출처가
  섞이지 않도록 캡션도 탭별로 다르게("실제 등락률로 정렬한..." vs
  "예시 데이터입니다..."). `HomeSidePanel`의 "실시간" 탭도 같은 급상승
  API를 재사용하도록 전환
- `MarketIndexRow`는 달러 환율·비트코인만 실데이터로 바꾸고 나머지
  5개 지수(코스피/코스닥/나스닥/S&P500/필라델피아반도체)는 여전히
  예시 데이터 - 캡션을 "환율·비트코인은 실시간, 나머지는 예시 데이터"로
  구체화

**변경 파일**
- `backend/core/.../infra/toss/{TossApiClient,dto/TossExchangeRateResponse,
  exception/TossApiErrorCode}.java` — 환율 조회 추가(`TOSS_007`)
- `backend/core/.../infra/upbit/`(신규 5파일) — Upbit 클라이언트
- `backend/core/.../market/`(신규) — `cache/{MarketIndexCache,
  AllListedStockCache,MarketRankingCache}`, `scheduler/MarketRankingScheduler`,
  `service/{MarketIndexService,MarketRankingService}`,
  `dto/response/{MarketIndexResponse,MarketRankingResponse}`,
  `exception/MarketErrorCode`
- `backend/api/.../market/controller/MarketController.java`(신규) —
  `GET /api/market/indices`, `GET /api/market/ranking`
- `backend/api/.../auth/config/SecurityConfig.java` — `/api/market/**`
  permit-all 추가(다른 사용자와 무관한 공개 시장 데이터라 `/api/stocks/**`와
  동일하게 취급)
- `backend/api/src/main/resources/application.yml`, `backend/.env.example` —
  `upbit.base-url`, `market-ranking.poll-interval-ms` 추가
- 테스트 4종(`AllListedStockCacheTest`, `MarketIndexCacheTest`,
  `MarketRankingCacheTest`, `MarketRankingSchedulerTest` - 전부 unit,
  기존 `WatchlistedStockCodeCacheTest`/`MarketCalendarCacheTest`/
  `PriceBroadcastSchedulerTest` 패턴 그대로 따름) + `MarketControllerTest`
  (integration, `MarketIndexCache`/`MarketRankingCache`를 직접 목으로
  대체 - `PriceControllerTest`가 `PriceCacheStore`를 목으로 대체하는 것과
  동일한 이유: TTL 있는 상태 저장 빈을 컨트롤러 테스트에 그대로 두면
  같은 스프링 컨텍스트를 공유하는 다른 테스트의 잔여 캐시값에 결과가
  갈릴 수 있음)
- `frontend/src/{types,api,hooks/queries}/market*.ts`(신규),
  `frontend/src/components/home/{MarketIndexRow,RankingTable,
  HomeSidePanel}.tsx`, `frontend/src/mock/marketMock.ts`(환율·비트코인
  항목 제거), `frontend/src/hooks/queryKeys.ts`

**결정 사항**
- 랭킹 캐시는 Redis가 아니라 메모리 `volatile` 필드로 결정 - 단일
  인스턴스 배치 결과를 5초마다 통째로 교체하는 용도라 인스턴스 간
  공유가 필요 없고, 재시작 시 초기화돼도 다음 틱(장중 기준 수 초 내)에
  다시 채워지므로 영속성 이점이 없음(`WatchlistedStockCodeCache` 등
  기존 캐시들과 동일한 "단순 메모리 TTL 캐시" 철학 유지)
- 거래대금/거래량은 이번 스코프에서 제외 - `docs/ROADMAP.md` #2b/#2c가
  이미 분석했듯 별도 데이터 소스(일배치 스크래핑 또는 외부 소스 우회)가
  필요해 "급등락(등락률)"과 난이도·구현 방식이 다름. 사용자가 승인한
  범위는 "환율·비트코인"과 "급등락 실시간 랭킹" 둘뿐이라 그대로 지킴
- 실시간 급등락 랭킹에 별도 WebSocket 토픽을 추가하지 않고 프론트
  React Query `refetchInterval: 5000`로 REST 폴링만 사용 - 기존
  `PriceBroadcastScheduler`도 "REST 폴링 → STOMP 변환"이지 Toss가 실제
  푸시를 주는 게 아니므로, 개별 종목처럼 소켓을 추가해도 진짜 실시간이
  되는 게 아니라 복잡도만 늘어난다고 판단
- 검증 도중 발견: 이전 세션에서 `pkill -f ":api:bootRun"`으로 백엔드를
  껐다고 여겼으나, 그 패턴은 그레이들 래퍼 프로세스만 매치하고 실제
  포그라운드로 남는 스프링 부트 JVM 자식 프로세스(`com.quantlab.
  QuantLabApplication`)는 매치하지 못해 살아있었음 - 이번 세션 초반
  `/api/market/**` permitAll 반영 전 코드로 계속 떠 있던 그 프로세스가
  새 bootRun의 포트 바인딩을 막아 401이 나온 것으로 원인 파악 후 PID로
  직접 kill해서 해결. 앞으로 bootRun을 내릴 땐 프로세스명
  (`QuantLabApplication`)까지 같이 pkill할 것
- Testcontainers 기반 통합 테스트(`MarketControllerTest` 포함)는 이
  로컬 환경에서 "Could not find a valid Docker environment"로 전부
  실패함을 확인(기존 `PriceControllerTest`도 동일하게 실패해 내
  변경과 무관한 환경 문제로 확인) - 실제 검증은 unit 테스트 15개
  전부 통과 + `docker-compose` 인프라로 띄운 실제 서버에 curl/Playwright로
  대체. 통합 테스트 자체는 CI 등 Docker 소켓이 정상 인식되는 환경에서
  마저 실행되어야 함
  - **(2026-07-13 후속)** 근본 원인 규명 + 해결 완료(Docker Desktop
    Engine API `MinAPIVersion` vs Testcontainers 1.20.4의 docker-java
    버전 비호환, `testcontainers-bom` 1.21.4로 해결) - 아래 §10 및
    2026-07-13 작업 기록 참고

**다음 작업**
- 여전히 미착수: 코스피·코스닥 국내지수(#1 나머지), 해외지수·VIX(#1),
  거래대금/거래량 랭킹(#2b/#2c), AI 요약(#3), 커뮤니티/피드(#4),
  관심종목 다중 그룹(#6), 의견 피드백(#7) - `docs/ROADMAP.md` 권장
  우선순위 참고
- Testcontainers Docker 소켓 인식 문제(로컬 환경)는 이번 세션 범위 밖
  이슈로 남겨둠 - 통합 테스트를 실제로 로컬에서 돌리려면 별도 조사 필요

</details>

<details>
<summary>2026-07-12 - CI 파이프라인 복구(무관한 4가지 브레이킹 이슈) + 토스 API 로그 격리</summary>

**변경 사항**
- `.github/workflows/ci.yml` 도입 이후 사실상 처음으로 fresh checkout
  검증이 제대로 돌면서, 서로 무관한 4가지 문제가 동시에 CI를 막고
  있었음을 하나씩 진단·수정:
  1. `gradle-wrapper.jar`가 한 번도 커밋된 적이 없었음 - `.gitignore`의
     부정 패턴(`!gradle/wrapper/gradle-wrapper.jar`)이 실제 경로
     (`backend/gradle/wrapper/`)와 안 맞아 `*.jar` 전체 무시 규칙에
     계속 가려짐(`git check-ignore -v`로 원인 규칙까지 확인)
  2. `AuthControllerTest`/`WatchlistControllerTest`가 로컬
     `backend/.env`(gitignore 대상) 존재에 암묵 의존 - CI엔 그 파일이
     없어 JWT 시크릿이 빈 문자열이 돼 `WeakKeyException`으로 8개 전부
     실패. `.env`를 실제로 치워보고 재현 후 테스트 전용 프로파일로 수정
  3. `StockDetailPage.tsx`가 참조하는 `IndicatorControls.tsx`/
     `utils/indicators.ts`/`CandleChart.tsx` 변경사항이 디스크엔
     있었지만 git엔 없었음(다른 세션의 git add 누락으로 보임) - 내용
     확인 후 사용자 승인 받아 그대로 커밋
  4. OAuth `redirectUri`를 정적 설정에서 요청별 동적값으로 옮기는
     리팩터링이 절반만 커밋돼 `OAuthProperties.Provider`와 테스트
     시그니처가 어긋나 있었음 - 실제 사용처(`OAuthClientDispatcher`
     등)를 추적해 안전한 완료인지 확인 후 마무리
- CI가 그린이 된 뒤에도 남아있던 "토스증권 API 토큰 발급 실패" 로그를
  사용자가 "토스 쪽에 GitHub IP를 허용 안 해서 그런 거냐"고 질문 -
  로그의 `Caused by` 체인을 직접 확인해 `invalid_client`(400) 응답임을
  근거로 IP 차단이 아니라 CI에 `TOSS_CLIENT_ID`/`SECRET` 자체가 없어서
  생기는 정상적인(그리고 무해한) 노이즈임을 규명
- 사용자가 시크릿 추가를 검토하길래, 실제 자격증명을 CI에 넣으면
  토스 토큰이 계정당 1개만 유효해(재발급 시 이전 토큰 즉시 무효화)
  push마다 도는 CI가 로컬/운영이 쓰던 토큰을 예고 없이 무효화시킬 수
  있다는 위험을 짚고 반대, 대신 `WatchlistControllerTest`에
  `TossApiClient`를 `@MockBean`으로 격리하는 쪽을 제안해 승인받고 구현

**변경 파일**
- `.gitignore`, `backend/gradle/wrapper/gradle-wrapper.jar`(최초 커밋)
- `backend/api/src/test/resources/application-test.yml`(신규) - 테스트
  전용 JWT 시크릿
- `frontend/src/components/chart/{IndicatorControls,CandleChart}.tsx`,
  `frontend/src/utils/indicators.ts`(신규)
- `backend/core/.../infra/oauth/OAuthProperties.java` 외 OAuth
  리팩터링 완료 관련 8개 파일(`.env.example`, `application.yml`,
  `docs/DEPLOYMENT.md` 등)
- `backend/api/src/test/java/.../WatchlistControllerTest.java` -
  `TossApiClient` mock 격리

**결정 사항**
- 실제 Toss 시크릿을 CI에 넣는 대신 mock 격리를 택함 - 토큰 단일
  유효성 제약이 만드는 실서비스 영향 리스크가 로그 노이즈 제거보다
  훨씬 크다고 판단
- 이번 세션 중 발견한, 내가 만들지 않은 우연한 미커밋 변경(프론트
  3파일, OAuth 완료 커밋)은 전부 "발견 → 사용자에게 확인 → 승인받고
  진행" 순서를 지켰음. 특히 OAuth 필드 제거는 실제 호출부를 추적해
  죽은 필드임을 코드로 직접 확인한 뒤에만 커밋
- 로컬 Testcontainers가 이 머신의 최신 Docker Desktop과 버전
  비호환이라(무관한 기존 테스트도 동일하게 실패) `WatchlistControllerTest`
  수정은 컴파일 검증 + 실제 CI 실행 결과로 검증. 같은 시기 다른
  세션도 독립적으로 동일 문제를 발견해 기록해둔 것을 확인(중복 조사
  아님)
  - **(2026-07-13 후속)** 이 세션의 "버전 비호환" 추정이 실제로 맞았음이
    확인됨(2026-07-13 세션에서 실측) - Docker Desktop Engine API의
    `MinAPIVersion`이 올라가면서 Testcontainers 1.20.4가 번들한
    docker-java 클라이언트가 그보다 낮은 버전으로 협상을 시도해 거부당한
    것. `backend/build.gradle`의 `testcontainers-bom`을 1.21.4로 올려
    해결. 상세는 `docs/DEVELOPMENT.md` §1 및 2026-07-13 작업 기록 참고

**다음 작업**
- 없음(이 세션 범위 내). 로컬 Testcontainers Docker 호환 문제는 앞선
  작업기록 항목에 이미 남아있어 중복 기록하지 않음

</details>

<details>
<summary>2026-07-13 - 로컬 Testcontainers "Could not find a valid Docker environment" 근본 원인 규명 + 해결</summary>

**변경 사항**
- 이전 두 세션(2026-07-11 OAuth 세션, 2026-07-12 급등락 랭킹 세션)이
  각각 "버전 비호환" 추정 / "원인 불명 환경 문제"로만 기록하고 넘어갔던
  이슈를 실제로 진단: `docker`/`docker compose` CLI는 정상 동작하는데
  Testcontainers 기반 통합 테스트(`ApiTestSupport` 하위 전체)만
  클래스 로딩 시점에 "Could not find a valid Docker environment"로
  실패하는 원인을 추적
- **1차 진단(오판)**: `docker context ls`로 활성 컨텍스트가
  `desktop-linux`(소켓 `~/.docker/run/docker.sock`)임을 확인했는데,
  머신 전역 `~/.testcontainers.properties`에 2023-11-22자로 박제된
  `docker.client.strategy=UnixSocketClientProviderStrategy` 핀이 있길래
  이게 원인이라 판단해 백업 후 삭제. 그런데 **삭제 후 재실행해도 동일하게
  재현**됨 - 1차 진단이 틀렸다는 뜻
- **2차 진단(실제 원인)**: `--info` 옵션으로 Testcontainers가 시도한
  전략들의 실패 사유를 직접 확인하니 `UnixSocketClientProviderStrategy`,
  `DockerDesktopClientProviderStrategy` 둘 다 동일하게
  `BadRequestException (Status 400: ...)`. `curl`로 소켓에 직접
  질의해 재현: `~/.docker/run/docker.sock`은 버전 없는 `/info`엔 200을
  주지만, `/v1.24/info`·`/v1.32/info`처럼 낮은 API 버전을 명시하면 400
  (`/v1.40/info`부터 200). `docker version`으로 확인한 서버의
  `MinAPIVersion`이 정확히 `1.40` - 즉 Testcontainers **1.20.4**가
  번들한 docker-java 클라이언트가 전략 탐지 시 그보다 낮은 API 버전으로
  하드코딩된 핑을 날려 최신 Docker Desktop 엔진에 거부당하는 게 진짜
  원인이었음. `DOCKER_API_VERSION` 환경변수로 강제 협상도 시도했으나
  효과 없음(전략 탐지 로직 자체가 버전을 하드코딩해 우회 불가)
- **해결**: `backend/build.gradle`의 `testcontainers-bom`을 1.20.4 →
  **1.21.4**로 업그레이드. `MarketControllerTest`/`PriceControllerTest`
  둘 다 통과 확인 후, `./gradlew :api:test :core:test` 전체 스위트를
  돌려 회귀 없음까지 확인
- 리포지토리 안에는 `.testcontainers.properties`나 `DOCKER_HOST`/
  `TESTCONTAINERS_*` 참조가 전혀 없음을 확인(Explore로 전체 검색) -
  진짜 원인(라이브러리-엔진 버전 비호환)은 코드 쪽 문제였고, 머신 전역
  핀 파일은 이번 실패와는 무관한 별개의(하지만 그 자체로는 유효한) 낡은
  설정이었음
- `docs/DEVELOPMENT.md` §1과 `CLAUDE.md` §10, 그리고 2026-07-11/
  2026-07-12 작업 기록의 관련 문구를 전부 이 정확한 결론으로 갱신 -
  1차 오판을 그대로 문서에 남겼다가 사용자에게 확인받아 정정한 과정까지
  함께 기록(같은 실수 반복 방지)

**변경 파일**
- `backend/build.gradle` — `testcontainers-bom` 1.20.4 → 1.21.4
- `~/.testcontainers.properties` — 삭제(백업 `~/.testcontainers.properties.bak`
  보관). *리포 밖 머신 설정, 결과적으로 이번 실패의 원인은 아니었지만
  낡은 설정이라 정리 겸 유지*
- `docs/DEVELOPMENT.md` §1 — "Testcontainers가 ... 실패" 트러블슈팅
  소절(정확한 원인/진단/해결 절차로 재작성)
- `CLAUDE.md` §10, 2026-07-11/2026-07-12 작업 기록 — 근본 원인 포인터
  정정

**결정 사항**
- 1차 진단이 틀렸음을 확인한 즉시 사용자에게 알리고, `build.gradle`
  의존성 버전 변경(원래 승인 범위인 "로컬 설정+문서화"를 벗어나는 실제
  코드 변경)에 대해 별도로 승인을 받은 뒤 진행 - 승인된 계획의 범위를
  임의로 넘기지 않기 위함
- `~/.testcontainers.properties`는 원인이 아니었다는 게 밝혀진 뒤에도
  삭제 상태를 되돌리지 않음 - 낡은 설정 자체는 여전히 무의미하고,
  삭제해도 부작용이 없으며 자동 감지 쪽이 더 유연함
- 버전을 최신 메이저(2.0.x)가 아니라 같은 1.x 라인의 1.21.4로 올림 -
  이번 문제(API 버전 협상)를 고치는 데는 충분했고, 메이저 업그레이드는
  API 변경 여부를 더 넓게 검토해야 해 이번 스코프를 벗어난다고 판단

**검증**
- `./gradlew :api:test --tests 'com.quantlab.market.controller.
  MarketControllerTest' --tests 'com.quantlab.price.controller.
  PriceControllerTest'` - 11개 전부 통과(수정 전엔 11개 전부 실패)
- `./gradlew :api:test :core:test` - 전체 스위트 회귀 없이 통과

**다음 작업**
- 없음(이 세션 범위 내)

</details>

<details>
<summary>2026-07-13 - 전종목 랭킹 스케줄러 청크 딜레이 추가 (동반 429 방지)</summary>

**변경 사항**
- `MarketRankingScheduler`가 청크(200종목씩, 스윕당 최대 14개)를 딜레이
  없이 연속 호출해, 스윕 하나가 순식간에 토스 `MARKET_DATA` 그룹의
  초당 토큰 버킷(스펙 예시 10건/초)을 넘겨버리는 것을 실제 운영 로그로
  확인. 같은 그룹을 공유하는 `PriceBroadcastScheduler`(관심종목 실시간
  시세, 3초 주기) 요청까지 동반 429를 유발할 수 있는 문제였음
- 청크 사이에 150ms 딜레이(`TOSS_API_DELAY_MS`)를 추가해 완화. 인터럽트
  발생 시엔 스윕을 안전하게 중단(다음 5초 틱에 재시도)

**변경 파일**
- `backend/core/.../market/scheduler/MarketRankingScheduler.java` - 청크
  간 `Thread.sleep(150)` 추가

**검증**
- `:core:test` 전체 스위트(105개) 통과 - 기존 `MarketRankingSchedulerTest`
  회귀 없음

</details>

<details>
<summary>2026-07-13 - 토스 API 토큰 401(invalid-token) 감지 시 캐시 무효화 + 재시도</summary>

**변경 사항**
- 백엔드 재기동 직후 `/api/market/indices` 등 토스 API 호출이 전부
  401(`invalid-token`)로 반복 실패하는 것을 발견. Redis에 캐시된 토큰
  TTL은 아직 21시간 넘게 남아있었는데도 토스 서버가 거부 - 토스 API는
  계정당 토큰 1개만 유효해(재발급 시 이전 토큰 즉시 무효화, §4 참고)
  다른 프로세스가 재발급받으면서 이 캐시가 TTL과 무관하게 조용히 죽은
  상태였던 것으로 추정. `TossTokenManager.getAccessToken()`이 Redis
  TTL만 믿고 캐시를 그대로 반환할 뿐, 401을 캐시 무효화 신호로 인식해
  재발급하는 경로가 전혀 없었던 게 근본 원인
- 즉시 조치로 Redis에 캐시된 죽은 토큰을 삭제해 재기동 후 정상화 확인
- 근본 수정: `TossTokenManager.invalidateToken()` 신규 추가(Redis 키
  삭제), `TossApiClient`에 `withTokenRetry` 헬퍼를 도입해 401
  (`HttpClientErrorException.Unauthorized`)을 받으면 캐시를 지우고 새
  토큰으로 1회만 재시도(그래도 실패하면 그대로 전파, 무한루프 방지).
  429(Rate Limit) 경로는 기존 그대로 유지하고 토큰 무효화를 트리거하지
  않도록 구분

**변경 파일**
- `backend/core/.../infra/toss/TossTokenManager.java` - `invalidateToken()` 추가
- `backend/core/.../infra/toss/TossApiClient.java` - `withTokenRetry` 헬퍼로
  4개 메서드(`getDailyCandles`/`getCurrentPrices`/`getExchangeRate`/
  `getMarketCalendar`) 전부 401 감지 시 1회 재시도하도록 통일
- `backend/core/src/test/java/com/quantlab/infra/toss/{TossApiClientTest,
  TossTokenManagerTest}.java`(신규) - 401 재시도 성공/재시도도 실패/429는
  무효화 안 함 3케이스 + 토큰 캐시 히트/미스/invalidateToken 3케이스

**결정 사항**
- 즉시 조치(Redis 캐시 삭제)와 근본 수정을 분리해 순서대로 진행 -
  캐시 삭제만으로도 당장 정상화되지만, 근본 원인(다른 프로세스의
  재발급이 언제든 재발할 수 있는 구조)을 그대로 두면 같은 장애가
  반복될 것이라 판단해 사용자 승인 하에 코드 수정까지 진행
- 401 감지를 여러 클라이언트가 공유하는 `ExternalApiInvoker`가 아니라
  `TossApiClient` 자체에 국한 - 공용 유틸을 건드리면 KIND/Upbit/퀀트엔진
  클라이언트 등 무관한 호출부까지 영향 범위가 넓어져, 이번 문제(토스
  토큰 단일 유효성)에 한정된 최소 침습 변경을 택함

**검증**
- 신규 유닛 테스트 6개 전부 통과, `:core:test`/`:api:test` 전체
  스위트(128개) 회귀 없이 통과
- 실제 `bootRun`으로 재기동 후 `curl /api/market/indices` 200 정상
  응답, 로그에 에러 없음 확인

</details>

<details>
<summary>2026-07-16 - Toss MARKET_DATA 레이트리밋 아키텍처 재설계 + 스케줄러/DTO 네이밍 정리</summary>

**변경 사항**
- **(핵심) 전종목 랭킹과 관심종목 실시간 시세가 각각 독립적으로 Toss를
  호출해 같은 종목 가격을 중복 조회하던 구조를, 한 스케줄러만 Toss를
  부르는 단일 파이프라인으로 재설계.** 발단은 실서버 로그에 뜬
  "토스증권 API 요청 한도를 초과했습니다" WARN - 원인은 전종목(~2,700개,
  14청크) 스윕을 딜레이 없이 연속 호출해 초당 토큰 버킷(스펙 예시
  10건/초)을 순식간에 넘긴 것이었음(1차 조치: 청크 사이 150ms 딜레이,
  커밋 `1fe940f`). 이어서 "관심종목 스케줄러도 Toss를 부르는데 두
  스케줄러가 중복 호출 아니냐"는 질문을 계기로 구조 자체를 재설계:
  이제 `MarketPriceSweepScheduler`(구 `MarketRankingScheduler`)가
  전종목 시세를 Redis(`PriceCacheStore`)에 전부 적재하는 유일한
  조회원이 되고, `WatchlistPriceRelayScheduler`(구
  `PriceBroadcastScheduler`)는 Toss를 전혀 호출하지 않고 그 Redis
  스냅샷을 관심종목만 골라 STOMP로 중계만 함. 청크 딜레이는
  120ms(PriceBroadcastScheduler 몫 + 네트워크 최악의 경우 0ms를
  가정해도 초당 10건 한도에 여유가 남도록 계산), 스케줄러 자체
  주기는 100ms로 낮춤 - `@Scheduled(fixedDelay=...)`는 이전 실행
  종료 후 대기이므로 겹칠 위험이 없고, 실제 페이싱은 청크 내부
  딜레이가 전담하기 때문에 바깥 주기를 낮추는 것 자체는 안전하다는
  점을 사용자와 함께 계산으로 확인
- 재설계 후 통합 테스트를 실제로 돌려보다가 `MarketCalendarCache`의
  null 체크 누락(기존부터 있던 버그, 스케줄러가 100ms로 빨라지면서
  테스트 컨텍스트 기동 직후 처음으로 실제 노출됨)을 발견해 방어 코드
  + 테스트 추가. 조사 결과 `ExternalApiInvoker`가 실제 HTTP 실패는
  이미 예외로 감싸주므로 이 null 케이스 자체는 프로덕션에서는 발생
  불가능한 테스트 전용 아티팩트였음(다만 진짜 Toss 장애 시 재시도에
  백오프가 없다는 점은 별개 리스크로 남겨둠 - 사용자가 속도를
  우선하기로 결정)
- **스케줄러/DTO 네이밍 정리**: 역할이 바뀐 김에 이름도 정리.
  `MarketRankingScheduler`→`MarketPriceSweepScheduler`(이제 랭킹은
  부산물이고 전종목 스윕이 본질), `PriceBroadcastScheduler`→
  `WatchlistPriceRelayScheduler`(직접 조회가 아니라 캐시 중계만
  한다는 걸 명시), `PriceBroadcastMessage`→`PriceSnapshot`(브로드캐스트
  전용 메시지가 아니라 전종목 캐시 값 본체가 됐으므로). 이 과정에서
  나온 대안들(DTO를 `PriceCache`로, 캐시 매니저들을 `*Service`로)은
  둘 다 기각 - `PriceCache`는 기존 `PriceCacheStore`와, `*Service`는
  이미 그 캐시들을 조합해 쓰는 `MarketIndexService`/`MarketRankingService`와
  실제로 이름이 충돌해 채택 불가함을 코드로 직접 확인 후 근거로 제시

**변경 파일**
- `backend/core/.../market/scheduler/MarketPriceSweepScheduler.java`(구
  `MarketRankingScheduler`) - 전종목 조회 + Redis 적재 + 랭킹 계산
- `backend/core/.../price/scheduler/WatchlistPriceRelayScheduler.java`(구
  `PriceBroadcastScheduler`) - Toss 의존성 전부 제거, Redis 읽기 전용으로 축소
- `backend/core/.../price/dto/response/PriceSnapshot.java`(구
  `PriceBroadcastMessage`) + 참조 9개 파일(`PriceCacheStore`,
  `PriceMapper`, `StockPriceService` 등)
- `backend/core/.../price/cache/MarketCalendarCache.java` - null 응답 방어
- `backend/api/src/main/resources/application.yml` - `market-ranking.poll-interval-ms`
  5000→100
- 테스트: `MarketPriceSweepSchedulerTest`, `WatchlistPriceRelaySchedulerTest`,
  `MarketCalendarCacheTest`(null 케이스 추가) 등
- `docs/ROADMAP.md`, `CLAUDE.md` §6/§10 - 새 이름/새 아키텍처로 갱신

**결정 사항**
- 레이트리밋 여유 계산은 항상 "네트워크 지연 0ms"를 가정 - 실측
  네트워크 왕복시간이 주는 여유를 안전마진으로 잡으면, 애초에 이번
  429를 유발했던 "네트워크가 알아서 페이싱해줄 것"이라는 가정과 같은
  함정이라 판단해 배제
- `PriceCacheStore`, `PriceCacheStore`가 쓰는 read-through 캐시 패턴
  자체는 이번 리네이밍 대상에서 제외(사용자가 유지 결정) - "Cache"
  접미사가 이 코드베이스에서는 TTL 매니저 클래스를 가리키는 일관된
  관례임을 다른 6개 클래스로 확인했기 때문

**검증**
- `:core:test`, `:api:test`(통합 테스트 `PriceControllerTest`/
  `WatchlistControllerTest`/`MarketControllerTest` 포함) 전체 스위트
  재설계 전/후, 리네이밍 전/후 각각 재실행해 전부 통과 확인

**다음 작업**
- 진짜 Toss 장애(예외) 시 `MarketCalendarCache`의 재시도에 백오프가
  없는 점은 알고 있는 채로 남겨둠(사용자가 100ms 유지를 선택) - 재발
  시 백오프 추가 검토
- 로그 관리 시스템(Loki+Grafana vs ELK)은 이번 세션에서 논의만 하고
  착수 전 단계에서 레이트리밋 문제로 우선순위가 밀림 - 다음 세션에서
  재논의 필요

</details>

<details>
<summary>2026-07-16 - 관측성 스택 신설(Prometheus/Grafana/Alertmanager, Phase 1: 메트릭+대시보드+알림)</summary>

**변경 사항**
- 지금까지 QuantLab의 관측성은 호스트 레벨(얕은 `/api/health`, cron이
  CloudWatch로 보내는 헬스/메모리/디스크 3개 지표)뿐이었고 **앱 내부는
  완전히 깜깜했음** - Actuator/Micrometer가 전무해 JVM 상태, HTTP
  지연/에러율은 물론, 바로 하루 전(2026-07-15/16) 재설계한 Toss
  `MARKET_DATA` 429 레이트리밋이 지금 실제로 얼마나 발생하는지조차 숫자로
  볼 수단이 없었음. "실제 운영 가능 + 포트폴리오 어필"을 목표로 현업
  표준 self-host 관측성 스택(Prometheus+Grafana+Alertmanager, PLG 계열의
  메트릭 축)을 Phase 1 범위(메트릭+대시보드+알림까지)로 구축
  - ELK(Elasticsearch/Kibana)와 Loki 중 로그 스택을 저울질하는 논의를
    거쳐(이번 Phase 1엔 로그 자체가 범위 밖이라 결론은 Phase 2로 유보)
    Loki 쪽으로 기울었음 - 메트릭 백본이 Prometheus로 고정되면 Grafana
    단일 UI로 통합되는 Loki가 자연스럽고, ELK/OpenSearch는 t3.small에
    올리기엔 무겁고(JVM 기반, 최소 수백MB~2GB 힙 권장) AWS 매니지드로도
    무료 티어가 사실상 없어 이 프로젝트 규모엔 과함
  - 로그(Loki)·트레이스(Tempo)는 Phase 2/3로 명시적으로 미룸 - 사용자가
    "메트릭 우선(단계적)"으로 확정, 이번 세션은 Phase 1만
- **앱 계측**: `backend/api`에 `spring-boot-starter-actuator` +
  `micrometer-registry-prometheus`, `backend/core`에 `micrometer-core`
  추가. `/actuator/prometheus`만 노출(그 외 엔드포인트는 닫음),
  `SecurityConfig`에 `/actuator/**` permitAll 추가 - nginx가 이 경로를
  프록시하지 않아(`frontend/nginx.conf`) 외부에서는 애초에 도달 불가,
  Prometheus만 docker 내부망에서 스크랩
  - 이 프로젝트만의 비즈니스 지표를 처음부터 계측(범용 JVM/HTTP 지표만
    붙이고 끝내지 않음): `TossApiClient`에 endpoint/outcome 태그가 붙은
    호출 Timer+Counter(429/401 감지가 핵심 - `TossRateLimitSpike` 알림의
    근거), `MarketPriceSweepScheduler`에 전종목 스윕 소요시간 Timer +
    청크 스킵 Counter, `PythonEngineClient`/`ScoreService`에 퀀트 엔진
    호출 Timer/실패 Counter + 응답 누락 종목 Counter
  - `ExternalApiInvoker`(static 유틸)는 `MeterRegistry`를 생성자로 주입받을
    수 없어 계측 지점을 호출부 빈(TossApiClient 등) 각각으로 선택 -
    공용 유틸을 억지로 인스턴스화하기보다 호출부에서 Timer.Sample로
    감싸는 편이 침습이 적음
  - `http.server.requests`에만 퍼센타일 히스토그램을 켜서(`management.
    metrics.distribution.percentiles-histogram`) Grafana에서 p95/p99
    지연 패널이 가능하게 함 - 커스텀 Timer(Toss/퀀트엔진)는 카디널리티
    낮고 평균 지연이면 충분해 히스토그램은 켜지 않음(전역으로 켜면
    태그 조합만큼 시계열이 폭증)
  - quant-engine(FastAPI)에는 `prometheus-fastapi-instrumentator`로
    `/metrics` 노출 - 요청량이 배치 위주라 커스텀 지표 없이 기본 HTTP
    계측만 적용
- **모니터링 스택**: `docker-compose.monitoring.yml` 신규(기존
  `docker-compose.cloudwatch.yml` 분리 패턴을 그대로 미러링) - Prometheus,
  Alertmanager, node-exporter, cAdvisor, Grafana(대시보드 3종: JVM,
  Spring Boot HTTP, QuantLab 비즈니스 지표 - `monitoring/grafana/`에
  프로비저닝 JSON으로 커밋해 최초 기동 시 자동 로드). cloudwatch
  오버레이와의 결정적 차이: **AWS 자격증명이 필요 없어 로컬에서도 실제
  `up`까지 기동·검증 가능**(로컬 Grafana 3000/프론트 3001과 안 겹치게
  Grafana만 3002로 매핑)
  - 알림 규칙은 `monitoring/prometheus/rules/alerts.yml`에 코드로:
    BackendDown/QuantEngineDown/HighHttp5xxRate/TossRateLimitSpike/
    QuantEngineFailureRate/JvmHeapHigh/HostMemoryHigh/HostDiskHigh 8종
  - Alertmanager → Slack `#quantlab-alerts`(기존 사용자 피드백용 Incoming
    Webhook과 별개 채널로 분리 등록 권장, `.env.prod.example`의
    `SLACK_ALERT_WEBHOOK_URL`)
- **버그 발견 + 수정(로컬 실제 기동 검증 중)**: Alertmanager 설정 파일은
  `${VAR}` 환경변수 치환을 지원하지 않는다. 이를 우회하려고 entrypoint에서
  sed로 치환하는 방식을 짰는데, 최초 구현(`${SLACK_ALERT_WEBHOOK_URL}`을
  치환 대상 플레이스홀더로 사용)이 실제로는 **docker compose 자신의
  `${VAR}` 보간과 충돌**해 sed 명령의 검색 패턴 자체가 실제 시크릿 값으로
  미리 치환돼버리는 버그를 발견 - `docker compose config`로 렌더링 결과를
  직접 눈으로 확인하지 않았다면 그냥 "동작하는 줄 알고" 넘어갔을 문제.
  실제로 컨테이너를 띄워보니 Alertmanager가 `"unsupported scheme"` 에러로
  기동 실패. 플레이스홀더를 `$` 문자가 아예 없는
  `__SLACK_ALERT_WEBHOOK_URL__`로 바꿔 compose 보간 대상 자체가 되지
  않게 해 해결(재기동 후 로그에 `"Completed loading of configuration
  file"` 확인)

**변경 파일**
- `backend/api/build.gradle`, `backend/core/build.gradle` - actuator/
  micrometer 의존성
- `backend/api/src/main/resources/application.yml` - `management.*` 블록
- `backend/api/.../auth/config/SecurityConfig.java` - `/actuator/**` permitAll
- `backend/core/.../infra/toss/TossApiClient.java`,
  `market/scheduler/MarketPriceSweepScheduler.java`,
  `infra/python/PythonEngineClient.java`, `score/service/ScoreService.java`
  - 커스텀 Micrometer 계측 + 관련 테스트(`TossApiClientTest`,
  `MarketPriceSweepSchedulerTest`, `ScoreServiceTest`)에 `SimpleMeterRegistry`
  주입 추가
- `quant-engine/main.py`, `quant-engine/requirements.txt` - `/metrics` 노출
- `docker-compose.monitoring.yml`(신규), `.env.prod.example` -
  `GRAFANA_ADMIN_PASSWORD`/`SLACK_ALERT_WEBHOOK_URL` 추가
- `monitoring/`(신규 디렉터리) - `prometheus/{prometheus.yml,rules/alerts.yml}`,
  `alertmanager/alertmanager.yml`, `grafana/provisioning/{datasources,
  dashboards}/*.yml`, `grafana/dashboards/*.json`(JVM/Spring HTTP/비즈니스)
- `docs/DEPLOYMENT.md` §13(신규), `docs/DEVELOPMENT.md`(모니터링 로컬
  기동 절), `CLAUDE.md` §11 - 문서

**결정 사항**
- Phase 1을 "메트릭+대시보드+알림까지 완결"로 정의하고 로그/트레이스는
  의도적으로 다음 세션으로 미룸 - 한 세션에 3-pillar를 전부 넣기보다
  단계마다 실제로 동작하는 상태를 만들어두는 편을 택함
- 호스트 메모리/디스크 알림이 기존 CloudWatch SNS 알람과 개념이
  겹치지만 굳이 걷어내지 않음 - Slack 단일 창으로 보고 싶을 때와
  AWS 콘솔로 보고 싶을 때 둘 다 남겨두는 것이 비용 대비 해가 없다고
  판단(중복 알림이 거슬리면 둘 중 하나만 유지하면 됨)
- Grafana/Prometheus/Alertmanager UI는 EC2에서 외부 노출하지 않고 SSH
  터널로만 접근하도록 문서화 - 별도 인증 계층을 새로 설계하기보다
  기존에 이미 확립된 "22번 포트 SSH 접근" 신뢰 경계를 재사용하는 편이
  이번 스코프에 맞는 최소 구현

**검증**
- `docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.yml
  --env-file .env.prod config` - 머지 성공(exit 0)
- Alertmanager 단독 기동 + 실제 웹훅 형식 값으로 재검증 -
  `"Completed loading of configuration file"` 확인(위 버그 수정 후)
- Grafana 대시보드 3종 JSON `python3 -m json.tool`로 문법 검증 통과

**다음 작업**
- Phase 2(로그, Loki+Promtail/Alloy, JSON 구조화 로깅+MDC traceId)
- Phase 3(트레이스, Tempo+micrometer-tracing, Spring↔quant-engine 요청
  상관관계)
- 실제 EC2에서의 전체 스택 기동 검증(리소스 사용량 실측 포함)은 사용자가
  EC2 프로비저닝 후 직접 진행 필요

</details>

<details>
<summary>2026-07-18 - 현재가 조회 429 반복 재발 원인 진단 및 제거</summary>

**변경 사항**
- 사용자가 "백엔드 돌린 지 얼마 안 돼서 429(rate-limit-exceeded)가 반복
  발생한다"고 리포트. 이전 작업기록(두 스케줄러의 Toss 중복 호출을
  구조적으로 제거한 리팩터링)에도 불구하고, `StockPriceService.
  getCurrentPrice()`(개별 종목 현재가 조회, Redis 캐시 미스 시
  fallback)가 여전히 `TossApiClient`를 직접·무페이싱으로 호출하는
  경로로 남아있던 걸 발견 - 스케줄러 재설계 범위 밖에 있던 서비스
  계층 코드였음
- 이 경로는 장이 닫혀 있으면 `MarketPriceSweepScheduler`가 아예 안 돌아
  Redis가 영원히 안 채워지므로 100% 확률로 발동했고, 프론트가 관심종목/
  최근 본 종목을 `useStockPricesQuery`로 5초마다 동시 폴링(종목당 1개
  요청)하는 구조라 매 폴링마다 N개의 무페이싱 Toss 요청이 한꺼번에
  몰려 토큰 버킷을 넘김. 실제로 백엔드를 재현용으로 띄워 로그를 관찰해
  확인
- Toss 재호출 대신 DB에 이미 있는 마지막 확정 종가
  (`DailyPriceRepository.findTopByStockCodeOrderByTradeDateDesc`)로
  응답하도록 수정 - `MarketPriceSweepScheduler`를 유일한 Toss 가격
  조회원으로 실제 일원화. 등락률은 기존처럼 `PreviousCloseCache`로 계산

**변경 파일**
- `backend/core/.../price/service/StockPriceService.java` - Toss 직접
  호출 제거, DB 폴백으로 대체
- `backend/core/.../price/dto/mapper/PriceMapper.java` - `DailyPrice`
  기반 매핑 오버로드 추가
- `backend/core/.../price/service/StockPriceServiceTest.java`,
  `backend/core/.../price/dto/mapper/PriceMapperTest.java`,
  `backend/api/.../price/controller/PriceControllerTest.java` - Toss
  목킹 대신 DB 픽스처 기반으로 테스트 재작성

**결정 사항**
- 이 저장소는 여러 Claude Code 세션이 같은 워킹 디렉터리를 동시에
  편집하는 구조로 운영되고 있다는 걸 이번 세션에서 실측으로 확인
  (`CurrentPriceResponse`의 `changeRate` 필드가 작업 도중 다른 세션에
  의해 실시간으로 추가/제거되는 걸 직접 목격). 커밋 직전 반드시
  `git diff`로 대상 파일이 여전히 예상한 모양인지 재확인한 뒤 좁은
  범위로만 `git add`하는 방식으로 대응 - 다음 세션도 이 저장소에서
  작업할 땐 동일한 위험을 염두에 둘 것
- 이전 429 완화 작업 이력이 있었지만, 이번 재발은 그 결론이 틀린 게
  아니라 그 리팩터링의 범위(스케줄러 간 중복)가 서비스 계층의 별도
  fallback 경로까지는 커버하지 못했던 사각지대였음 - 유사 패턴이 더
  있는지는 이번 세션에서 전수조사하지 않음

**다음 작업**
- 다른 서비스에 유사한 "캐시 미스 시 외부 API 직접 호출" fallback
  패턴이 더 있는지 점검 필요(이번엔 사용자 리포트로 우연히 발견)
- 저장소 동시 편집 위험은 구조적으로 남아있음 - 여러 세션을 병행
  운영할 계획이면 세션별 git worktree 분리를 고려할 것

**추가**: 같은 세션에서 이어서 사용자가 "기동하자마자 FRED 요청이
RST_STREAM으로 11분간 66회 반복 실패한다"고 리포트. curl로
`fred.stlouisfed.org`에 HTTP/1.1(200 정상)·HTTP/2(즉시 스트림 리셋)를
직접 재현해 원인 확정 - 이 엔드포인트 앞단이 HTTP/2 요청을 거부함.
Python 엔진 h2c 버그(Phase 3)와 동일한 해법(JDK HttpClient를 HTTP/1.1로
고정)을 `FredApiConfig`에 적용, 커밋 `a9c9448`. `infra/fred` 패키지
나머지 파일(Client/Properties/exception)은 다른 세션의 미완성
작업이라 손대지 않고 이 설정 파일 하나만 분리 커밋함.

</details>

<details>
<summary>2026-07-19 - 스코어링 백테스팅 인프라 구축 (Phase A~D: KIS 연동 + 국내/해외 유니버스 백필 + 스코어 로직 개선)</summary>

**배경**: `quant-engine/calculator/scorer.py`의 v2 스코어링(추세추종/평균회귀
두 축)은 코드에 "TODO: 초기값, 백테스트 후 튜닝 필요"로 명시된 임계값이
많았으나 백테스트 인프라 자체가 없었다. 별도 대화(claude-fable-5)에 스코어링
방식과 백테스팅 계획을 설명해 외부 방법론 리뷰를 받고(`quant-engine/docs/
BACKTEST_METHODOLOGY_REVIEW.md`, gitignore 처리), 그 권고를 실제 계획
(Phase A~G)에 반영해 이번 세션에서 A~D를 구현·라이브 검증했다.

**변경 사항**

1. **스코어 로직 선행 수정 (Phase D)** — 백테스트 전에 고쳐야 할 설계 결함들:
   - **거래량 배율 비대칭 버그 수정**: `_apply_volume_multiplier`가
     `score>50이면 곱하고 <50이면 나누는` 방식이라 같은 거래량 조건에서
     강세/약세가 다르게 증폭됐다(multiplier=1.3일 때 60점→78, 40점→30.8 -
     편차 기준 2.8배 vs 1.92배). Fable 리뷰가 지적한 항목을 실제 코드와
     대조해 확인 후 `dev = score - 50; adjusted = 50 + dev * multiplier`로
     대칭화, 50 경계 불연속도 함께 해소. 회귀 테스트로 대칭성 자체를 검증
     (`test_multiplier_amplifies_symmetric_deviation_from_50`).
   - **사분면(Quadrant) 라벨 도입**: 종합점수(두 축 평균)만으로는 "상승추세
     눌림목"과 "추세 연장·과열"이 비슷한 값으로 뭉개지는 문제(Fable 지적)를
     보완. 놀랍게도 `commentary.py`가 코멘트 템플릿 선택용으로 이미 동일한
     4분면 분류 로직(`_classify_quadrant`)을 내부적으로 갖고 있었음을
     발견 - 이를 `scorer.py`로 옮겨 `ScoreResult.quadrant`로 노출하고
     `commentary.py`는 그 값을 재사용하도록 리팩터(중복 분류 로직 제거,
     단일 소스화). Spring 쪽 계약(`Score` 엔티티, `ScoreMapper`,
     `ScoreResponse`, `ScoreBatchApiResponse`, 신규 `Quadrant` enum)까지
     전파.
   - Fable이 지적한 다른 항목(`ewm(adjust=False)`, RSI Wilder 방식, 수정주가
     요청)은 실제 코드 대조 결과 **이미 반영돼 있었음**을 확인해 손대지 않음.

2. **KIS(한국투자증권) 해외주식 연동 (Phase A)** — 토스는 국내 전용이라
   해외 유니버스는 별도 벤더가 필요. `infra/kis` 패키지 신규(토큰 관리 +
   401 감지 시 무효화·재시도 - 기존 `TossTokenManager` 패턴 재사용, 해외
   현재가/기간별시세 클라이언트). 실제 앱키로 라이브 검증(NAS/AAPL
   현재가·일별시세 둘 다 `rt_cd=0` 정상 확인, DTO 필드 최초 설계 그대로
   일치). 해외 종목마스터는 REST API가 아니라 zip 압축된 CP949 탭구분
   정적 파일(`https://.../{code}mst.cod.zip`)로 제공됨을 실제 다운로드로
   확인 후 파싱 구현(NASDAQ 3,921종목/NYSE 2,443종목 실제 등록 검증) -
   ETF는 종목구분 코드(2:주식/3:ETF)로 자동 구분됨을 확인해 별도 필터 불요.

3. **국내/해외 유니버스 2-pass 백필 (Phase C)** — 거래대금(종가×거래량)
   상위 500종목을 60일 스캔 → 랭킹 → 400일 심화 백필하는 2단계 구조.
   국내는 REIT를 이름 기반(포함 매칭 + 예외 2건: 메리츠금융지주/
   블리츠웨이엔터테인먼트)으로 제외, ETF는 종목마스터 소스(KIND)가
   원래 법인만 다뤄 자동 제외됨을 실측 확인. 해외는 미국 달러 소수점
   가격을 기존 `DailyPrice`(Long 컬럼, 원화 정수 전제)에 못 담아
   `OverseasDailyPrice`(Double 컬럼) 별도 엔티티로 분리.
   국내 500종목/해외(AAPL·MSFT 스모크테스트) 모두 라이브 백필 검증
   완료(국내: 실제 SK하이닉스/삼성전자 등 대형주가 랭킹 상위 일치).

4. **국내 지수 벤치마크 이력 (Phase B)** — 초과수익률 계산 기준선.
   네이버 금융 비공식 API가 `pageSize` 상한(60)만 있고 `page`를 늘리면
   그 이전 데이터로 끊김 없이 이어짐을 실제 호출로 확인, 페이지네이션
   오버로드 추가(기존 메서드는 안 건드림). KOSPI/KOSDAQ 각 420일 라이브
   백필 검증, 재실행 시 멱등성 확인.

**변경 파일** (신규 파일 다수 생략, 대표 항목만)
- `quant-engine/calculator/scorer.py` - 거래량 대칭화, 사분면 분류/노출
- `quant-engine/calculator/commentary.py` - 사분면 분류 로직 제거, scorer.py 값 재사용
- `quant-engine/schemas.py`, `main.py` - `quadrant` 필드 노출
- `backend/core/.../infra/kis/**` - KIS 클라이언트 신규(토큰/현재가/일별시세/마스터파일)
- `backend/core/.../score/domain/{Score,Quadrant}.java`, `dto/mapper/ScoreMapper.java` 등 - 사분면 계약 전파
- `backend/core/.../market/service/{BenchmarkIndexBackfillService,DomesticUniverseSelectionService,OverseasUniverseSelectionService}.java` - 유니버스 선정/백필
- `backend/core/.../price/domain/OverseasDailyPrice.java`, `price/service/OverseasDailyPriceBackfillService.java` - 해외 소수점 가격 별도 엔티티
- `backend/core/.../stock/service/OverseasStockMasterSyncService.java`, `stock/domain/MarketType.java` - 해외 종목마스터, NASDAQ/NYSE 추가(국내 전용 루프 보호)
- `backend/api/.../DevController.java`, `application.yml`, `.env.example` - 수동 트리거 엔드포인트 + KIS 설정(다른 세션의 동시 변경과 분리해 추가분만 반영)

**결정 사항**
- 백테스트 방법론은 백지에서 설계하지 않고 외부 리뷰(Fable)를 받아 검증 후
  실행 - 리뷰가 지적한 항목을 실제 코드와 하나하나 대조해 "이미 반영됨 vs
  진짜 고쳐야 함"을 구분한 뒤 진짜 버그(거래량 비대칭)만 수정. 근거 없이
  전부 뜯어고치지 않음.
- 해외 leg의 전체 6,364종목 라이브 실행은 이번 세션에서 보류(소규모
  스모크테스트만 검증) - 국내 500종목 백필에서만도 실행 환경(다른 세션의
  동시 백엔드 인스턴스와 메모리 경합)이 예측 불가능하게 프로세스를 죽여
  여러 라운드로 나눠 재개해야 했음. 전체 해외 스캔은 규모가 더 커 별도
  세션/시점에 진행하기로.
- 이 세션도 다른 세션들과 같은 워킹 디렉터리를 공유(피드/피드백/유저/
  업로드 등 무관한 대규모 변경이 동시에 쌓여 있었음). 커밋 전 `Score.java`/
  `ScoreMapper.java`/`application.yml`/`.env.example`/`DevController.java`/
  `.gitignore` 6개 파일은 다른 세션의 변경과 같은 파일에 섞여 있어, HEAD
  버전으로 되돌린 뒤 내 변경분만 재적용해 스테이징하고 작업트리는 다시
  전체 내용으로 복원하는 방식으로 정확히 분리해 커밋함.
- Phase B(벤치마크 백필)가 의존하는 `NaverFinanceApiClient`의 페이지네이션
  오버로드는 다른 세션이 만든 완전히 새 파일(아직 미커밋) 위에 추가한
  것이라, 그 파일 전체를 내 커밋에 포함시키면 다른 세션의 작업 전체를
  잘못 귀속시키게 된다 - 커밋하지 않고 남겨둠. 즉 이번 세션이 커밋한
  Phase B 코드는 그 커밋 히스토리만 체크아웃하면 컴파일이 안 되고, 다른
  세션이 `infra/naver` 패키지를 커밋해야 완전해진다(작업트리에는 이미
  합쳐진 상태로 있어 실제 동작·테스트는 전부 확인됨).

**다음 작업**
- Phase E: `compute_scores(df)` 순수함수화(라이브·백테스트 공용), Python
  `/backtest/score` 엔드포인트, 백테스트 결과 저장(Spring, `score_version` 태그)
- Phase F: 백테스트 프론트 페이지(사분면 배경 오버레이, 분위수 버킷 테이블,
  Rank IC, 디스클레이머)
- Phase G: 등급 컷오프 등 근거 기반 튜닝(백테스트 결과 나온 뒤)
- 해외 유니버스 전체 6,364종목 실제 스캔+랭킹+백필은 미실행 - 다음 세션에서
  국내와 동일한 라운드 방식으로 진행 필요
- 다른 세션의 미커밋 변경(feed/feedback/user/upload, 프론트엔드 대부분)은
  이번 세션 범위 밖 - 손대지 않음

</details>

<details>
<summary>2026-07-20 - 홈 랭킹 테이블 UX 다듬기 + 리퀴드 글래스 실험(적용→원복) + 로컬 검증 포트 격리 원칙 확립</summary>

**변경 사항**
- 검색 모달 결과 목록에 ↑↓ 방향키 탐색 + Enter로 하이라이트된 항목 선택 구현
- 애플 iOS 26 "리퀴드 글래스" 디자인 실험: 검색모달/관심그룹 모달 3종에
  블러 기반으로 먼저 적용 → 사용자 요청으로 SVG feDisplacementMap 기반
  "진짜 굴절" 버전으로 고도화(판단 프레임워크를 글로벌 CLAUDE.md에 문서화)
  → 안전한 정적 패널 7곳(ColorWidthChip, LoginModal, ProfileMenu,
  AddToWatchlistGroupPicker, FeedComposeModal, FeedbackModal,
  IndicatorSettingsModal)로 적용 범위 재조정 → **최종적으로 사용자가
  시각적 선호로 전체 원복 요청** → 코드에서 완전히 제거하고 방법론만
  문서로 남김
- 종목 상세 페이지 관심종목 하트를 이름 줄 안에서 헤더 섹션 최우측(스코어
  카드 옆)으로 재배치
- 홈 실시간 랭킹 테이블: "관심종목만" 필터를 스코어/거래대금/급상승/
  급하락 4탭 전부에 통일, 텍스트 버튼→하트 아이콘 온오프 토글로 교체하고
  실시간 기간 드롭다운 왼쪽으로 이동, 산업명이 길 때 표 레이아웃이
  밀리던 문제를 max-width 캡+말줄임+title 툴팁으로 수정
- 차트 지표 설정 색상/굵기 바텀시트의 "굵기"/"선종류"를 세로 스택에서
  좌우 2칼럼으로 병합
- 로컬 검증용 백엔드 포트를 8080에서 8081로 분리(사용자가 8080에 자신의
  라이브 세션을 띄워둘 수 있음) - `SERVER_PORT`/`VITE_API_BASE_URL`
  환경변수 오버라이드로 실제 동작 검증, 종료도 프로세스명 패턴(`pkill -f`)
  대신 포트 바인딩 조회(`lsof -ti:PORT`)로 전환 - 같은 이름의 다른 포트
  인스턴스를 실수로 같이 죽이는 것 방지

**변경 파일**
- 이번 세션 변경은 이미 다른 세션이 묶은 대형 커밋 2개(`5e43515`,
  `e97da74`)에 함께 포함되어 있음 - 이 세션이 직접 커밋한 것은 없음
  (`git log -S` 대조로 확인)
- 주요 파일: `frontend/src/pages/StockDetailPage.tsx`,
  `frontend/src/components/home/RankingTable.tsx`,
  `frontend/src/components/chart/ColorWidthChip.tsx`,
  `frontend/src/components/search/SearchOverlay.tsx`,
  `frontend/src/index.css`, 전역/프로젝트 `CLAUDE.md`

**결정 사항**
- 리퀴드 글래스는 "정적 크기 패널 vs 잦은 리사이즈 요소" 판단
  프레임워크로 적용 범위를 좁혔지만, 최종적으로는 사용자가 시각적
  선호로 전부 원복을 선택 - "안전한 후보"라는 판단이 "반드시 적용해야
  함"을 뜻하지는 않음을 재확인
- 로컬 개발 서버 포트를 사용자 기본값과 분리하는 원칙을 프론트
  (3001→3002)에 이어 백엔드(8080→8081)까지 확장 - 종료 명령도 프로세스명
  패턴에서 포트 기반 조회로 전환(다른 세션/사용자의 동일 프로세스명
  인스턴스를 실수로 같이 죽이는 위험 제거)

**다음 작업**
- 없음(이 세션 범위 내). 멀티세션 동시편집 리스크는 기존 CLAUDE.md에
  이미 기록됨(중복 기록 안 함)

</details>

<details>
<summary>2026-07-22 - 프리미엄 구독 결제 기능 신규 구현(토스페이먼츠, 사업자등록 전 - 테스트 키 전용)</summary>

**배경**
사업자등록이 아직 안 된 상태에서 프리미엄 구독 결제 기능을 미리
구현해달라는 요청. 실결제는 불가능하지만, 토스페이먼츠는 개발자센터
이메일 가입만으로 테스트 키를 즉시 발급하므로 테스트 키로 전체 플로우를
지금 구현해두고, 사업자등록 완료 후 `.env`의 키만 라이브 키로 교체하면
되도록 설계. 결제 방식은 자동결제(빌링키)만 지원하기로 확정 - 토스페이먼츠
빌링 API가 카드에만 적용되는 기술적 제약이 있어 결제수단은 카드로
고정되고(휴대폰·카카오페이·네이버페이 등은 애초에 빌링키 발급 대상이
아님), 대신 장기 플랜(6/12개월) 결제 시 카드사 할부(`cardInstallmentPlan`
파라미터, 자동결제 승인 API에도 지원됨을 문서로 확인)를 붙여 목돈 부담을
줄이는 쪽으로 방향을 잡았다.

**변경 사항**
- 백엔드: `subscription`(SubscriptionPlan/Subscription 도메인 - 사용자당
  row 1건, 갱신마다 상태 전이) + `payment`(청구 시도 이력) 신규 도메인
  패키지. 기존 `watchlist` 도메인 구조(entity/repository/service/dto/exception)
  를 그대로 따름
- `infra/tosspayments` 외부 클라이언트 신규(`infra/toss`의
  ExternalApiInvoker/에러코드 패턴 재사용, 시크릿키 Basic Auth라 토큰
  관리자는 불필요) - 빌링키 발급/청구, 웹훅 서명 검증(HMAC-SHA256,
  정확한 헤더명은 문서 접근 불가로 이번 세션에 확정 못함 - 실제 연동
  시 재확인 필요)
- 빌링키(카드 자동결제 권한 토큰) DB 컬럼은 AES-GCM으로 암호화
  (`BillingKeyConverter`) - 암호화 키 미설정 시(로컬 개발) 평문 통과,
  운영 배포 전 `SUBSCRIPTION_BILLING_KEY_ENCRYPTION_KEY` 필수
  (`openssl rand -base64 32`로 생성)
  - 트랜잭션 경계 이슈 발견: `PaymentService`에서 결제 승인(외부 API,
    트랜잭션 밖) 후 DB 저장을 같은 클래스의 `@Transactional` 메서드로
    self-invocation하면 Spring AOP 프록시를 안 거쳐 트랜잭션이 무시됨
    (Phase 3 `ScorePersistenceService` 분리 때와 동일한 원인) - 저장
    책임을 `SubscriptionService.activateOrResubscribe`로 분리해 해결
  - 자동 갱신 재시도(`SubscriptionRenewalScheduler`, 매일 04:00): 실패
    시 `nextBillingAt`을 +1일로 미뤄 재시도 예약, 3회 소진 시 PAST_DUE
    전환(재시도를 스케줄하지 않으면 exact-equality 쿼리 특성상 다음날
    조회에서 아예 안 잡혀 재시도가 안 되는 문제를 미리 발견해 반영)
- 요금제 3개월/6개월/12개월(월 기준가 7,900원, 할인 없이 시딩) -
  `SubscriptionPlanInitializer`(`StockMasterInitializer`와 동일한
  ApplicationRunner 시딩 패턴, Flyway 없이 ddl-auto=update만 쓰는
  프로젝트라 그대로 재사용)
- API: `GET /api/subscription/plans`, `GET /api/subscription/me`
  (customerKey 포함 - 프론트가 카드 등록 위젯을 열기 전에 필요),
  `POST /api/subscription/billing-key`(빌링키 발급+즉시 첫결제 한 번에),
  `POST /api/subscription/cancel`(자동갱신만 해제, 현재 주기 끝까지는
  이용 가능), `GET /api/subscription/payments`, `POST
  /api/webhooks/tosspayments`(서명 검증으로 인증 대체, SecurityConfig
  permitAll 추가)
- 프론트: `@tosspayments/tosspayments-sdk`(v2, 카드 자동결제 위젯) 도입,
  `/subscribe`(플랜+할부 선택, 카드 등록 위젯 호출) · `/subscribe/result`
  (successUrl/failUrl 콜백 - 위젯이 붙여주는 authKey/customerKey 쿼리
  파라미터로 빌링키 발급 API 호출) 신규 페이지, `MyInfoPage`에 구독
  상태/결제 이력 섹션 추가, `ProfileMenu`에 "구독 관리" 진입점 추가
- 테스트: `SubscriptionServiceTest`/`PaymentServiceTest`(Mockito 단위),
  `TossPaymentsApiClientTest`(MockRestServiceServer, 기존
  `TossApiClientTest` 패턴), `SubscriptionControllerTest`(통합,
  `@MockBean TossPaymentsApiClient`로 실제 토스 호출 격리) - 이 세션
  환경엔 Docker가 없어 통합 테스트는 컴파일만 확인, 실행은 로컬에서
  필요

**결정 사항**
- 프리미엄 혜택(관심종목 개수 제한 등 실제 기능 게이팅)은 이번 범위에서
  보류 - `Subscription.status`로 프리미엄 여부를 판별할 수 있는 구조만
  만들어두고, 어떤 기능을 잠글지는 별도 요청 때 진행하기로 함(사용자 확인)
- `User` 엔티티에 plan/tier 필드를 추가하지 않음 - 프리미엄 여부가
  `Subscription.status == ACTIVE`로 충분히 판별되므로 불필요한 컬럼 추가
  회피

**다음 작업**
- 사업자등록 완료 후 토스페이먼츠 라이브 키로 교체 + 웹훅 헤더명 실제
  연동 시 재확인
- 프리미엄 게이팅 대상 기능 결정 및 구현(별도 요청 시)
- Docker 있는 환경에서 `SubscriptionControllerTest` 통합 테스트 실행 확인

</details>
