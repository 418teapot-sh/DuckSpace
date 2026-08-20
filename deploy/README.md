# 배포 서버(EC2) 설정

`.github/workflows/deploy.yml`이 성공하려면 EC2 인스턴스에 아래가 미리 준비되어 있어야 합니다.
인스턴스가 새로 뜨거나 담당자가 없을 때 복구할 수 있도록 여기에 정리합니다.

## 1. systemd 서비스

최초 1회만 아래로 설치하면, 그 다음부터는 **매 배포마다 `deploy.yml`이 리포의
[`duckspace.service`](./duckspace.service) 내용을 그대로 서버에 다시 반영**합니다
(`sudo cp` + `daemon-reload`). 그래서 이 파일을 고쳐놓고 서버에는 안 퍼진 채로 배포가
"성공"해버리는 사고(예전에 jar 파일명 바꿨을 때 겪음 — jar는 최신인데 ExecStart 는 옛날
파일명을 계속 가리켜서 실제로는 옛날 jar 가 재시작되던 문제)가 이제 안 남습니다.

```bash
sudo cp deploy/duckspace.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable duckspace   # 이건 배포마다 다시 할 필요 없음 — 재부팅 시 자동 시작 설정
```

- jar 경로: `/home/ubuntu/DuckSpace/build/libs/duckspace.jar`
  (파일명이 `build.gradle`의 `bootJar.archiveFileName`으로 고정되어 있어 `version`이 바뀌어도
  안 바뀜 — 배포 스크립트·systemd가 전부 이 경로를 고정값으로 참조하기 때문)
  - deploy.yml이 새 jar를 `build/libs/staging/`에 먼저 올린 뒤 검증·백업까지 마치고 이 경로로
    원자적으로 교체함(`mv`). `build/libs/staging/`과 `deploy/`(유닛 파일 업로드용) 디렉터리는
    `deploy.yml`의 "Ensure remote directories exist" 스텝이 매번 `mkdir -p`로 알아서 만드므로
    수동으로 미리 만들어둘 필요 없음.
  - jar 무결성 검증에 `python3 -m zipfile -t`를 씀. AWS 기본 Ubuntu AMI는 cloud-init 구동에
    python3 이 필요해 기본 포함되어 있음 — 다른 AMI로 바꾸면 이 전제가 깨질 수 있음.
- `--spring.profiles.active=dev`를 커맨드라인 인자로 명시함. 이게 빠지면 `application.yml`의
  `spring.profiles.default: local`이 적용되어 `application-local.yml`에 하드코딩된
  로컬 전용 JWT 시크릿으로 뜨는 보안 사고가 날 수 있음 — **절대 빼면 안 됨.**

## 2. 환경변수 (`/etc/duckspace/app.env`)

`EnvironmentFile=/etc/duckspace/app.env`로 주입. `dev` 프로필은 아래가 없으면 부팅이 실패함
(의도된 동작 — 시크릿 기본값 노출 방지).

```
DB_URL=...
DB_USERNAME=...
DB_PASSWORD=...
JWT_SECRET=...
OPENAI_API_KEY=...        # 아직 빈 값이어도 됨
REMOVEBG_API_KEYS=key1,key2,key3,key4,key5,key6   # 아직 빈 값이어도 됨 (콤마 구분, 서로 다른 계정 키)
```

`REMOVEBG_API_KEYS`(복수)가 예전 이름 `REMOVEBG_API_KEY`(단수)를 대체합니다. 옛날 이름만
설정돼 있으면 부팅은 정상적으로 되지만 배경 제거가 조용히 꺼진 채로 200을 응답합니다 —
굿즈 사진을 직접 열어보기 전까지 티가 안 나니, 이 서버에 이미 `REMOVEBG_API_KEY`가 설정돼
있었다면 `REMOVEBG_API_KEYS`로 이름을 옮겨주세요.

## 3. nginx 리버스 프록시 + HTTPS

[`nginx-duckspace.conf`](./nginx-duckspace.conf)를 설치하고 Certbot으로 인증서를 발급합니다.

```bash
sudo cp deploy/nginx-duckspace.conf /etc/nginx/sites-available/duckspace
sudo ln -s /etc/nginx/sites-available/duckspace /etc/nginx/sites-enabled/duckspace
sudo certbot --nginx -d duckspace.cloud -d www.duckspace.cloud
sudo nginx -t && sudo systemctl reload nginx
```

## 4. GitHub Actions가 SSH로 `systemctl restart`를 실행할 권한

이 EC2는 AWS 기본 Ubuntu AMI라 `/etc/sudoers.d/90-cloud-init-users`(cloud-init 기본 생성 파일)에
`ubuntu ALL=(ALL) NOPASSWD:ALL`이 이미 들어 있어서, 별도 sudoers 설정을 추가하지 않았어도
`deploy.yml`의 `sudo systemctl restart duckspace`가 비밀번호 없이 동작합니다.

> **참고:** 이건 `systemctl restart duckspace` 하나만 허용한 게 아니라 `ubuntu` 유저 전체에 대한
> 무제한 sudo입니다. 지금은 AMI 기본값을 그대로 쓰는 상태이며, 범위를 좁힌 별도 sudoers 규칙은
> 없습니다. 인스턴스를 새로 만들 때도 같은 AMI라면 자동으로 따라오지만, 다른 AMI로 바꾸면
> 이 전제가 깨질 수 있습니다.

## 5. GitHub Secrets (Repository → Settings → Secrets and variables → Actions)

| Secret | 용도 |
|---|---|
| `EC2_HOST` | 탄력적 IP (`3.35.142.38`) |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | EC2 접속용 PEM 프라이빗 키 |

## 6. 디스크/메모리 (t3.micro)

기본 8GiB 디스크로는 부족해서 20GiB로 확장(`growpart` + `resize2fs`), 2GiB 스왑을 추가해뒀습니다.
빌드는 GitHub Actions 러너에서 하고 EC2에는 jar만 전달하므로, 서버에서 직접 `./gradlew`를
실행하지 않는 한 이 여유분으로 충분합니다.

## 7. 지금 서버에 어떤 커밋이 떠 있는지 확인하기

헬스체크를 통과한 배포마다 `/home/ubuntu/DuckSpace/CURRENT_SHA`에 커밋 SHA를 기록합니다.

```bash
cat /home/ubuntu/DuckSpace/CURRENT_SHA
```

롤백(헬스체크 실패로 이전 jar로 되돌아간 경우)이 일어나면 이 파일은 갱신되지 않으므로,
직전 성공 배포의 SHA로 남아있습니다.

## 8. 수동 스키마 마이그레이션이 필요한 배포

Flyway/Liquibase가 없고 `ddl-auto: update`만 씁니다. `update`는 **컬럼 삭제·이름 변경·NOT NULL
해제를 반영하지 않으므로**, 이런 변경이 담긴 PR을 배포할 땐 앱 재시작 전(또는 직후, 트래픽 없는
타이밍)에 서버 MySQL에 아래 SQL을 수동으로 실행해야 합니다. 안 하면 그 컬럼이 걸린 INSERT/UPDATE가
전부 실패합니다.

**대기중인 마이그레이션:** 없음.

(2026-08-20 완료: `banner.popup_id` NOT NULL 해제, 프로덕션 실행 완료 — PR #109, 이슈 #108,
순수 광고 배너(팝업 미연결) 허용. `ALTER TABLE banner MODIFY COLUMN popup_id BIGINT NULL;`
실행 전엔 `popupId` 없이 배너를 만들면 `Column 'popup_id' cannot be null`로 500이 났음
(RYU-TOMI가 PR #109 리뷰에서 실제로 재현·발견). 로컬 DB는 각자 필요할 때 반영.)

(2026-08-17 완료: `exchange_detail.method` 컬럼 삭제, 로컬/프로덕션 둘 다 실행 완료 —
`ALTER TABLE exchange_detail DROP COLUMN method;`. 실행 전엔 `POST /api/posts/exchange`가
매번 `Field 'method' doesn't have a default value`로 실패했음.)

새로 스키마를 바꾸는 PR이 머지될 때마다 이 섹션에 다음 항목을 추가하는 걸 관례로 합니다.
