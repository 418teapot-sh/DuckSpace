# DuckSpace — 백엔드 작업 규칙

덕질 굿즈 수집·전시·교환 플랫폼. Spring Boot 단일 서버입니다.
백엔드 3명이 도메인을 나눠 **병렬로** 작업하므로, 아래 규칙은 대부분 충돌을 줄이기 위한 것입니다.

---

## ⚠️ Spring Boot 4 주의사항 (가장 자주 틀리는 부분)

Spring Boot **4.1** / Java 21입니다. Boot 3.x와 다른 점이 있어서 습관대로 쓰면 컴파일이 깨집니다.

- **Jackson 3**을 씁니다. `com.fasterxml.jackson.*` 이 아니라 **`tools.jackson.*`** 입니다.
  예) `tools.jackson.databind.ObjectMapper`
- 웹 스타터는 `spring-boot-starter-web` 이 아니라 **`spring-boot-starter-webmvc`** 입니다.
- 테스트 스타터가 `spring-boot-starter-test` 하나가 아니라 모듈별로 나뉘어 있습니다.
  (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test` 등)
- **테스트 슬라이스 어노테이션의 패키지가 전부 바뀌었습니다.** `org.springframework.boot.test.autoconfigure.*`
  로 임포트하면 "package does not exist" 가 납니다. `@DataJpaTest` 와 `TestEntityManager` 는
  서로 다른 패키지에 있으니 특히 주의하세요.
  ```java
  import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
  import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
  ```
  `@DataJpaTest` 는 `@Configuration` 을 스캔하지 않아서 Auditing 이 안 걸립니다.
  `createdAt` 이 not null 이므로 `@Import(JpaAuditingConfig.class)` 를 같이 붙여야 합니다.
- 서드파티 라이브러리를 추가할 때는 Boot 4 호환 여부를 먼저 확인하세요. 아직 대응 안 된 것이 있습니다.

---

## 로컬 실행

DB는 **MySQL**입니다. Docker로 띄운 뒤 앱을 실행하세요.

```bash
docker compose up -d     # MySQL 8 (localhost:3306, DB/계정/비번 모두 duckspace)
./gradlew bootRun        # 기본 프로필 local
```

- 스키마가 꼬였을 때: `docker compose down -v && docker compose up -d` (**데이터 전부 삭제**됩니다)
- **테스트도 MySQL로 돕니다.** `docker compose up -d`를 먼저 하지 않으면 `./gradlew test`가 실패합니다.
  `docker-compose.yml`이 개발용 `duckspace`와 테스트용 `duckspace_test` DB를 함께 만듭니다.
  H2를 쓰지 않는 이유는 **운영(MySQL)과 미묘하게 다르게 동작하는 지점** 때문입니다 —
  실제로 enum을 varchar로 매핑할 때 H2가 잘못된 check 제약을 만들어 INSERT가 전부 실패한 적이 있습니다.
- ⚠️ **`Unknown database 'duckspace_test'` 가 나면** 이 설정이 생기기 전에 이미 `docker compose up -d`를
  돌린 경우입니다. 초기화 스크립트는 **MySQL 볼륨이 비어 있을 때만** 실행되기 때문입니다.
  볼륨을 날리지 않고 고치려면:
  ```bash
  docker compose exec -T mysql mysql -uroot -pduckspace < docker/mysql-init/README-local-mysql.sql
  ```
- Swagger: http://localhost:8080/swagger-ui.html (우측 상단 Authorize에 액세스 토큰만 붙여넣기)

---

## 패키지 구조

> ⚠️ **논의 중입니다.** 확정되면 이 섹션을 갱신하세요. 아래는 현재 기본안입니다.

```
com.duckspace
├── global/                  공통 인프라 (구현 완료 — 상의 없이 바꾸지 마세요)
│   ├── auth/                JwtTokenProvider, JwtAuthenticationFilter, AuthUser
│   ├── config/              Security, CORS, Swagger, JpaAuditing
│   ├── entity/              BaseTimeEntity
│   ├── exception/           BaseErrorCode, BusinessException, GlobalExceptionHandler
│   ├── filter/              TraceIdFilter
│   └── response/            ApiResponse
└── domain/
    └── <도메인>/
        ├── controller/
        ├── service/
        ├── repository/
        ├── entity/
        ├── dto/             request/, response/
        └── exception/       <도메인>ErrorCode
```

**도메인 폴더는 담당자가 소유합니다.** 남의 도메인 폴더를 수정해야 하면 먼저 물어보세요.

---

## 필수 컨벤션

### 응답은 항상 `ApiResponse`

```java
return ApiResponse.success(data);   // 데이터 있음
return ApiResponse.noContent();     // 데이터 없음 (DELETE 등)
```

`ResponseEntity`를 직접 반환하지 마세요. 프론트가 `{ success, data, error, traceId }` 형태를 기대합니다.

### 에러코드는 도메인별로 만듭니다

**`GlobalErrorCode`에 도메인 에러를 추가하지 마세요.** 3명이 같은 파일을 건드리면 merge 충돌이 계속 납니다.
대신 도메인 패키지에 `BaseErrorCode`를 구현하는 enum을 각자 만듭니다.

```java
@Getter
public enum CatalogErrorCode implements BaseErrorCode {
    SERIES_NOT_FOUND(HttpStatus.NOT_FOUND, "시리즈를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    CatalogErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}

// 사용
throw new BusinessException(CatalogErrorCode.SERIES_NOT_FOUND);
```

`GlobalExceptionHandler`가 자동으로 잡아서 `ApiResponse` 형식으로 변환합니다.

### 로그인 유저 꺼내기

```java
@GetMapping("/me")
public ApiResponse<MyResponse> me(@AuthenticationPrincipal AuthUser authUser) {
    Long userId = authUser.getUserId();
}
```

JWT에서 `userId`만 꺼내므로 DB 조회가 없습니다. User 엔티티가 필요하면 직접 조회하세요.

### 엔티티

- `BaseTimeEntity`를 상속하면 `createdAt` / `updatedAt`이 자동으로 채워집니다.
- 인증 없이 접근할 API는 `SecurityConfig.PUBLIC_ENDPOINTS`에 추가해야 합니다. **기본은 전부 인증 필요**입니다.

---

## Git 컨벤션

1. **이슈를 먼저 생성**하고, 브랜치 이름에 이슈 번호를 넣습니다.
2. 브랜치: `<타입>/<이슈번호>-<슬러그>` — 예) `feat/12-catalog-api`
3. 커밋·PR 제목: `[Feat]: `, `[Chore]: `, `[Refactor]: `, `[Fix]: `
4. PR 템플릿을 채우고 **Label과 Assignee를 지정**합니다.
5. `main`에 직접 커밋하지 마세요.

---

## 하지 말 것

- `.env`, API 키, `*.pem` 커밋 — `.gitignore`에 패턴이 있지만 파일명이 다르면 그대로 올라갑니다
- `GlobalErrorCode`에 도메인 에러 추가
- `global/` 패키지를 상의 없이 수정
- `application.yml`에 시크릿 **기본값** 넣기 — dev/prod는 환경변수로 주입합니다 (로컬 값은 `application-local.yml`에)

---

## 알아둘 것

- `dev` 프로필은 `JWT_SECRET`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수가 필요합니다.
  없으면 **부팅이 실패합니다** — 시크릿이 기본값으로 조용히 노출되는 것을 막기 위한 의도된 동작입니다.
- **외부 API 키는 프로젝트 루트 `.env`에 넣습니다.** `application.yml`이 `spring.config.import`로
  읽고, `.gitignore`에 막혀 있습니다. 없어도 부팅은 됩니다(해당 기능만 비활성).
  ```
  REMOVEBG_API_KEYS=key1,key2,...
  OPENAI_API_KEY=...
  ```
  `JWT_SECRET`처럼 없으면 부팅이 실패해야 하는 값과 달리, 이건 **키가 없는 팀원도 나머지 기능을
  개발할 수 있어야 해서** 빈 기본값을 둡니다. dev/prod는 `/etc/duckspace/app.env`를 씁니다.
- **remove.bg는 계정당 무료 호출이 월 50회, 0.25MP(preview)로 제한**됩니다. `size=auto`나 `full`은
  크레딧을 소모하므로 무료 플랜에서는 실패합니다. 개발 중 반복 테스트에는 API를 쓰지 마세요.
  서로 다른 계정에서 발급받은 키를 콤마로 여러 개 넣으면(`REMOVEBG_API_KEYS`), 하나가 크레딧을
  소진(402)하거나 무효(403)해질 때 자동으로 다음 키로 전환됩니다(`RemoveBgClient`).
  `REMOVEBG_API_KEY`(단수)는 예전 이름이며 폴백으로만 남아있으니 새로 설정할 땐 쓰지 마세요.
- 리프레시 토큰 저장소가 아직 없습니다. 로그아웃·재발급을 구현하려면 테이블이 필요합니다.
- 소셜 로그인(카카오)은 현재 구현 범위가 아닙니다. **폼 로그인**으로 진행합니다.
  나중에 붙일 수 있도록 `User`에 `provider`, `provider_id`를 nullable로 열어두기로 했습니다.
- 비밀번호를 저장하려면 `PasswordEncoder` 빈이 필요한데 아직 등록되어 있지 않습니다.
