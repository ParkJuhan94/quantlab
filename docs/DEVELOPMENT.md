# 개발 가이드

로컬에서 백엔드+프론트엔드를 함께 띄우는 법과, 실제 동작을 검증하는
방법론을 정리한다.

---

## 1. 로컬 개발 환경 실행

기본 실행 명령어(인프라/백엔드/Python 엔진/프론트엔드)는
[`CLAUDE.md` §11](../CLAUDE.md#11-자주-쓰는-명령어) 참고. 아래는 최초
1회만 필요한 준비 작업과, 그 명령어들만으로는 안 드러나는 주의사항이다.

- **quant-engine**: 최초 1회 `python3 -m venv venv && source venv/bin/activate
  && pip install -r requirements.txt`로 가상환경을 준비해야 하고, Spring과
  완전히 별도 프로세스라 `bootRun`을 띄워도 자동으로 같이 뜨지 않는다 -
  둘 다 떠 있어야 관심종목 등록 시 스코어 계산이 된다.
- **프론트엔드**: 최초 1회 `cp .env.example .env.local`로 환경변수 파일을
  준비해야 한다(`VITE_API_BASE_URL=http://localhost:8080` 정도면 충분).

quant-engine이 꺼져 있으면 관심종목 등록/배치 시 스코어 계산만 조용히
실패한다(`WatchlistService`가 스코어 계산을 별도 스레드 +
`SafeExecutor`로 감싸둬서 등록 API 자체는 성공 응답을 반환하고, 로그에
`ExternalApiException` / `ConnectException`만 남는다). 관심종목 등록은
됐는데 스코어가 안 보이면 제일 먼저 `curl localhost:8000/docs`로
quant-engine 생존 여부부터 확인할 것.

### 개발용 로그인 — 실제 OAuth 콘솔 등록 없이 인증 화면 검증하기

로그인 페이지의 "개발용 로그인" 버튼(개발 모드에서만 노출)은
`POST /dev/auth/token`을 호출해 실제 구글/카카오/네이버 콘솔 등록 없이
테스트 유저 JWT를 바로 발급받는다. 관심종목/대시보드/로그아웃 등 인증이
필요한 화면은 전부 이걸로 검증 가능하다. 백엔드가 `dev` 프로필로
떠 있어야 `/dev/**` 엔드포인트가 존재한다.

### 흔한 함정

- **작업 디렉터리 잔류**: bash 세션에서 `cd backend && ./gradlew ...`를
  실행하면 그 세션의 작업 디렉터리가 `backend/`로 남는다. 이어서
  `cd frontend`를 안 하고 `npm run dev`를 치면 `backend/`에서
  `package.json`을 못 찾아 에러난다.
- **Redis 캐시 오염**: `TestContainerSupport`는 MySQL만 Testcontainers로
  격리하고 **Redis는 격리하지 않는다**(로컬 실제 Redis를 그대로 씀).
  수동 검증 세션 전에 이전 세션이 남긴 시세 캐시를 지워두는 게 좋다:
  ```bash
  redis-cli -p 6381 KEYS "price:current:*" | xargs -r redis-cli -p 6381 DEL
  ```
- **Testcontainers가 "Could not find a valid Docker environment"로
  실패**: `docker` CLI는 정상 동작하는데 `./gradlew :api:test`의
  `MarketControllerTest`/`PriceControllerTest` 등 `ApiTestSupport` 하위
  통합 테스트만 클래스 로딩 시점에 이 에러로 전부 실패한다면, **최신
  Docker Desktop과 Testcontainers 라이브러리 버전 간 Docker Engine API
  버전 비호환**을 의심할 것(2026-07-13 세션에서 실측 규명 - 이미
  `backend/build.gradle`을 수정해뒀으므로 정상적으로는 이 문제를 다시
  만날 일이 없지만, Docker Desktop을 업데이트한 뒤 재발하면 아래
  절차로 확인).
  - **증상 재현/진단**: `docker version`으로 서버의 `MinAPIVersion`을
    확인하고, 그보다 낮은 버전으로 소켓에 직접 질의해본다.
    ```bash
    curl -s --unix-socket ~/.docker/run/docker.sock http://localhost/v1.24/info -o /dev/null -w '%{http_code}\n'
    # 400이면 그 API 버전은 이 Docker Desktop 엔진이 거부한다는 뜻
    curl -s --unix-socket ~/.docker/run/docker.sock http://localhost/info -o /dev/null -w '%{http_code}\n'
    # 버전 없는 요청은 200 - 소켓 자체는 멀쩡하다는 뜻
    ```
    `./gradlew :api:test ... --info`로 돌리면 STDOUT에 Testcontainers가
    시도한 각 전략(`UnixSocketClientProviderStrategy`,
    `DockerDesktopClientProviderStrategy`)의 실패 사유가 그대로
    찍힌다 - 둘 다 동일한 `BadRequestException (Status 400: ...)`이면
    이 문제가 맞다. `DOCKER_API_VERSION` 환경변수로 강제 협상은 **안
    먹힌다**(전략 탐지 자체가 버전을 하드코딩) - 라이브러리를 올려야
    한다.
  - **해결**: `backend/build.gradle`의 `testcontainers-bom` 버전을
    올린다(1.20.4 → 1.21.4에서 실측 해결, 이후 더 최신 버전이 나오면
    그쪽으로). `./gradlew :api:test :core:test`로 전체 스위트가
    회귀 없이 통과하는지 반드시 재확인할 것.
  - 참고: macOS 홈 디렉터리의 `~/.testcontainers.properties`에
    `docker.client.strategy`가 낡은 값으로 박제돼 있는 경우도 있는데
    (리포 안에는 이 파일이 없음 - 머신 전역 설정), 이건 위 API 버전
    문제와는 **별개의 원인**이다. 지워도(자동 감지가 살아나도) 위
    API 버전 비호환 자체는 해결되지 않으므로 혼동하지 말 것 - 실제로
    2026-07-13 세션에서 처음엔 이 핀이 원인이라고 오판했다가, 핀을
    지운 뒤에도 동일하게 재현되는 것을 보고서야 진짜 원인(API 버전
    비호환)을 찾았다.
  - Testcontainers가 Redis까지 격리하지는 않으므로, Redis(로컬
    인프라, 6381)가 안 떠 있으면 위 문제를 다 해결해도 같은 통합
    테스트가 이번엔 Redis 연결 실패로 다른 에러 메시지를 내며 깨진다
    (증상이 다르므로 구분할 것).

---

## 2. Playwright 헤드리스 브라우저로 실제 동작 검증하기

타입체크·빌드 통과와 "실제로 브라우저에서 동작함"은 다른 차원의
증거다. curl로는 버튼 클릭, localStorage 상태, WebSocket 프레임,
레이아웃 오버플로우를 확인할 수 없다.

### 설치 (프로젝트에 영구 의존성으로 남기지 않음)

```bash
cd frontend
npm install -D playwright --no-save   # package.json에 기록 안 됨
npx playwright install chromium        # 브라우저 바이너리, 최초 1회만
```

### 스크립트 작성 규칙

- 검증 스크립트는 **`frontend/` 디렉터리 안에** 임시로 둔다. Node의
  모듈 해석은 스크립트 자신의 위치를 기준으로 `node_modules`를
  찾으므로, `frontend/` 밖(예: 홈 디렉터리의 임시 폴더)에 두면
  `playwright` 모듈을 못 찾아 에러난다.
- 검증이 끝나면 스크립트를 삭제한다(`e2e_*.mjs`처럼 눈에 띄는 이름을
  쓰면 정리하기 쉽다). 커밋 대상이 아니다.

### 자주 쓰는 패턴

```js
import { chromium } from 'playwright';
const browser = await chromium.launch();
const page = await browser.newPage();

// 콘솔 에러/페이지 크래시 수집 — 거의 모든 스크립트에 기본으로 넣는다
page.on('console', (msg) => { if (msg.type() === 'error') console.log(msg.text()); });
page.on('pageerror', (err) => console.log(String(err)));

// 네트워크 응답 코드 확인 — 401→재발급→재시도 같은 인터셉터 로직 검증
page.on('response', (res) => console.log(res.status(), res.url()));

// WebSocket 실제 프레임 가로채기 — STOMP SUBSCRIBE가 정말 나갔는지 확인
page.on('websocket', (ws) => {
  ws.on('framesent', (d) => console.log('SENT', d.payload));
  ws.on('framereceived', (d) => console.log('RECV', d.payload));
});

// localStorage 조작 — 토큰 만료/손상 시나리오 재현
await page.evaluate(() => localStorage.setItem('ql_access', 'broken'));

// 네트워크 장애 시뮬레이션 — 백엔드를 실제로 안 내려도 됨
await page.route('**/api/watchlist', (route) => route.abort('failed'));

// 모바일 뷰포트 + 가로 오버플로우 체크
const mobilePage = await browser.newPage({ viewport: { width: 375, height: 667 } });
const { scrollWidth, clientWidth } = await mobilePage.evaluate(() => ({
  scrollWidth: document.documentElement.scrollWidth,
  clientWidth: document.documentElement.clientWidth,
}));
// scrollWidth > clientWidth 면 가로 오버플로우가 있다는 뜻
```

### 되돌릴 수 없는 조작을 검증할 때

예를 들어 "관심종목이 비어있을 때의 빈 상태 UI"를 확인하려면 기존
관심종목을 전부 지워야 한다. 이런 검증은:

1. 조작 **전에** 원래 상태를 기록한다(예: 화면에서 stockCode 목록을
   읽어두거나 DB를 직접 조회).
2. 조작 → 검증.
3. 같은 API(UI 클릭이든 직접 호출이든)로 **반드시 원상복구**한다.
4. DB를 직접 조회해 복구가 실제로 반영됐는지 재확인한다.

### 이 방식으로 실제 세션에서 잡은 버그 예시

- `sockjs-client`가 브라우저에 없는 Node 전역 `global`을 참조해
  로그인 페이지 전체가 빈 화면으로 렌더링된 것 — 타입체크·빌드는
  전부 통과했지만 브라우저에서 열어보지 않았다면 놓쳤을 문제.
- SockJS의 CORS 자격증명 요구사항으로 WebSocket 연결 자체가
  실패한 것 — `page.on('websocket', ...)`으로 실제 프레임을
  가로채서야 "구독 프레임이 0개"라는 걸 확인했다.
- 375px 모바일 뷰포트에서 스코어 대시보드 테이블이 페이지를 221px
  밀어내던 오버플로우, 캔들 차트가 컨테이너 밖으로 20px 삐져나오던
  문제 — 스크린샷을 직접 찍어보고서야 발견.
- React Query 기본 재시도(3회, 지수 백오프) 때문에 백엔드 장애 시
  에러 노출까지 7초 넘게 걸리던 것 — 네트워크 장애를 실제로
  시뮬레이션해 시간을 측정하고서야 드러남.

---

## 3. 배포(Docker) 아티팩트 로컬 검증

`docker-compose.prod.yml`(EC2 배포용, `docs/DEPLOYMENT.md` 참고)을 고치고
나서 실제 EC2에 올리기 전에 로컬에서 검증할 수 있는 부분과 없는 부분을
구분해야 한다. 이미지 빌드·컨테이너 간 배선·헬스체크는 로컬에서 100%
검증 가능하고, 실제 AWS 리소스(S3/CloudWatch/SNS)만 EC2에서 확인 가능하다.

### 전체 스택 로컬 기동

```bash
# 시크릿 파일 준비(실제 값 없이 형식만 맞아도 로컬 기동엔 충분 - Toss/OAuth
# 관련 값은 실제 자격증명이 없으면 그 기능만 실패하고 나머지는 정상 기동)
cp .env.prod.example .env.prod

# 이미지 3개 빌드 + 전체 스택 기동(dev용 docker-compose.yml과는
# name: quantlab-prod로 프로젝트명이 분리돼 있어 dev MySQL/Redis와
# 컨테이너·볼륨이 겹치지 않는다)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

curl http://localhost/api/health          # {"status":"UP",...}
curl http://localhost/                     # SPA index.html
curl -X POST http://localhost/dev/auth/token  # 405(nginx가 /dev를 안 막고 SPA로 떨어뜨림)

# 정리 - 로컬 전용 시크릿 파일은 커밋 대상이 아니므로 검증 끝나면 삭제
docker compose -f docker-compose.prod.yml --env-file .env.prod down
rm .env.prod
```

### CloudWatch 오버레이는 머지 결과만

`docker-compose.cloudwatch.yml`(로그 수집용, `awslogs` 드라이버)은 실제
AWS 자격증명이 있어야 컨테이너가 기동되므로 로컬에서 `up`까지는 못 한다.
`config`는 API 호출 없이 YAML만 머지하므로 로컬에서도 검증 가능:

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.cloudwatch.yml \
  --env-file .env.prod config | grep -A5 "logging:"
# 5개 서비스 모두 driver: awslogs 인지, awslogs-group이 서비스별로
# 맞게 갈렸는지(/quantlab/backend 등) 확인
```

### 모니터링 오버레이는 로컬에서도 실제 기동 가능

`docker-compose.monitoring.yml`(Prometheus/Grafana/Alertmanager/
node-exporter/cAdvisor, `docs/DEPLOYMENT.md` §13)은 `docker-compose.
cloudwatch.yml`과 달리 AWS 자격증명이 전혀 필요 없어 로컬에서 전체
스택을 그대로 기동해 검증할 수 있다:

```bash
cp .env.prod.example .env.prod
docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.yml \
  --env-file .env.prod up -d --build

curl -s localhost:8080/actuator/prometheus | head       # 백엔드 지표 노출 확인(포트가 열려 있다면)
curl -s localhost:9090/-/healthy                          # Prometheus
curl -s localhost:9090/api/v1/targets | grep '"health"'   # 전 타깃 up 확인
curl -s localhost:3002/api/health                          # Grafana (호스트 포트 3002)

docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.yml \
  --env-file .env.prod down
rm .env.prod
```

Alertmanager 설정(`monitoring/alertmanager/alertmanager.yml`)은 `${VAR}`
치환을 지원하지 않아 entrypoint가 sed로 플레이스홀더
(`__SLACK_ALERT_WEBHOOK_URL__`)를 실제 값으로 치환한 사본을 만들어
기동한다 - 로그에 `"Completed loading of configuration file"`이 찍히면
정상, `"unsupported scheme"` 에러가 보이면 `SLACK_ALERT_WEBHOOK_URL`
값이 실제 URL 형식(`https://hooks.slack.com/...`)이 아니라는 뜻이다.

### 신규 셸 스크립트는 문법 검사까지만

`scripts/*.sh`(모니터링 지표 전송, MySQL 백업, cron 등록)는 EC2 호스트
전제(컨테이너 이름 `quantlab-prod-*`을 직접 참조, `aws` CLI 호출)라
로컬에서 실행까지는 의미 없다. 문법 오류만 로컬에서 잡는다:

```bash
bash -n scripts/report-health-metric.sh
bash -n scripts/backup-mysql.sh
bash -n scripts/install-cron.sh
```

**`scripts/install-cron.sh`는 로컬에서 실제로 실행하지 말 것** - 이
스크립트는 현재 로그인한 사용자의 진짜 `crontab`을 수정한다. EC2 위
전용 스크립트를 로컬 개발 머신에서 돌리면 본인 macOS/Linux 계정의
크론탭이 오염된다.

### 이 방식으로 실제 세션에서 잡은 버그 예시

- `docker-compose.prod.yml`에 프로젝트명을 명시하지 않았더니, dev용
  `docker-compose.yml`과 같은 디렉터리라 기본 프로젝트명(디렉터리명)을
  공유해 로컬에서 prod 스택을 검증차 띄우자 dev MySQL/Redis 컨테이너·
  볼륨을 그대로 재사용(사실상 덮어씀)해버린 것 — 실제로 `docker compose
  up`을 돌려보고 `Recreate` 로그를 보고서야 발견했다(`name: quantlab-prod`
  로 해결).
- `GlobalExceptionHandler`의 catch-all이 매핑되지 않은 모든 경로를
  500으로 응답시키고 있던 것 — `/dev/auth/token`이 prod 프로파일에서
  정말 404가 되는지 문서에 쓰기 전에 실제 컨테이너에 curl을 날려보다
  발견(nginx 계층에서 먼저 405로 막힌다는 것도 이때 같이 확인했다 -
  둘 다 실제로 요청을 보내보지 않았다면 문서에 틀린 내용을 남길
  뻔했다).
