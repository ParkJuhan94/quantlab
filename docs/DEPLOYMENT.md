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

로그·모니터링·백업(§5~§8)은 CloudWatch Agent 같은 별도 에이전트 없이
**IAM 인스턴스 역할 + AWS CLI 기반 셸 스크립트/오버레이**로 구성한다.
단일 EC2, 저트래픽 규모에 맞춘 최소 구성이며, 인스턴스가 여러 대로
늘어나면 그때 CloudWatch Agent나 Container Insights로 전환을 검토한다.

---

## 1. EC2 사전 준비

- Ubuntu 22.04 LTS 권장, t3.small 이상(MySQL+Redis+Spring+FastAPI+nginx
  동시 구동 고려)
- 보안 그룹 인바운드: `22`(SSH, 본인 IP로 제한 권장), `80`(HTTP),
  `443`(HTTPS, TLS 적용 시)
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
- AWS CLI v2 설치(§5~§8의 로그/모니터링/백업 스크립트가 사용):
  ```bash
  curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o awscliv2.zip
  sudo apt-get install -y unzip && unzip awscliv2.zip && sudo ./aws/install
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
vi .env.prod   # 실제 값으로 채움 (AWS_REGION, BACKUP_S3_BUCKET 포함)
```

**주의**: `docker-compose.prod.yml`은 `${VAR}` 치환에 `.env.prod`를 쓰기
위해 항상 `--env-file .env.prod` 플래그와 함께 실행해야 한다(Docker
Compose는 기본적으로 같은 디렉터리의 `.env`만 자동 로드하고, 다른
이름의 파일은 명시적으로 지정해야 인식한다).

## 4. 최초 기동

로그 수집(§6)까지 함께 켜려면 `docker-compose.cloudwatch.yml`을
`-f`로 추가한다(§5의 IAM 역할이 먼저 연결돼 있어야 컨테이너가 정상
기동한다 - 없으면 각 컨테이너가 CloudWatch Logs 인증 실패로 뜨지 않음).
IAM/S3/CloudWatch를 아직 안 붙였다면 우선 `docker-compose.prod.yml`만
쓰고, §5~§8을 마친 뒤 재기동해도 된다.

```bash
cd ~/quantlab
docker compose -f docker-compose.prod.yml -f docker-compose.cloudwatch.yml --env-file .env.prod pull
docker compose -f docker-compose.prod.yml -f docker-compose.cloudwatch.yml --env-file .env.prod up -d
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

## 5. 로그·모니터링·백업 사전 준비 (IAM 인스턴스 역할)

§6~§8의 스크립트/오버레이가 공통으로 필요로 하는 권한을 EC2 인스턴스
프로파일(IAM Role)로 부여한다. 액세스 키를 `.env.prod`에 박아넣지
않는 이유는, 그 파일이 컨테이너에도 `env_file:`로 그대로 주입되기
때문 - 인스턴스 역할을 쓰면 자격증명이 파일로 존재하지 않고 EC2
메타데이터 서비스를 통해서만 유효하다.

1. 정책 생성(계정 ID·리전은 실제 값으로 교체):
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "ContainerLogsToCloudWatch",
         "Effect": "Allow",
         "Action": [
           "logs:CreateLogGroup",
           "logs:CreateLogStream",
           "logs:PutLogEvents",
           "logs:DescribeLogStreams"
         ],
         "Resource": "arn:aws:logs:ap-northeast-2:<ACCOUNT_ID>:log-group:/quantlab/*"
       },
       {
         "Sid": "CustomMetrics",
         "Effect": "Allow",
         "Action": "cloudwatch:PutMetricData",
         "Resource": "*"
       },
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
   (`cloudwatch:PutMetricData`는 AWS API 특성상 리소스 수준 권한을
   지원하지 않아 `Resource: "*"`가 불가피하다 - 대신 액션 자체를
   `PutMetricData` 하나로만 제한)
2. IAM 콘솔 → 역할 생성 → 신뢰 대상 EC2 → 위 정책을 인라인으로 연결
3. EC2 콘솔 → 인스턴스 선택 → 작업 → 보안 → IAM 역할 수정 → 방금
   만든 역할 연결
4. EC2에서 확인: `aws sts get-caller-identity`가 역할 ARN을 반환하면
   정상 연결된 것

## 6. 로그 수집 (CloudWatch Logs)

`docker-compose.cloudwatch.yml`이 5개 서비스 모두에 `awslogs` 로깅
드라이버를 지정한다(로그 그룹 `/quantlab/{서비스명}`). 이 오버레이는
`docker-compose.prod.yml` 본체에 넣지 않았다 - `awslogs` 드라이버는
컨테이너 기동 시점에 AWS 자격증명/리전을 요구해서, 로컬 검증
(`docker compose -f docker-compose.prod.yml build`, Phase 6에서 확인해둔
경로)에 섞이면 AWS 자격증명이 없는 로컬 환경에서 기동 자체가 실패한다.

1. 로그 그룹을 미리 만들고 보존 기간을 설정(자동 생성에 맡기면 기본
   "무기한 보존"이라 비용이 계속 쌓인다):
   ```bash
   for svc in mysql redis quant-engine backend frontend; do
     aws logs create-log-group --log-group-name "/quantlab/$svc" --region ap-northeast-2 || true
     aws logs put-retention-policy --log-group-name "/quantlab/$svc" --retention-in-days 14 --region ap-northeast-2
   done
   ```
2. §4의 `up -d` 명령을 오버레이 포함해서 실행하면(이미 그렇게 안내함)
   자동으로 적용된다.
3. 조회는 CloudWatch 콘솔의 Logs Insights, 또는:
   ```bash
   aws logs tail /quantlab/backend --follow --region ap-northeast-2
   ```

## 7. 모니터링 / 알림 (CloudWatch 커스텀 메트릭 + SNS)

CloudWatch Agent를 설치하는 대신, `scripts/report-health-metric.sh`가
5분마다 `/api/health` 상태(0/1)·메모리 사용률·디스크 사용률 3개를
`QuantLab` 네임스페이스로 전송한다(EC2 기본 제공 `StatusCheckFailed`는
에이전트 없이도 무료로 잡히므로 1차 방어선으로 그대로 활용). 커스텀
메트릭은 계정당 10개까지 상시 무료 - 이 3개는 비용이 들지 않는다.

1. cron 등록(헬스/리소스 지표 5분마다, MySQL 백업 매일 03:00 - §8과
   함께 한 번에 등록됨):
   ```bash
   ~/quantlab/scripts/install-cron.sh
   ```
2. SNS 알림 토픽 생성 + 이메일 구독:
   ```bash
   aws sns create-topic --name quantlab-alerts --region ap-northeast-2
   aws sns subscribe \
     --topic-arn arn:aws:sns:ap-northeast-2:<ACCOUNT_ID>:quantlab-alerts \
     --protocol email --notification-endpoint you@example.com
   # 수신 메일함에서 구독 확인 링크를 클릭해야 실제로 알림이 온다
   ```
3. 알람 등록(4종 - 인스턴스 상태·앱 헬스체크·디스크·메모리):
   ```bash
   TOPIC_ARN=arn:aws:sns:ap-northeast-2:<ACCOUNT_ID>:quantlab-alerts

   # EC2 자체가 죽은 경우(에이전트 불필요, 무료 기본 제공 지표)
   aws cloudwatch put-metric-alarm \
     --alarm-name quantlab-ec2-status-check-failed \
     --namespace AWS/EC2 --metric-name StatusCheckFailed \
     --statistic Maximum --period 300 --evaluation-periods 2 \
     --threshold 1 --comparison-operator GreaterThanOrEqualToThreshold \
     --dimensions Name=InstanceId,Value=<INSTANCE_ID> \
     --alarm-actions "$TOPIC_ARN"

   # 인스턴스는 살아있지만 앱(nginx→backend 경로)이 응답 안 하는 경우.
   # treat-missing-data를 breaching으로 둬야 "지표 자체가 안 옴"(cron이
   # 죽었거나 인스턴스가 응답 없음)도 정상(OK)이 아니라 알람으로 처리된다
   aws cloudwatch put-metric-alarm \
     --alarm-name quantlab-health-check-failed \
     --namespace QuantLab --metric-name HealthCheck \
     --statistic Minimum --period 300 --evaluation-periods 2 \
     --threshold 1 --comparison-operator LessThanThreshold \
     --treat-missing-data breaching \
     --alarm-actions "$TOPIC_ARN"

   # 디스크 90% 이상 - 로그/백업이 로컬에 계속 쌓이는 것을 조기 감지
   aws cloudwatch put-metric-alarm \
     --alarm-name quantlab-disk-high \
     --namespace QuantLab --metric-name DiskUtilization \
     --statistic Average --period 300 --evaluation-periods 1 \
     --threshold 90 --comparison-operator GreaterThanOrEqualToThreshold \
     --alarm-actions "$TOPIC_ARN"

   # 메모리 90% 이상
   aws cloudwatch put-metric-alarm \
     --alarm-name quantlab-memory-high \
     --namespace QuantLab --metric-name MemoryUtilization \
     --statistic Average --period 300 --evaluation-periods 2 \
     --threshold 90 --comparison-operator GreaterThanOrEqualToThreshold \
     --alarm-actions "$TOPIC_ARN"
   ```
4. 확인: `~/quantlab/scripts/report-health-metric.sh`를 수동으로 한 번
   실행한 뒤 `aws cloudwatch get-metric-data`나 콘솔에서 `QuantLab`
   네임스페이스에 값이 찍히는지 확인.

## 8. DB 백업 (S3)

`scripts/backup-mysql.sh`가 `quantlab-prod-mysql` 컨테이너 안에서
`mysqldump`(대상 DB만, `--all-databases` 아님)를 떠 gzip 압축 후 S3에
올린다. **Redis는 백업 대상이 아니다** - `PriceCacheStore`가 담는 건
시세 스냅샷/스코어 캐시뿐이라 유실돼도 다음 폴링 틱·배치에서 다시
채워지는 파생 데이터이고, 유일한 진실 소스는 MySQL뿐이다.

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
2. `.env.prod`에 `BACKUP_S3_BUCKET`(위에서 만든 버킷명) 기입 확인(§3)
3. cron 등록은 §7과 동일한 `scripts/install-cron.sh` 한 번으로 끝난다
   (매일 03:00 - 장중·16시 배치와 겹치지 않는 새벽 시간대)
4. 수동 1회 실행으로 확인:
   ```bash
   ~/quantlab/scripts/backup-mysql.sh
   aws s3 ls s3://<BACKUP_S3_BUCKET>/mysql/
   ```
5. **복구 절차**(백업 파일에서 MySQL로 되돌리기):
   ```bash
   # 로컬에 남아있는 백업(최근 3개)에서 복구
   gunzip -c ~/quantlab/backups/quantlab-mysql-<TIMESTAMP>.sql.gz \
     | docker exec -i -e MYSQL_PWD="$DB_PASSWORD" quantlab-prod-mysql mysql -u root quantlab

   # 또는 S3에서 직접 스트리밍 복구
   aws s3 cp s3://<BACKUP_S3_BUCKET>/mysql/quantlab-mysql-<TIMESTAMP>.sql.gz - \
     | gunzip \
     | docker exec -i -e MYSQL_PWD="$DB_PASSWORD" quantlab-prod-mysql mysql -u root quantlab
   ```

## 9. GitHub Actions 배포 시크릿

`.github/workflows/deploy.yml`이 참조하는 리포지토리 시크릿(Settings →
Secrets and variables → Actions):

| 시크릿 | 용도 |
|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP 또는 도메인(Elastic IP 고정 후 값 - §1) |
| `EC2_USER` | SSH 접속 계정(예: `ubuntu`) |
| `EC2_SSH_KEY` | EC2 접속용 프라이빗 키(PEM 전체 내용) |
| `VITE_GOOGLE_CLIENT_ID` / `VITE_KAKAO_CLIENT_ID` / `VITE_NAVER_CLIENT_ID` | 프론트 빌드 시 인라인되는 공개 OAuth 클라이언트 ID(시크릿 값 아니지만 저장소에 안 남기려고 시크릿으로 관리) |

`GITHUB_TOKEN`(GHCR 푸시용)은 워크플로에 자동 주입되므로 별도 등록 불필요.

이후 배포는 `git tag v1.0.0 && git push origin v1.0.0` 또는 Actions 탭에서
`Deploy` 워크플로를 수동 실행(`workflow_dispatch`)하면 된다. 배포
워크플로는 §6의 로그 수집 오버레이(`docker-compose.cloudwatch.yml`)를
포함해서 배포하므로, §5의 IAM 역할이 먼저 연결돼 있어야 한다.

## 10. OAuth 리다이렉트 URI 재등록

로컬 개발은 `http://localhost:3001/oauth/callback/{provider}`를 콘솔에
등록해뒀지만, 실제 배포 도메인에서는 각 프로바이더(구글/카카오/네이버)
콘솔에 **운영 도메인 기준 redirect URI**를 추가로 등록해야 한다(예:
`https://your-domain.example.com/oauth/callback/google`). 리다이렉트
URI는 프론트가 `window.location.origin` 기준으로 런타임에 동적 생성해
백엔드로 넘기므로 `.env.prod`에 별도 값을 맞출 필요는 없고, 프로바이더
콘솔 등록만 하면 된다. 이 콘솔 등록 작업은 각 계정 소유자가 직접
진행해야 하는 부분으로, 실제 OAuth 라운드트립 검증은 이 등록이 끝난
뒤에만 가능하다(CLAUDE.md "다음 작업"에 기록된 미완 항목).

## 11. TLS(다음 단계, 이번 범위 밖)

현재 구성은 HTTP(80)만 지원한다. 도메인이 연결되면 `certbot`으로 nginx에
TLS를 추가하는 것을 다음 단계로 남겨둔다(예: `certbot --nginx` 또는
`frontend` 컨테이너 앞에 별도 TLS 종단 프록시를 두는 방식). OAuth
프로바이더 상당수가 프로덕션 redirect URI에 HTTPS를 요구하므로, 실제
소셜 로그인 검증 전에 선행돼야 한다.

## 12. 배포 갱신 / 롤백

```bash
# 최신 배포로 갱신 (CI/CD가 자동으로 수행하는 것과 동일한 절차)
cd ~/quantlab && git pull
docker compose -f docker-compose.prod.yml -f docker-compose.cloudwatch.yml --env-file .env.prod pull
docker compose -f docker-compose.prod.yml -f docker-compose.cloudwatch.yml --env-file .env.prod up -d

# 특정 커밋 SHA로 롤백 (deploy.yml이 latest와 함께 :<sha> 태그도 푸시함)
docker pull ghcr.io/parkjuhan94/quantlab-backend:<sha>
docker tag ghcr.io/parkjuhan94/quantlab-backend:<sha> ghcr.io/parkjuhan94/quantlab-backend:latest
docker compose -f docker-compose.prod.yml -f docker-compose.cloudwatch.yml --env-file .env.prod up -d backend
```

## 13. 모니터링 스택 (Prometheus / Grafana / Alertmanager, Phase 1)

§6~§8의 CloudWatch 기반 구성(로그/헬스체크/리소스 3개 지표)과 별개로,
**앱 내부 지표**(JVM, HTTP 요청률/지연/에러율, Toss API 429 발생률, 전종목
시세 스윕 소요시간, 퀀트 엔진 호출 실패율 등)를 보기 위한 self-host
관측성 스택이다. `docker-compose.monitoring.yml`이 오버레이로 분리돼
있고, `docker-compose.cloudwatch.yml`과 달리 **AWS 자격증명이 필요 없어
로컬에서도 그대로 기동해 검증할 수 있다**(`docs/DEVELOPMENT.md` 참고).

구성 요소: Prometheus(지표 수집·저장, 15일 보존), Alertmanager(Slack
알림 라우팅), Grafana(대시보드 3종 - JVM/Spring HTTP/QuantLab 비즈니스
지표, `monitoring/grafana/` 프로비저닝으로 최초 기동 시 자동 로드),
node-exporter(호스트 리소스), cAdvisor(컨테이너별 리소스). 백엔드는
`spring-boot-starter-actuator` + Micrometer로 `/actuator/prometheus`를
노출하고(`backend/api/build.gradle`), quant-engine은
`prometheus-fastapi-instrumentator`로 `/metrics`를 노출한다
(`quant-engine/main.py`).

1. `.env.prod`에 아래 두 값을 채운다(`.env.prod.example` 참고):
   - `GRAFANA_ADMIN_PASSWORD` — Grafana 초기 관리자 비밀번호
   - `SLACK_ALERT_WEBHOOK_URL` — 알림 전용 Slack Incoming Webhook URL.
     **기존 `SLACK_FEEDBACK_WEBHOOK_URL`(사용자 피드백)과 다른 채널로
     새로 발급받을 것을 권장** - 인프라 알람과 사용자 피드백이 같은
     채널에 섞이면 노이즈가 커진다.
2. §4의 기동 명령에 `docker-compose.monitoring.yml`을 오버레이로 추가한다
   (다른 오버레이와 함께 여러 개를 동시에 지정할 수 있다):
   ```bash
   docker compose -f docker-compose.prod.yml -f docker-compose.cloudwatch.yml \
     -f docker-compose.monitoring.yml --env-file .env.prod up -d --build
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
4. Grafana 접속 후 좌측 `Dashboards → QuantLab` 폴더에 3개 대시보드가
   프로비저닝돼 있는지 확인(JVM, Spring Boot HTTP, 비즈니스 지표).
   데이터소스(Prometheus)도 프로비저닝으로 자동 등록된다 - 수동 설정 불필요.
5. 알림 규칙은 `monitoring/prometheus/rules/alerts.yml`에 코드로 관리한다
   (BackendDown, QuantEngineDown, HighHttp5xxRate, TossRateLimitSpike,
   QuantEngineFailureRate, JvmHeapHigh, HostMemoryHigh, HostDiskHigh).
   Alertmanager UI(`http://localhost:9093`, 터널 경유)에서 발화 중인
   알림/Silence 상태를 확인할 수 있다.
6. 리소스 영향: 이 스택은 EC2에 **약 1GB의 추가 RAM**을 요구한다(Prometheus
   +Grafana+Alertmanager+node-exporter+cAdvisor 합산). §1에서 t3.small을
   권장했는데, 이 스택까지 상시 가동하려면 **t3.medium 이상으로 승격을
   권장**한다. 로컬 검증에는 이 제약이 적용되지 않는다.
7. Phase 2(로그, Loki)·Phase 3(트레이스, Tempo)는 아직 미착수 - CLAUDE.md
   작업 기록 참고.
