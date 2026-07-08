# 배포 가이드 — 단일 EC2 + nginx 리버스 프록시

QuantLab을 EC2 인스턴스 한 대에 Docker Compose로 배포하는 방법을 정리한다.
아키텍처: `frontend`(nginx, 80) 가 정적 파일을 서빙하면서 `/api`·`/ws`
요청을 같은 오리진에서 `backend`(8080)로 리버스 프록시한다. 프론트가
same-origin으로만 요청하므로 브라우저 CORS 자체가 필요 없어진다
(`frontend/nginx.conf`, `frontend/Dockerfile`의 `VITE_API_BASE_URL=""` 참고).

```
인터넷 → EC2:80(nginx, frontend 컨테이너)
              ├── / → 정적 파일(React SPA)
              ├── /api/* → backend:8080 (내부 네트워크)
              └── /ws/*  → backend:8080 (내부 네트워크, WebSocket 업그레이드)
         backend:8080 → mysql:3306, redis:6379, quant-engine:8000 (모두 내부 네트워크, 외부 미노출)
```

---

## 1. EC2 사전 준비

- Ubuntu 22.04 LTS 권장, t3.small 이상(MySQL+Redis+Spring+FastAPI+nginx
  동시 구동 고려)
- 보안 그룹 인바운드: `22`(SSH, 본인 IP로 제한 권장), `80`(HTTP),
  `443`(HTTPS, TLS 적용 시)
- Docker + Docker Compose 플러그인 설치:
  ```bash
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker $USER   # 재로그인 필요
  sudo apt-get install -y docker-compose-plugin
  ```
- 저장소 클론(GitHub Actions 배포 워크플로가 `cd ~/quantlab && git pull`을
  전제하므로 반드시 이 경로에 클론):
  ```bash
  git clone https://github.com/ParkJuHan94/quantlab.git ~/quantlab
  cd ~/quantlab
  ```

## 2. GHCR(GitHub Container Registry) 접근

이미지는 `.github/workflows/deploy.yml`이 태그 푸시(`v*`) 또는 수동
실행(`workflow_dispatch`) 시 GHCR에 퍼블리시한다. EC2에서 이미지를
pull하려면 저장소를 public으로 두거나(가장 간단), private이면 EC2에서도
로그인이 필요하다:

```bash
echo $GHCR_PAT | docker login ghcr.io -u <github-username> --password-stdin
```

## 3. 시크릿 파일 배치

`.env.prod.example`을 참고해 `~/quantlab/.env.prod`를 만든다(이 파일은
`.gitignore` 대상이라 저장소에 없으므로 EC2에서 직접 작성):

```bash
cp .env.prod.example .env.prod
vi .env.prod   # 실제 값으로 채움
```

**주의**: `docker-compose.prod.yml`은 `${VAR}` 치환에 `.env.prod`를 쓰기
위해 항상 `--env-file .env.prod` 플래그와 함께 실행해야 한다(Docker
Compose는 기본적으로 같은 디렉터리의 `.env`만 자동 로드하고, 다른
이름의 파일은 명시적으로 지정해야 인식한다).

## 4. 최초 기동

```bash
cd ~/quantlab
docker compose -f docker-compose.prod.yml --env-file .env.prod pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
docker compose -f docker-compose.prod.yml logs -f backend   # 기동 확인
```

확인:
- `curl http://localhost/api/health` → `{"status":"UP","service":"QuantLab"}`
- `curl http://localhost/` → React SPA `index.html` 응답
- `curl -X POST http://localhost/dev/auth/token` → `405 Not Allowed`(nginx
  응답). `nginx.conf`가 `/api/`·`/ws/`만 backend로 프록시하고 `/dev/**`는
  아예 정의돼 있지 않아 SPA 정적 파일 catch-all(`location /`)로 떨어져
  backend까지 요청이 도달하지도 않는다 - 개발용 토큰 발급 엔드포인트가
  nginx 계층에서부터 막히는지 여기서 반드시 확인.
  덧붙여 backend가 이 요청을 직접 받는 경우(예: nginx 설정 실수로 프록시
  범위가 넓어진 상황)에도 `DevController`가 `@Profile("dev")`라 prod
  프로파일에서 로드되지 않아 404로 응답한다(이중 방어 - 컨테이너 내부에서
  `docker compose exec backend`로 직접 확인 가능)

## 5. GitHub Actions 배포 시크릿

`.github/workflows/deploy.yml`이 참조하는 리포지토리 시크릿(Settings →
Secrets and variables → Actions):

| 시크릿 | 용도 |
|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP 또는 도메인 |
| `EC2_USER` | SSH 접속 계정(예: `ubuntu`) |
| `EC2_SSH_KEY` | EC2 접속용 프라이빗 키(PEM 전체 내용) |
| `VITE_GOOGLE_CLIENT_ID` / `VITE_KAKAO_CLIENT_ID` / `VITE_NAVER_CLIENT_ID` | 프론트 빌드 시 인라인되는 공개 OAuth 클라이언트 ID(시크릿 값 아니지만 저장소에 안 남기려고 시크릿으로 관리) |

`GITHUB_TOKEN`(GHCR 푸시용)은 워크플로에 자동 주입되므로 별도 등록 불필요.

이후 배포는 `git tag v1.0.0 && git push origin v1.0.0` 또는 Actions 탭에서
`Deploy` 워크플로를 수동 실행(`workflow_dispatch`)하면 된다.

## 6. OAuth 리다이렉트 URI 재등록

로컬 개발은 `http://localhost:3001/oauth/callback/{provider}`를 콘솔에
등록해뒀지만, 실제 배포 도메인에서는 각 프로바이더(구글/카카오/네이버)
콘솔에 **운영 도메인 기준 redirect URI**를 추가로 등록해야 한다(예:
`https://your-domain.example.com/oauth/callback/google`). `.env.prod`의
`GOOGLE_REDIRECT_URI` 등도 동일하게 맞출 것. 이 프로바이더 콘솔 등록
작업은 각 계정 소유자가 직접 진행해야 하는 부분으로, 실제 OAuth
라운드트립 검증은 이 등록이 끝난 뒤에만 가능하다(CLAUDE.md "다음 작업"에
기록된 미완 항목).

## 7. TLS(다음 단계, 이번 범위 밖)

현재 구성은 HTTP(80)만 지원한다. 도메인이 연결되면 `certbot`으로 nginx에
TLS를 추가하는 것을 다음 단계로 남겨둔다(예: `certbot --nginx` 또는
`frontend` 컨테이너 앞에 별도 TLS 종단 프록시를 두는 방식). OAuth
프로바이더 상당수가 프로덕션 redirect URI에 HTTPS를 요구하므로, 실제
소셜 로그인 검증 전에 선행돼야 한다.

## 8. 배포 갱신 / 롤백

```bash
# 최신 배포로 갱신 (CI/CD가 자동으로 수행하는 것과 동일한 절차)
cd ~/quantlab && git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d

# 특정 커밋 SHA로 롤백 (deploy.yml이 latest와 함께 :<sha> 태그도 푸시함)
docker pull ghcr.io/parkjuhan94/quantlab-backend:<sha>
docker tag ghcr.io/parkjuhan94/quantlab-backend:<sha> ghcr.io/parkjuhan94/quantlab-backend:latest
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend
```
