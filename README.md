# 🐥 DuckSpace (덕스페이스)

> **덕질 굿즈를 수집·전시·교환하는 팬덤 공간 플랫폼의 백엔드 API 서버입니다.**
> 굿즈 사진의 배경을 자동 제거해 나만의 "장식장"에 자유롭게 배치하고, 다른 유저와 굿즈를 교환하거나 이야기를 나눌 수 있습니다.

* **🌐 Service Link**: [https://duckspace.cloud](https://duckspace.cloud) *(백엔드 API 서버)*
* **🖥️ Frontend**: [duck-space-frontend.vercel.app](https://duck-space-frontend.vercel.app) · [repo](https://github.com/Johminseo/DuckSpace-Frontend)
* **📜 API Document (Swagger)**: [https://duckspace.cloud/swagger-ui.html](https://duckspace.cloud/swagger-ui.html)

> ⚠️ 배포 환경의 Swagger 는 운영/시연 시점에 **비공개로 전환**될 수 있습니다.
> 로컬에서는 `http://localhost:8080/swagger-ui.html` 로 항상 열려 있습니다.

---

## 👥 Team & Role

백엔드 개발자 3인이 도메인을 분담하여 병렬 개발을 진행했습니다.

*(공통 인프라: 인증 필터, 전역 예외 처리, 공통 응답 포맷, CI/CD 배포 파이프라인)*

| 담당자 | 주요 구현 도메인 |
| --- | --- |
| **[@RYU-TOMI](https://github.com/RYU-TOMI)** | **전시(장식장)** 배치 CRUD, 굿즈 이미지 파이프라인, **덕톡 1:1 채팅** |
| **[@418teapot-sh](https://github.com/418teapot-sh)** | **덕톡라운지 게시판**(잡담/교환), 유저·팔로우, **JWT 인증**, **배포 인프라** |
| **[@Yun-pix](https://github.com/Yun-pix)** | **팝업스토어**, 유저 프로필, 배너, 홈 피드 |

---

## ✨ Key Features

**🖼️ 전시 (장식장)**

* **자유 배치 CRUD**: 굿즈를 비율 좌표(0~1)와 회전 각도로 배치. 대표 장식장은 단건 조회
* **커서 기반 페이지네이션**: 장식장 피드 · 내 장식장 목록 · 유저별 장식장 목록 · 굿즈 검색
* **배경 제거 파이프라인**: 사진 업로드 시 `remove.bg API` 로 **비동기** 배경 제거 → 384px PNG 로 정규화 후 저장. 보관함(`POST /api/images`)과 장식장 직접 업로드(`POST /api/exhibitions/{id}/items/upload`) 두 경로가 같은 파이프라인을 씁니다

**💬 덕톡라운지 & 채팅**

* **게시판**: 잡담 및 교환 게시글 CRUD, 댓글, 신고 기능
* **교환 매칭**: 교환 신청 프로세스 (수락 / 거절 / 완료)
* **1:1 채팅**: 1:1 대화방 생성 및 메시지 주고받기 (폴링 방식, 커서 기반 대화 내역 조회)

**🏪 팝업스토어 & 유저**

* **팝업스토어**: 팝업 상세 정보 및 찜(좋아요), 관리자용 팝업 등록/수정/삭제
* **AI 요약**: 팝업/배너 등록·수정 시 OpenAI로 일정·홍보 문구를 자동 요약해 저장 (`OpenAiSummaryClient`, `BannerSummaryClient`)
* **유저 & 팔로우**: 프로필 관리, 유저 검색(최근 검색어 제공), 팔로우/팔로워 시스템

---

## 🛠 Tech Stack

* **Language & Framework**: Java 21, Spring Boot 4.1 (`spring-boot-starter-webmvc`, Jackson 3)
* **Build & Tooling**: Gradle, Lombok
* **Security & Auth**: Spring Security, JWT (Access / Refresh Token) — `jjwt`
* **Database & Persistence**: Spring Data JPA, MySQL 8
* **External API**: AWS S3(AWS SDK), remove.bg · OpenAI (별도 SDK 없이 JDK `HttpClient` 직접 호출)
* **API Documentation**: springdoc-openapi (Swagger)
* **DevOps**: GitHub Actions, AWS EC2, Nginx, Let's Encrypt, systemd

> Spring Boot **4.x** 라 3.x 와 다른 점이 있습니다 — Jackson 은 `tools.jackson.*`(Jackson 3),
> 웹 스타터는 `spring-boot-starter-webmvc`, 테스트 슬라이스 어노테이션도 패키지가 바뀌었습니다.
> 서드파티 라이브러리를 추가할 때는 Boot 4 호환 여부를 먼저 확인해 주세요.

---

## 🏗 Architecture & Code Structure

### Directory Structure

```text
com.duckspace
├── global/                  # 공통 인프라
│   ├── auth/                # JwtTokenProvider, JwtAuthenticationFilter, AuthUser
│   ├── config/               # Security, CORS, Swagger, JpaAuditing
│   ├── entity/               # BaseTimeEntity
│   ├── exception/            # BaseErrorCode, BusinessException, GlobalExceptionHandler
│   ├── filter/                # TraceIdFilter
│   ├── response/              # ApiResponse
│   └── support/               # Paging(커서 페이징), LikeEscaper, ServiceZone, openai/
└── domain/                  # 비즈니스 도메인
    └── <domain>/
        ├── controller/
        ├── service/
        ├── repository/
        ├── entity/
        ├── dto/               # request/, response/
        └── exception/          # <domain>ErrorCode
```

### Common API Response Format

모든 API 응답은 통합 규격인 `ApiResponse` 객체를 통해 일관되게 반환됩니다.

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "traceId": "a1b2c3d4-..."
}
```

---

## 🚀 Getting Started (Local)

### 1. Prerequisite

로컬 실행과 **테스트 모두** MySQL 이 필요합니다.

```bash
# 1. 개발(duckspace) 및 테스트(duckspace_test) DB 동시 생성
docker compose up -d

# 2. 로컬 서버 실행 (기본 프로필: local)
./gradlew bootRun
```

> **Swagger UI 확인**: `http://localhost:8080/swagger-ui.html`

**`./gradlew test` 도 MySQL 로 돕니다.** `docker compose up -d` 를 먼저 하지 않으면 테스트가 전부 실패합니다.
H2 를 쓰지 않는 이유는 **운영(MySQL)과 미묘하게 다르게 동작하는 지점** 때문입니다 — 실제로 enum 을
varchar 로 매핑할 때 H2 가 잘못된 check 제약을 만들어 INSERT 가 전부 실패한 적이 있습니다.

<details>
<summary>Docker 없이 로컬에 설치된 MySQL 을 쓰는 경우</summary>

`docker/mysql-init/README-local-mysql.sql` 에 DB·계정 생성 SQL 이 있습니다.

```bash
mysql -uroot -p < docker/mysql-init/README-local-mysql.sql
```

`duckspace` 와 `duckspace_test` 두 DB 가 만들어집니다. 이미 `docker compose up -d` 를 돌린 뒤라면
초기화 스크립트가 **볼륨이 비어 있을 때만** 실행되므로, `Unknown database 'duckspace_test'` 가 날 때도
같은 SQL 을 직접 넣어주면 됩니다.

</details>

### 2. Environment Variables (.env)

프로젝트 루트 디렉토리에 `.env` 파일을 생성하고 필요한 키를 작성해 주세요.

*(API 키가 없어도 서버 부팅은 가능하나, 배경 제거 및 관련 AI 기능만 제한됩니다.)*

```env
REMOVEBG_API_KEYS=your_key_1,your_key_2
OPENAI_API_KEY=your_openai_key
```

> ⚠️ **remove.bg 무료 플랜은 계정당 월 50회, `preview`(0.25MP) 전용입니다.**
> 키를 넣은 상태로 사진 업로드를 누르면 **진짜 크레딧이 나갑니다.** 반복 테스트가 필요하면
> 키를 비워두세요 — 키가 없으면 배경 제거만 건너뛰고 원본이 그대로 저장됩니다.
> 서로 다른 계정의 키를 콤마로 여러 개 넣으면 하나가 소진(402)될 때 다음 키로 자동 전환됩니다.

---

## 🔄 CI/CD & Deployment

* **자동 배포**: `main` 브랜치 PR 머지 시 GitHub Actions를 통해 AWS EC2로 자동 배포됩니다.
* **안전성 유지**: 헬스체크(`actuator/health`)에 실패하는 경우 자동으로 **이전 버전으로 롤백**됩니다.
* 자세한 서버 설정(systemd, Nginx, SSL)은 [`deploy/README.md`](./deploy/README.md)에서 확인하실 수 있습니다.

---

## 🤙 Git & Commit Convention

1. **Branch Rule**: 이슈 생성 후 이슈 번호를 포함하여 브랜치를 작성합니다.
   * Format: `<Type>/<Issue-Number>-<Slug>` (예: `feat/12-catalog-api`)

2. **Commit / PR Title Prefix**:
   * `[Feat]:` 새로운 기능 추가
   * `[Fix]:` 버그 수정
   * `[Refactor]:` 코드 리팩토링
   * `[Chore]:` 빌드 업무, 패키지 매니저 설정 등

3. **PR Process**: PR 템플릿(Issue / As-Is / To-Be)을 작성하고 Label 및 Assignee 지정 후 리뷰를 진행합니다. (`main` 브랜치 직접 커밋 금지)
