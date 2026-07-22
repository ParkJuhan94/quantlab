# 배포 가이드 — 단일 EC2 + nginx 리버스 프록시

QuantLime을 EC2 인스턴스 한 대에 Docker Compose로 배포하는 방법을 정리한다.
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

로그·리소스·앱 알림은 CloudWatch가 아니라 **자체 호스팅 PLG(Prometheus·
Grafana·Alertmanager) 스택**(§10)으로 일원화했다 - AWS 자격증명 없이
로컬에서도 그대로 기동해 검증할 수 있고, Slack 하나로 알림을 모을 수
있다. DB 백업(§5)만 S3 + 최소 권한 IAM 인스턴스 역할을 쓴다(백업 파일을
어딘가로 옮겨야 해서 AWS 자격증명이 불가피하다).

---

## 1. EC2 사전 준비

- Ubuntu 22.04 LTS 권장, **t3.medium 이상**(MySQL+Redis+Spring+FastAPI+
  nginx에 더해 §10의 PLG 관측성 스택까지 상시 구동 - 스택만으로 약 1GB
  추가 RAM 필요, §10 참고)
- 보안 그룹 인바운드: `22`(SSH, 본인 IP로 제한 권장), `80`(HTTP),
  `443`(HTTPS, TLS 적용 시). Prometheus/Grafana/Alertmanager 포트
  (9090/9093/3002)는 열지 않는다(§10에서 SSH 터널로만 접근).
- **Elastic IP 할당 후 인스턴스에 연결.** 일반 EC2 퍼블릭 IP는 인스턴스
  재시작(중지→시작)마다 바뀐다 - 도메인 A레코드와 OAuth redirect URI가
  전부 이 IP를 참조하므로, 고정해두지 않으면 재시작할 때마다 둘 다
  다시 맞춰야 한다.
  ```bash
  aws ec2 allocate-address --domain vpc --region ap-northeast-2
  aws ec2 associate-address --instance-id <INSTANCE_ID> --allocation-id <ALLOCATION_ID>
  ```
- Docker + Docker Compose 플러그인 설치:
  ```bash
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker $USER   # 재로그인 필요
  sudo apt-get install -y docker-compose-plugin
  ```
- AWS CLI v2 설치(§5의 MySQL 백업 스크립트가 S3 업로드에 사용):
  ```bash
  curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o awscliv2.zip
  sudo apt-get install -y unzip && unzip awscliv2.zip && sudo ./aws/install
  ```
- 저장소 클론(GitHub Actions 배포 워크플로가 `cd ~/quantlime && git pull`을
  전제하므로 반드시 이 경로에 클론):
  ```bash
  git clone https://github.com/ParkJuHan94/quantlime.git ~/quantlime
  cd ~/quantlime
  ```

## 2. GHCR(GitHub Container Registry) 접근

이미지는 `.github/workflows/cd.yml`이 태그 푸시(`v*`) 또는 수동
실행(`workflow_dispatch`) 시 GHCR에 퍼블리시한다. EC2에서 이미지를
pull하려면 저장소를 public으로 두거나(가장 간단), private이면 EC2에서도
로그인이 필요하다:

```bash
echo $GHCR_PAT | docker login ghcr.io -u <github-username> --password-stdin
```

## 3. 시크릿 파일 배치

`.env.prod.example`을 참고해 `~/quantlime/.env.prod`를 만든다(이 파일은
`.gitignore` 대상이라 저장소에 없으므로 EC2에서 직접 작성):

```bash
cp .env.prod.example .env.prod
vi .env.prod   # 실제 값으로 채움 (AWS_REGION, BACKUP_S3_BUCKET 포함)
```

**주의**: `docker-compose.prod.yml`은 `${VAR}` 치환에 `.env.prod`를 쓰기
위해 항상 `--env-file .env.prod` 플래그와 함께 실행해야 한다(Docker
Compose는 기본적으로 같은 디렉터리의 `.env`만 자동 로드하고, 다른
이름의 파일은 명시적으로 지정해야 인식한다).

## 4. 최초 기동

모니터링 스택(§10, PLG)까지 함께 켜는 것을 권장한다 - AWS 자격증명이
필요 없어 처음부터 같이 켜도 안전하다(`GRAFANA_ADMIN_PASSWORD`/
`SLACK_ALERT_WEBHOOK_URL`을 아직 `.env.prod`에 안 채웠어도 기본값으로
기동은 되고, 알림 전송만 나중에 값 채운 뒤 재기동하면 된다).
`cd.yml`의 자동 배포도 이 오버레이를 항상 포함한다(§9).

```bash
cd ~/quantlime
docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.yml --env-file .env.prod pull
docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.yml --env-file .env.prod up -d
docker compose -f docker-compose.prod.yml logs -f backend   # 기동 확인
```

모니터링 스택 없이 최소 구성만 먼저 확인하고 싶다면 `-f
docker-compose.monitoring.yml`을 빼고 `docker-compose.prod.yml`만 써도
된다.

확인:
- `curl http://localhost/api/health` → `{"status":"UP","service":"QuantLime"}`
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

## 5. DB 백업 (S3, IAM 인스턴스 역할)

`scripts/backup-mysql.sh`가 `quantlime-prod-mysql` 컨테이너 안에서
`mysqldump`(대상 DB만, `--all-databases` 아님)를 떠 gzip 압축 후 S3에
올린다. **Redis는 백업 대상이 아니다** - `PriceCacheStore`가 담는 건
시세 스냅샷/스코어 캐시뿐이라 유실돼도 다음 폴링 틱·배치에서 다시
채워지는 파생 데이터이고, 유일한 진실 소스는 MySQL뿐이다.

S3 업로드 권한은 액세스 키를 `.env.prod`에 박아넣지 않고 **EC2 인스턴스
프로파일(IAM Role)**로 부여한다 - 그 파일이 컨테이너에도 `env_file:`로
그대로 주입되기 때문에, 인스턴스 역할을 쓰면 자격증명이 파일로 존재하지
않고 EC2 메타데이터 서비스를 통해서만 유효하다.

1. S3 버킷 생성 + 라이프사이클(장기 보존은 스크립트가 아니라 버킷
   설정에 위임 - 30일 후 자동 만료로 비용 억제):
   ```bash
   aws s3api create-bucket --bucket <BACKUP_S3_BUCKET> \
     --region ap-northeast-2 \
     --create-bucket-configuration LocationConstraint=ap-northeast-2

   aws s3api put-bucket-lifecycle-configuration \
     --bucket <BACKUP_S3_BUCKET> \
     --lifecycle-configuration '{
       "Rules": [{
         "ID": "expire-old-mysql-backups",
         "Filter": {"Prefix": "mysql/"},
         "Status": "Enabled",
         "Expiration": {"Days": 30}
       }]
     }'
   ```
2. IAM 정책 생성(계정 ID·버킷명은 실제 값으로 교체) → 역할 생성(신뢰
   대상 EC2) → 인라인 정책으로 연결 → EC2 인스턴스에 역할 연결:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "MysqlBackupBucket",
         "Effect": "Allow",
         "Action": ["s3:PutObject", "s3:GetObject", "s3:ListBucket"],
         "Resource": [
           "arn:aws:s3:::<BACKUP_S3_BUCKET>",
           "arn:aws:s3:::<BACKUP_S3_BUCKET>/*"
         ]
       }
     ]
   }
   ```
   IAM 콘솔 → 역할 생성 → 신뢰 대상 EC2 → 위 정책을 인라인으로 연결 →
   EC2 콘솔 → 인스턴스 선택 → 작업 → 보안 → IAM 역할 수정 → 방금 만든
   역할 연결. 확인: EC2에서 `aws sts get-caller-identity`가 역할 ARN을
   반환하면 정상 연결된 것.
3. `.env.prod`에 `BACKUP_S3_BUCKET`(위에서 만든 버킷명) 기입 확인(§3)
4. cron 등록(매일 03:00 - 장중·16시 배치와 겹치지 않는 새벽 시간대):
   ```bash
   ~/quantlime/scripts/install-cron.sh
   ```
5. 수동 1회 실행으로 확인:
   ```bash
   ~/quantlime/scripts/backup-mysql.sh
   aws s3 ls s3://<BACKUP_S3_BUCKET>/mysql/
   ```
6. **복구 절차**(백업 파일에서 MySQL로 되돌리기):
   ```bash
   # 로컬에 남아있는 백업(최근 3개)에서 복구
   gunzip -c ~/quantlime/backups/quantlime-mysql-<TIMESTAMP>.sql.gz \
     | docker exec -i -e MYSQL_PWD="$DB_PASSWORD" quantlime-prod-mysql mysql -u root quantlime

   # 또는 S3에서 직접 스트리밍 복구
   aws s3 cp s3://<BACKUP_S3_BUCKET>/mysql/quantlime-mysql-<TIMESTAMP>.sql.gz - \
     | gunzip \
     | docker exec -i -e MYSQL_PWD="$DB_PASSWORD" quantlime-prod-mysql mysql -u root quantlime
   ```

## 6. GitHub Actions 배포 시크릿

`.github/workflows/cd.yml`이 참조하는 리포지토리 시크릿(Settings →
Secrets and variables → Actions):

| 시크릿 | 용도 |
|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP 또는 도메인(Elastic IP 고정 후 값 - §1) |
| `EC2_USER` | SSH 접속 계정(예: `ubuntu`) |
| `EC2_SSH_KEY` | EC2 접속용 프라이빗 키(PEM 전체 내용) |
| `VITE_GOOGLE_CLIENT_ID` / `VITE_KAKAO_CLIENT_ID` / `VITE_NAVER_CLIENT_ID` | 프론트 빌드 시 인라인되는 공개 OAuth 클라이언트 ID(시크릿 값 아니지만 저장소에 안 남기려고 시크릿으로 관리) |

`GITHUB_TOKEN`(GHCR 푸시용)은 워크플로에 자동 주입되므로 별도 등록 불필요.

이후 배포는 `git tag v1.0.0 && git push origin v1.0.0` 또는 Actions 탭에서
`CD` 워크플로를 수동 실행(`workflow_dispatch`)하면 된다. `deploy` job은
EC2 SSH 시크릿 3종이 모두 등록돼 있어야 성공한다 - `build-and-push` job은
이 시크릿들과 무관하게(GHCR 푸시만) 독립적으로 성공할 수 있다.

## 7. OAuth 리다이렉트 URI 재등록

로컬 개발은 `http://localhost:3001/oauth/callback/{provider}`를 콘솔에
등록해뒀지만, 실제 배포 도메인에서는 각 프로바이더(구글/카카오/네이버)
콘솔에 **운영 도메인 기준 redirect URI**를 추가로 등록해야 한다(예:
`https://your-domain.example.com/oauth/callback/google`). 리다이렉트
URI는 프론트가 `window.location.origin` 기준으로 런타임에 동적 생성해
백엔드로 넘기므로 `.env.prod`에 별도 값을 맞출 필요는 없고, 프로바이더
콘솔 등록만 하면 된다. 이 콘솔 등록 작업은 각 계정 소유자가 직접
진행해야 하는 부분으로, 실제 OAuth 라운드트립 검증은 이 등록이 끝난
뒤에만 가능하다(CLAUDE.md "다음 작업"에 기록된 미완 항목).

## 8. TLS(다음 단계, 이번 범위 밖)

현재 구성은 HTTP(80)만 지원한다. 도메인이 연결되면 `certbot`으로 nginx에
TLS를 추가하는 것을 다음 단계로 남겨둔다(예: `certbot --nginx` 또는
`frontend` 컨테이너 앞에 별도 TLS 종단 프록시를 두는 방식). OAuth
프로바이더 상당수가 프로덕션 redirect URI에 HTTPS를 요구하므로, 실제
소셜 로그인 검증 전에 선행돼야 한다.

## 9. 배포 갱신 / 롤백

```bash
# 최신 배포로 갱신 (CI/CD가 자동으로 수행하는 것과 동일한 절차)
cd ~/quantlime && git pull
docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.yml --env-file .env.prod pull
docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.yml --env-file .env.prod up -d

# 특정 커밋 SHA로 롤백 (cd.yml이 latest와 함께 :<sha> 태그도 푸시함)
docker pull ghcr.io/parkjuhan94/quantlime-backend:<sha>
docker tag ghcr.io/parkjuhan94/quantlime-backend:<sha> ghcr.io/parkjuhan94/quantlime-backend:latest
docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.yml --env-file .env.prod up -d backend
```

## 10. 모니터링 스택 (Prometheus / Grafana / Alertmanager)

로그·리소스·앱 알림 전체를 CloudWatch 대신 이 self-host 관측성
스택(PLG류) 하나로 처리한다. **앱 내부 지표**(JVM, HTTP 요청률/지연/
에러율, Toss API 429 발생률, 전종목 시세 스윕 소요시간, 퀀트 엔진 호출
실패율 등)뿐 아니라 호스트 리소스(node-exporter)·컨테이너별 리소스
(cAdvisor)까지 함께 본다. `docker-compose.monitoring.yml`이 오버레이로
분리돼 있고, AWS 자격증명이 필요 없어 로컬에서도 그대로 기동해 검증할
수 있다(`docs/DEVELOPMENT.md` 참고). §4의 최초 기동부터 기본 포함해서
켜는 것을 권장하고, `cd.yml`의 자동 배포도 항상 포함한다(§9).

구성 요소: Prometheus(지표 수집·저장, 15일 보존), Alertmanager(Slack
알림 라우팅), Grafana(대시보드 3종 - JVM/Spring HTTP/QuantLime 비즈니스
지표, `monitoring/grafana/` 프로비저닝으로 최초 기동 시 자동 로드),
node-exporter(호스트 리소스), cAdvisor(컨테이너별 리소스). 백엔드는
`spring-boot-starter-actuator` + Micrometer로 `/actuator/prometheus`를
노출하고(`backend/api/build.gradle`), quant-engine은
`prometheus-fastapi-instrumentator`로 `/metrics`를 노출한다
(`quant-engine/main.py`).

1. `.env.prod`에 아래 두 값을 채운다(`.env.prod.example` 참고). 값을
   아직 안 채웠어도 기본값(`admin` / 빈 웹훅)으로 기동은 되므로 §4의
   최초 기동을 막지 않는다 - Slack 알림만 나중에 채운 뒤 재기동하면 됨:
   - `GRAFANA_ADMIN_PASSWORD` — Grafana 초기 관리자 비밀번호
   - `SLACK_ALERT_WEBHOOK_URL` — 알림 전용 Slack Incoming Webhook URL.
     **`SLACK_FEEDBACK_WEBHOOK_URL`(사용자 피드백, §11)과 다른 채널로
     새로 발급받을 것을 권장** - 인프라 알람과 사용자 피드백이 같은
     채널에 섞이면 노이즈가 커진다.
2. §4에서 이미 `docker-compose.monitoring.yml`을 포함해 기동했다면 별도
   조치가 필요 없다. 나중에 추가하는 경우엔 같은 오버레이를 `-f`로
   얹어 재기동하면 된다:
   ```bash
   docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.yml \
     --env-file .env.prod up -d --build
   ```
3. **Prometheus/Grafana/Alertmanager UI는 외부에 노출하지 않는다**
   (`docker-compose.monitoring.yml`이 호스트 포트를 열긴 하지만, 보안
   그룹에서 9090/9093/3002를 열어두지 말 것 - 22/80/443만 인바운드
   허용하는 §1 설정을 그대로 유지). 대신 SSH 터널로 접근한다:
   ```bash
   ssh -L 3002:localhost:3002 -L 9090:localhost:9090 -L 9093:localhost:9093 \
     ubuntu@<EC2_HOST>
   # 이후 로컬 브라우저에서 http://localhost:3002 (Grafana),
   # http://localhost:9090 (Prometheus), http://localhost:9093 (Alertmanager)
   ```
4. Grafana 접속 후 좌측 `Dashboards → QuantLime` 폴더에 3개 대시보드가
   프로비저닝돼 있는지 확인(JVM, Spring Boot HTTP, 비즈니스 지표).
   데이터소스(Prometheus)도 프로비저닝으로 자동 등록된다 - 수동 설정 불필요.
5. 알림 규칙은 `monitoring/prometheus/rules/alerts.yml`에 코드로 관리한다
   (BackendDown, QuantEngineDown, HighHttp5xxRate, TossRateLimitSpike,
   QuantEngineFailureRate, JvmHeapHigh, HostMemoryHigh, HostDiskHigh).
   Alertmanager UI(`http://localhost:9093`, 터널 경유)에서 발화 중인
   알림/Silence 상태를 확인할 수 있다.
6. 리소스 영향: 이 스택은 EC2에 **약 1GB의 추가 RAM**을 요구한다(Prometheus
   +Grafana+Alertmanager+node-exporter+cAdvisor 합산) - §1에서 t3.medium
   이상을 권장한 이유. 로컬 검증에는 이 제약이 적용되지 않는다.
7. Phase 2(로그, Loki)·Phase 3(트레이스, Tempo)는 아직 미착수 - CLAUDE.md
   작업 기록 참고.

## 11. §10 이후 추가된 연동 (KIS·네이버금융·TradingView·이미지 업로드·쿠키 인증)

§10(관측성 스택) 이후 세션들에서 추가된 기능들이 배포 설정에 요구하는
부분을 정리한다. `.env.prod.example`·`docker-compose.prod.yml`·
`frontend/nginx.conf`는 이미 아래 내용을 반영해뒀으므로, `.env.prod`를
새로 작성하거나 갱신할 때 참고만 하면 된다.

### 외부 연동 (KIS / 네이버금융 / TradingView)

- **한국투자증권(KIS)**: 해외 종목(백테스트 유니버스) 조회용. `KIS_APP_KEY`
  `KIS_APP_SECRET`을 KIS Developers 포털에서 발급해 `.env.prod`에 채운다.
  미설정 시 이 기능만 비활성되고 나머지 서비스는 정상 동작(`app-key`/
  `app-secret`의 기본값이 빈 문자열이라 애플리케이션 부팅 자체는 막지
  않음 - 실제 호출 시점에 인증 실패로만 나타남).
  Toss가 국내 전용이라(CLAUDE.md §4) 별도로 필요해진 연동이다.
- **네이버 금융 / TradingView**: 둘 다 인증 불필요한 공개 API라
  `.env.prod.example`의 기본값을 그대로 두면 된다 - 도메인이 바뀌지 않는
  한 손댈 일이 없다.

### 이미지 업로드 (로컬 디스크 저장)

피드 게시글·"의견 보내기" 첨부 이미지는 별도 오브젝트 스토리지 없이
`backend` 컨테이너 내부 디스크(`FileStorageService`)에 저장하고, 같은
경로를 `/uploads/**`로 정적 서빙한다(`WebConfig.addResourceHandlers`).
단일 EC2 규모에 맞춘 의도적으로 단순한 설계지만, 컨테이너화 환경이라
아래 두 가지를 반드시 배선해줘야 한다 - 둘 중 하나라도 빠지면 이미지
업로드 자체는 성공하는데 업로드된 이미지가 안 보이거나(nginx) 다음
배포에서 사라지는(볼륨) 형태로 나타나 뒤늦게 발견하기 쉽다:

1. **영속 볼륨**: `docker-compose.prod.yml`의 `backend` 서비스에
   `upload_data:/app/uploads` 볼륨을 마운트해뒀다(`UPLOAD_DIR=/app/uploads`
   와 반드시 같은 경로). 이게 없으면 §9의 `pull && up -d`(배포마다
   컨테이너 재생성)를 실행할 때마다 그동안 업로드된 이미지가 전부
   유실된다.
2. **nginx 프록시**: `frontend/nginx.conf`에 `location /uploads/`를
   `backend`로 프록시하는 블록을 추가해뒀다. 이게 없으면 `/uploads/*`
   요청이 이 파일 맨 아래 SPA catch-all(`location /`)에 걸려 이미지
   대신 `index.html`이 내려간다(404가 아니라 200 + 잘못된 콘텐츠라
   원인 파악이 더 헷갈릴 수 있음).

`.env.prod`의 `UPLOAD_PUBLIC_BASE_URL`은 반드시 실제 서비스 도메인으로
채울 것 - 기본값(`localhost:8080`)을 그대로 두면 서버가 절대 URL을
만들어야 하는 소비자(Slack 웹훅 등, `FeedbackService` 참고)에서 이미지
링크가 깨진다. 업로드 크기 제한은 `.env.prod`의 `UPLOAD_MAX_SIZE_BYTES`
(애플리케이션 레벨 검증, `FileStorageService`)와 `application.yml`의
`spring.servlet.multipart.max-file-size`(서블릿 레벨, 현재 5MB 하드코딩)
**둘 다** 올려야 한다 - 서블릿 쪽이 더 작으면 애플리케이션 코드까지
요청이 도달하기 전에 잘린다.

확인:
```bash
curl -F "image=@test.png" -H "Authorization: Bearer $TOKEN" \
  http://<EC2_HOST>/api/uploads/images
# {"imageUrl":"/uploads/xxxxxxxx.png"} 형태 응답 확인 후
curl -I http://<EC2_HOST>/uploads/xxxxxxxx.png   # 200 확인(nginx 프록시)
docker compose -f docker-compose.prod.yml --env-file .env.prod \
  exec backend ls /app/uploads   # 실제 파일 존재 확인(볼륨 마운트)
```

### Slack 피드백 웹훅

사이드패널 "의견 보내기"가 전송하는 `SLACK_FEEDBACK_WEBHOOK_URL`은
§10의 `SLACK_ALERT_WEBHOOK_URL`(인프라 알람)과 **다른 채널**로 별도
발급받을 것을 권장한다 - 사용자 피드백과 인프라 알람이 섞이면 노이즈가
커진다. 미설정 시 조용히 실패하지 않고 "의견 보내기" API가 명시적으로
`WEBHOOK_NOT_CONFIGURED` 에러를 반환한다.

### 쿠키 기반 리프레시 토큰

리프레시 토큰이 응답 바디 대신 httpOnly 쿠키(`RefreshTokenCookieProvider`)로
내려가도록 바뀌었다(XSS 시 탈취 범위를 액세스 토큰으로 한정하기 위함).
`.env.prod`의 `COOKIE_SECURE`는 **현재 배포(순수 HTTP)에서는 반드시
`false`로 둘 것** - `secure=true` 쿠키는 HTTPS가 아니면 브라우저가
저장 자체를 거부해 로그인 유지가 안 되는 형태로 나타난다. §8(TLS)을
적용해 도메인에 HTTPS가 붙은 뒤에는 **`COOKIE_SECURE=true`로 반드시
전환**할 것 - 이 전환을 잊으면 로그인은 계속 되지만 리프레시 토큰이
평문 HTTP로도 전송 가능한 상태로 남아 §8을 적용한 의미가 퇴색된다.
프론트-백엔드가 같은 오리진(nginx same-origin 프록시)이라 CORS/쿠키
전송 자체는 별도 설정 없이 동작한다(SameSite=Strict, `axios`
`withCredentials: true` 기본 적용됨).
