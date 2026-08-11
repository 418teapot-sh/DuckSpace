# 배포 서버(EC2) 설정

`.github/workflows/deploy.yml`이 성공하려면 EC2 인스턴스에 아래가 미리 준비되어 있어야 합니다.
인스턴스가 새로 뜨거나 담당자가 없을 때 복구할 수 있도록 여기에 정리합니다.

## 1. systemd 서비스

[`duckspace.service`](./duckspace.service)를 그대로 설치합니다.

```bash
sudo cp deploy/duckspace.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable duckspace
```

- jar 경로: `/home/ubuntu/DuckSpace/build/libs/duckspace-0.0.1-SNAPSHOT.jar`
  (deploy.yml이 scp로 이 경로에 덮어씀 — 디렉터리는 미리 만들어져 있어야 함: `mkdir -p /home/ubuntu/DuckSpace/build/libs`)
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
OPENAI_API_KEY=...   # 아직 빈 값이어도 됨
```

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
