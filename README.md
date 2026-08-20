# 🐥 DuckSpace (덕스페이스)

> **덕질 굿즈를 수집·전시·교환하는 팬덤 공간 플랫폼의 백엔드 API 서버입니다.**
> 굿즈 사진의 배경을 자동 제거해 나만의 "장식장"에 자유롭게 배치하고, 다른 유저와 굿즈를 교환하거나 이야기를 나눌 수 있습니다.

* **🌐 Service Link**: [https://duckspace.cloud](https://duckspace.cloud)
* **📜 API Document (Swagger)**: [https://duckspace.cloud/swagger-ui.html](https://duckspace.cloud/swagger-ui.html)

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

* **자유 배치 CRUD**: 굿즈 배치 및 대표 장식장 조회 (커서 기반 페이지네이션)
* **배경 제거 파이프라인**: 이미지 업로드 시 `remove.bg API`를 통해 비동기로 배경을 자동 제거 후 보관함에 저장

**💬 덕톡라운지 & 채팅**

* **게시판**: 잡담 및 교환 게시글 CRUD, 댓글, 신고 기능
* **교환 매칭**: 교환 신청 프로세스 (수락 / 거절 / 완료)
* **1:1 채팅**: 1:1 대화방 생성 및 메시지 주고받기 (폴링 방식, 커서 기반 대화 내역 조회)

**🏪 팝업스토어 & 유저**

* **팝업스토어**: 팝업 상세 정보 및 찜(좋아요), 관리자용 팝업 등록/수정/삭제
* **유저 & 팔로우**: 프로필 관리, 유저 검색(최근 검색어 제공), 팔로우/팔로워 시스템

---

## 🛠 Tech Stack

* **Language & Framework**: Java 21, Spring Boot 4.1 (`spring-boot-starter-webmvc`, Jackson 3)
* **Security & Auth**: Spring Security, JWT (Access / Refresh Token)
* **Database & Persistence**: Spring Data JPA, MySQL 8
* **External API**: AWS S3, remove.bg API, OpenAI API
* **API Documentation**: springdoc-openapi (Swagger)
* **DevOps**: GitHub Actions, AWS EC2, Nginx, Let's Encrypt, systemd

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
│   └── response/              # ApiResponse
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

로컬 테스트 환경 작동을 위해 **MySQL 컨테이너**가 미리 구동되어 있어야 합니다.

```bash
# 1. 개발(duckspace) 및 테스트(duckspace_test) DB 동시 생성
docker compose up -d

# 2. 로컬 서버 실행 (기본 프로필: local)
./gradlew bootRun
```

> **Swagger UI 확인**: `http://localhost:8080/swagger-ui.html`

### 2. Environment Variables (.env)

프로젝트 루트 디렉토리에 `.env` 파일을 생성하고 필요한 키를 작성해 주세요.

*(API 키가 없어도 서버 부팅은 가능하나, 배경 제거 및 관련 AI 기능만 제한됩니다.)*

```env
REMOVEBG_API_KEYS=your_key_1,your_key_2
OPENAI_API_KEY=your_openai_key
```

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
