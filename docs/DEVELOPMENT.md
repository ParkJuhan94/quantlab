# 개발 가이드

로컬에서 백엔드+프론트엔드를 함께 띄우는 법과, 실제 동작을 검증하는
방법론을 정리한다.

---

## 1. 로컬 개발 환경 실행

```bash
# 1) 인프라 (MySQL 3308, Redis 6381)
docker-compose up -d

# 2) 백엔드 (dev 프로필이 application.yml 기본값)
cd backend && ./gradlew :api:bootRun

# 3) quant-engine (최초 1회만 venv 준비, Spring과 별도 프로세스로 항상 띄워야 함)
cd quant-engine
python3 -m venv venv && source venv/bin/activate && pip install -r requirements.txt   # 최초 1회
source venv/bin/activate
uvicorn main:app --reload --port 8000

# 4) 프론트엔드 (최초 1회만 .env.local 준비)
cd frontend
cp .env.example .env.local   # VITE_API_BASE_URL=http://localhost:8080 정도면 충분
npm run dev                   # http://localhost:3001 고정(strictPort)
```

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
