# 배치 A — `global/**` 공용 인프라 코드 리뷰

> 대상: `src/main/java/com/duckspace/global/**` 21개 파일 885 LOC (기준 커밋 `f1fbc06`)
> 
> ⚠️ 이후 `2449c02`(#55 팝업 찜)가 머지되면서 **A-07 의 팝업 부분이 해결**됐습니다. 해당 항목에 표시해 뒀습니다.
> 관련 이슈: #59

`global/` 은 프로젝트에서 **리뷰 이력이 한 번도 없던 유일한 영역**이라 배치 A로 잡았습니다.
아래 20건은 전부 코드로 재확인했습니다. **수정은 하지 않았습니다** — `global/` 은 상의 없이
건드리지 않는 게 규칙이라, 판단과 수정은 담당자 몫으로 남겨둡니다.

항목마다 인라인 코멘트로 **반박 / 동의 / 담당 배정**을 남겨주세요.
동의가 안 되는 항목은 그대로 닫아도 됩니다 — 근거를 적어뒀으니 판단만 해주시면 됩니다.

---

## 현재 상태 (2026-08-20 기준)

**아래 본문은 `2449c02` 시점의 스냅샷입니다.** 그 뒤로 대부분 처리됐으니, 지금 뭐가 남았는지는 이 표로 보세요.

| # | 상태 | 처리 |
|---|---|---|
| A-01 Swagger 배포 공개 | ⏸ 보류 | **시연 직전 전환.** 프론트 요청(A안)으로 그때까지 열어둡니다 |
| A-02 `/actuator/**` 와일드카드 | ✅ | #91 — `health` · `info` 로 축소 |
| A-03 OpenAI 타임아웃 | ✅ | #88 |
| A-04 JWT 필터 예외 500 HTML | ✅ | #79 |
| A-05 5xx 원인 미로깅 | ✅ | #79 |
| A-06 테스트가 외부 API 호출 | ✅ | #88 |
| A-07 메서드 무관 공개 경로 | ✅ | #80 (팝업 쪽은 #55 가 먼저 해결) |
| A-08 공개 `/api/popups` == ADMIN 목록 | ⏸ 보류 | **담당자 의도 확인 필요.** 기본값은 유지 |
| A-09 커밋된 JWT 시크릿 | ✅ | #95 — 비 `local` 프로필에서 부팅 차단 |
| A-10 404/405 가 400 으로 | ✅ | #79 |
| A-11 용량 초과 업로드 | ✅ | #79 — 413 `IMAGE_TOO_LARGE` |
| A-12 검증 에러 중복 소실 | ✅ | #79 |
| A-13 `X-Trace-Id` CORS | ✅ | #80 |
| A-14 traceId 8자 | ✅ | #80 |
| A-15 JWT 4회 파싱 | ✅ | #80 |
| A-16 필터 이중 등록 | ✅ | #80 |
| A-17 ~ A-20 소소한 것 | ✅ | #79 |

**20건 중 18건 해결, 2건 보류.**

> **✅ 해결** · **🔶 절반** · **⏸ 보류**(이유 있음) · **➖ 조치 없음**(결정 또는 재현 불가) · **❌ 미해결**

---

## 🔴 배포 서버에 지금 열려 있는 것

### A-01. Swagger·OpenAPI 문서가 배포 서버에 공개돼 있습니다

**위치** `config/SecurityConfig.java:43-45`

```java
"/swagger-ui/**",
"/swagger-ui.html",
"/v3/api-docs/**",
```

`PUBLIC_ENDPOINTS` 에 프로필 구분 없이 들어가 있습니다. springdoc 이 `implementation`
의존성이라(`build.gradle:44`) 배포 jar 에 포함되고, 배포는 `--spring.profiles.active=dev`
로 뜹니다(`deploy/duckspace.service:11`).

**영향** `https://duckspace.cloud/v3/api-docs` 를 누구나 받을 수 있습니다.
`/api/admin/**` 를 포함한 전체 라우트와 요청 본문 스키마가 그대로 노출됩니다.
엔드포인트를 숨기는 게 보안의 전부는 아니지만, 공격 표면을 통째로 알려줄 이유는 없습니다.

**제안** `application-dev.yml` 에 `springdoc.api-docs.enabled: false` /
`springdoc.swagger-ui.enabled: false` 를 두거나, 세 매처를 `local` 프로필에서만 등록.

---

### A-02. `/actuator/**` 가 와일드카드로 열려 있습니다

**위치** `config/SecurityConfig.java:42`

actuator 가 classpath 에 있고(`build.gradle:21`) `management.*` 설정이 어느 프로필에도
없어서, **오늘 웹으로 노출되는 건 `/actuator/health` 하나뿐입니다.** 그래서 지금 당장
새는 정보는 없습니다.

**영향** 문제는 매처가 개별 엔드포인트가 아니라 `**` 라는 점입니다. 메트릭을 붙이려고
`management.endpoints.web.exposure.include` 를 추가하는 순간 — 흔한 작업입니다 —
`/actuator/env`, `/actuator/configprops`, `/actuator/heapdump` 가 한꺼번에 무인증이 됩니다.
`env` 와 `configprops` 는 기본 마스킹이 있지만 **`heapdump` 에는 없습니다.**

**제안** `/actuator/health`, `/actuator/info` 로 좁히기. 나중에 필요한 걸 추가하는 편이
지금 전부 열어두고 나중에 기억해서 막는 것보다 안전합니다.

---

### A-03. OpenAI 호출에 타임아웃이 없는데 트랜잭션 안에서 돕니다

**위치** `support/openai/AbstractOpenAiSummaryClient.java:18`

```java
this.restClient = RestClient.builder()          // 정적 팩토리 — Boot 설정을 안 받습니다
        .baseUrl("https://api.openai.com/v1")
        .defaultHeader("Authorization", "Bearer " + apiKey)
        .build();
```

`RestClient.builder()` 는 Boot 가 구성해주는 `RestClient.Builder` **빈이 아니라** 정적
팩토리라 `spring.http.client.*` 설정을 받지 않습니다. Apache/Jetty 클라이언트가 classpath 에
없어 JDK 팩토리로 떨어지는데, **read timeout 기본값이 무한**입니다.

호출부는 `PopupService` 의 `@Transactional` 메서드 안입니다 (55행 `@Transactional` → 59행 호출,
76행 → 81행). `BannerService` 도 같은 구조입니다.

**영향** OpenAI 가 응답을 안 주고 소켓이 멈추면 `POST /api/admin/popups` 하나가
**톰캣 워커 스레드와 MySQL 커넥션을 무기한 붙잡습니다.** 코드 주석이 기대하는
`catch (Exception)` 폴백("등록/수정 자체가 막히면 안 되므로 null 로 폴백")은
**행(hang)에는 걸리지 않습니다** — 예외가 아니니까요. 커넥션 풀이 먼저 마릅니다.

**제안** 요청 팩토리에 connect/read 타임아웃(수 초)을 명시. 요약을 트랜잭션 밖으로 빼는
것도 검토해볼 만합니다 — 외부 API 왕복이 DB 트랜잭션을 물고 있을 이유가 없습니다.

---

## 🟠 확실한 버그

### A-04. JWT 필터에서 예외가 나면 `ApiResponse` 가 아니라 500 HTML 이 나갑니다

**위치** `auth/JwtAuthenticationFilter.java:35`

```java
if (token != null && jwtTokenProvider.validate(token)) {
    if (jwtTokenProvider.isAccessToken(token)) {
        AuthUser authUser = new AuthUser(jwtTokenProvider.getUserId(token),
                                          jwtTokenProvider.getRole(token));   // try/catch 없음
```

`validate()` 는 서명과 만료만 증명합니다. 그 다음 `getUserId()` 의 `Long.valueOf(subject)`
(`JwtTokenProvider.java:66`)와 `getRole()` 의 `Role.valueOf(role)`(75행)은 값이 예상 밖이면
unchecked 예외를 던집니다.

**영향** `@RestControllerAdvice` 는 DispatcherServlet 안에서만 동작합니다. **필터는 그
바깥**이라 `GlobalExceptionHandler` 가 못 잡고, 컨테이너 기본 에러 페이지가 나갑니다.
프론트는 `{ success, data, error, traceId }` 를 기대하는데 파싱 불가능한 HTML 을 받고,
401 로 복구 가능한 상황이 500 이 됩니다.

**재현 경로** `Role` 에 상수를 추가해 배포 → 그 role 이 담긴 토큰 발급 → 롤백.
액세스 토큰 30분 동안 그 사용자들의 모든 요청이 500 입니다.

**제안** 블록을 `try/catch (RuntimeException)` 으로 감싸고 인증 없이 통과시키기
(그러면 뒤의 `JwtAuthenticationEntryPoint` 가 정상적으로 401 `ApiResponse` 를 냅니다).

---

### A-05. 5xx 의 원인이 로그에 전혀 남지 않습니다

**위치** `exception/BusinessException.java:10`, `exception/GlobalExceptionHandler.java:31-34`

두 가지가 겹칩니다.

1. `BusinessException` 에 **원인 예외를 받는 생성자가 없습니다.** 생성자는
   `(BaseErrorCode)` 와 `(BaseErrorCode, String)` 뿐입니다.
2. `GlobalExceptionHandler` 는 401/403 만 `warn` 으로 올리고 **나머지는 전부
   `log.info(...)`** 이며, throwable 을 넘기지 않아 스택 트레이스가 안 남습니다.

`INTERNAL_SERVER_ERROR` 인 도메인 코드가 실제로 있습니다 —
`ExhibitionErrorCode.IMAGE_PROCESSING_FAILED`, `PostErrorCode.IMAGE_UPLOAD_FAILED`,
`UserErrorCode.IMAGE_UPLOAD_FAILED`.

그리고 모든 throw 지점이 원인을 버립니다. **제 코드도 포함입니다:**

| 위치 | 패턴 |
|---|---|
| `LocalImageStorage.java:44-45` | `catch (IOException e)` → `throw new BusinessException(CODE)` |
| `ExhibitionItemService.java:242-243` | 〃 (제 코드) |
| `PostImageService.java:64-65` | 〃 |
| `ProfileImageService.java:74-75` | 〃 |

**영향** S3/디스크 쓰기가 실패하면 클라이언트는 500 을 받는데, 서버에 남는 증거는
`[IMAGE_UPLOAD_FAILED] 이미지 업로드에 실패했습니다.` INFO 한 줄뿐입니다.
디스크 풀인지, 권한인지, 네트워크인지 알 방법이 없습니다.

**제안** `BusinessException(BaseErrorCode, Throwable)` 생성자 추가 +
핸들러에서 `status.is5xxServerError()` 면 `log.error(..., e)`.

---

### A-06. `./gradlew test` 가 매번 api.openai.com 으로 실제 요청을 보냅니다

**위치** `src/test/resources/application-test.yml:12`

```yaml
openai:
  api-key: test-dummy-key     # 비어있지 않음 → enabled = true
```

게이트가 `this.enabled = apiKey != null && !apiKey.isBlank();` 라 더미 키도 **활성으로
판정**됩니다. `PopupAdminSmokeTest` 는 `post("/api/admin/popups")` 와 `patch(...)` 를
`isOk()` 기대로 쏘므로, 테스트를 돌릴 때마다 최소 2회 외부 호출이 나갑니다.

**영향** 401 을 받고 null 폴백되어 테스트는 통과하지만, 테스트가 외부 네트워크에
의존하게 됩니다. 네트워크가 막힌 CI 러너에서는 **A-03 의 무한 타임아웃과 겹쳐
실패가 아니라 행으로 갑니다.**

**제안** `api-key:` 를 빈 값으로. 그러면 의도된 `enabled=false` 단락 경로를 탑니다.

---

## 🟡 설계 · 운영

### A-07. 메서드 무관하게 열린 공개 경로 — ✅ 팝업은 #55 에서 해결됨

**위치** `config/SecurityConfig.java:37-40`

같은 파일 javadoc 이 전시를 GET 전용으로 분리한 이유를 이렇게 적어뒀습니다 —
*"경로만으로 열면 아무나 남의 장식장을 지울 수 있게 됩니다."* 리뷰 기준 커밋(`f1fbc06`)
에서는 팝업이 메서드 무관 목록에 있었고, `/api/home`·`/api/banners` 도 마찬가지였습니다.

> **✅ 팝업 부분은 이미 해결됐습니다.** 이 리뷰를 돌린 뒤 머지된 **#55(팝업 찜, `2449c02`)**
> 가 `/api/popups`·`/api/popups/**` 를 `PUBLIC_ENDPOINTS` 에서 빼고
> `PUBLIC_GET_ENDPOINTS` 로 옮기면서 `{popupId:[0-9]+}` 숫자 제약까지 걸었습니다.
> 덕분에 찜(`POST`/`DELETE .../like`)과 위시리스트(`GET .../likes`)가 모두 인증 필요로
> 남았습니다. 코드에 이유까지 주석으로 남기셔서 **이 부분은 조치 불필요**합니다.

**남아 있는 부분** `/api/home`(38행)과 `/api/banners`(39행)는 여전히 메서드 무관입니다.
두 컨트롤러 모두 GET 매핑뿐이라 **지금 노출된 것은 없습니다.** 다만 팝업이 방금 지나온
것과 똑같은 경로입니다 — 나중에 이 아래에 변경 API 가 생기면 조용히 무인증이 됩니다.

**제안** 팝업과 같은 방식으로 `PUBLIC_GET_ENDPOINTS` 로 이동. 우선순위는 낮습니다.

---

### A-08. 공개 `/api/popups` 가 ADMIN 전용 목록과 같은 데이터를 반환합니다

**위치** `config/SecurityConfig.java:40` + `PopupService`

`getPopups()` 와 `getAllPopupsForAdmin()` 이 둘 다 `findAllByOrderByStartDateAsc()` 를 호출하고
같은 `PopupSummaryResponse` 로 매핑합니다. #54 의 의도(프론트의 종료 탭)와는 일치하지만,
결과적으로 **관리자 전용 목록 엔드포인트가 무의미해졌습니다.**

지금은 문제가 아니지만, 나중에 임시저장·비공개 팝업 개념이 생기면 **기본적으로 공개
경로로 새어 나갑니다.** 의도한 상태가 맞는지만 확인 부탁드립니다.

---

### A-09. 기본 프로필이 `local` 이고, 쓸 수 있는 JWT 시크릿이 커밋돼 있습니다

**위치** `resources/application.yml:5`, `resources/application-local.yml:3`

```yaml
spring.profiles.default: local
```
```yaml
jwt.secret: local-dev-temporary-secret-key-change-this-later-123456   # 커밋돼 있음
```

`application-local.yml` 이 `storage.type` 까지 채우므로, 프로필 없이 뜬 jar 는
**실패하지 않고 정상 부팅합니다.**

**이미 문서화돼 있습니다** — `deploy/README.md:29-31` 이 "이게 빠지면 로컬 전용 JWT
시크릿으로 뜨는 보안 사고가 날 수 있음 — **절대 빼면 안 됨**" 이라고 명시하고 있습니다.
그래서 팀이 모르는 위험은 아닙니다.

**남은 문제는 강제 수단이 없다는 것뿐입니다.** systemd 유닛을 손대거나, 컨테이너로
수동 실행하거나, 롤백에서 플래그가 빠지면 앱은 **건강하게** 뜹니다. 그리고 저장소를 가진
누구나 `{"sub":"1","role":"ADMIN"}` 을 서명해 `/api/admin/**` 전체에 접근할 수 있습니다.

**제안** 부팅 시 어서션 하나 — `local` 프로필이 아닌데 시크릿이 알려진 로컬 값이면
기동 실패. `dev` 가 환경변수 없으면 부팅 실패하도록 만든 것과 같은 사고방식입니다.

---

### A-10. 404 여야 할 것이 400 으로, 405 도 400 으로 나갑니다

**위치** `exception/GlobalExceptionHandler.java:71-79`

`NoResourceFoundException` 과 `HttpRequestMethodNotSupportedException` 을 한 핸들러로 묶어
둘 다 `BAD_REQUEST` 를 반환합니다.

**영향** 오타난 경로(`/api/exhibition/1`)가 404 가 아니라 400 이고, GET 전용 라우트에
POST 하면 405 가 아니라 400 이며 RFC 가 요구하는 `Allow` 헤더도 없습니다.
400 은 호출자에게 "네 요청 본문이 잘못됐다"고 말하는 건데 본문은 멀쩡합니다.
프론트 재시도 로직과 모니터링이 404/405 기준으로 동작하면 오분류됩니다.

**제안** 두 핸들러로 분리해 `NOT_FOUND` / `METHOD_NOT_ALLOWED`.

---

### A-11. 용량 초과 업로드가 "잘못된 multipart 요청" 으로 나갑니다

**위치** `exception/GlobalExceptionHandler.java:87`

`MaxUploadSizeExceededException` 이 `MultipartException` 을 상속해서 일반 핸들러에
흡수됩니다. 업로드 제한이 10MB 인데 **CLAUDE.md 에 폰 사진이 3~8MB 라고 적혀 있는 만큼
가장 흔하게 마주칠 실패**인데, 사용자는 파일이 너무 크다는 걸 알 방법이 없습니다.

**제안** 전용 핸들러로 413 + 메시지에 한도 명시.

---

### A-12. 검증 에러가 중복 시 하나만 남고, 응답 위치도 다릅니다

**위치** `exception/GlobalExceptionHandler.java:45-53`

`Map<String, String> fieldErrors = new HashMap<>();` 라, 한 필드가 `@NotBlank` 와 `@Size` 를
동시에 위반하면 **나중에 들어온 메시지만 남습니다.** 순서는 실행마다 다를 수 있습니다.

또 이 핸들러만 `success:false` 인데 내용을 **`data`** 에 담습니다. 다른 모든 에러 경로는
`data` 가 null 이라, 실패 시 `error` 만 읽는 프론트는 필드 메시지를 영영 못 봅니다.

**제안** `merge((a, b) -> a + ", " + b)` + 위치를 `error` 쪽으로 통일할지 논의.

---

### A-13. `X-Trace-Id` 를 프론트가 읽을 수 없습니다 (CORS)

**위치** `config/CorsConfig.java:32`

```java
configuration.setExposedHeaders(List.of("Authorization"));
```

`TraceIdFilter.java:33` 이 로그 대조용으로 `X-Trace-Id` 를 응답에 실어 보내는데,
CORS 는 노출 목록에 없는 헤더를 크로스 오리진 JS 에서 못 읽게 막습니다.
`https://*.vercel.app` 에서 프론트는 이 값을 읽을 수 없어 **필터의 목적이 무력화**됩니다.

게다가 노출 목록에 든 `Authorization` 은 **요청 헤더**라 이 API 가 응답에 실은 적이
없습니다. 목록이 정확히 뒤집혀 있습니다.

**제안** `List.of("X-Trace-Id")`.

---

### A-14. traceId 가 8자라 충돌이 흔합니다

**위치** `filter/TraceIdFilter.java:31`

```java
String traceId = UUID.randomUUID().toString().substring(0, 8);   // 32비트
```

생일 한계로 **약 7.7만 요청이면 중복 확률 50%** 입니다. 팀원이 알려준 traceId 로 로그를
grep 하면 무관한 요청의 줄이 섞여 나옵니다 — 클래스 javadoc 이 약속한 바로 그 조회가
깨집니다.

**제안** 12~16자, 또는 전체 UUID.

---

### A-15. 요청 한 번에 JWT 를 4번 파싱합니다

**위치** `auth/JwtAuthenticationFilter.java:36-37`

`validate()`, `isAccessToken()`, `getUserId()`, `getRole()` 이 각각 독립적으로
`parseClaims()`(`JwtTokenProvider.java:106`)를 호출합니다. 인증된 요청마다
**HMAC 검증 4회 + JSON 역직렬화 4회**입니다.

기능 문제는 아니지만 모든 인증 요청이 무는 비용입니다.

**제안** 한 번 파싱한 `Claims` 를 넘겨 쓰거나, principal 을 돌려주는 단일 `parse()`.

---

### A-16. 필터가 두 번 등록됩니다

**위치** `auth/JwtAuthenticationFilter.java:19` (`@Component`)

Boot 는 `Filter` 빈을 서블릿 필터로 자동 등록하는데, `SecurityConfig.java:91` 이 같은
인스턴스를 시큐리티 체인에도 넣습니다. `OncePerRequestFilter` 의 중복 실행 방지 덕에
**지금은 실질 버그가 없습니다.**

다만 사본이 시큐리티 체인 **바깥**에 존재하는 상태라, 누군가 `securityMatcher` 나
두 번째 `SecurityFilterChain`, `web.ignoring()` 을 추가하는 순간 보호가 사라집니다.

**제안** `FilterRegistrationBean` 으로 `setEnabled(false)` 하거나 `@Component` 를 떼고
`SecurityConfig` 에서 생성.

*(참고: `TraceIdFilter` 의 `@Component` + `@Order(HIGHEST_PRECEDENCE)` 는 의도대로
맞습니다 — 401/403 핸들러에서도 traceId 가 찍히려면 시큐리티 체인 밖이어야 합니다.)*

---

### A-17 ~ A-20. 소소한 것

| # | 위치 | 내용 |
|---|---|---|
| A-17 | `auth/JwtTokenProvider.java:75` | `Role.valueOf` 가 미지의 값에 예외 → null 케이스는 `USER` 로 폴백하는데 **미지 값은 폴백이 없어** 비대칭. A-04 와 같이 고치면 됩니다 |
| A-18 | `auth/JwtTokenProvider.java:34` | `createAccessToken` 이 `createToken` 본문을 복제. 지금 `createToken` 은 리프레시 전용이라, 토큰 생성 로직을 고치면 **리프레시에만 반영**됩니다 |
| A-19 | `auth/JwtTokenProvider.java:34` | `role.name()` 은 null 이 오면 NPE — 현재는 `User` 빌더 기본값 덕에 안전할 뿐입니다 |
| A-20 | `auth/Role.java:6` | 파일 끝 개행 누락 |

---

## 확인했고 문제 없던 것

리뷰가 짚었지만 실제로는 안전한 것들입니다. 다음에 같은 걸 또 의심하지 않도록 남겨둡니다.

- **JWT 알고리즘 혼동 공격 방어됨** — `verifyWith(SecretKey)` + `parseSignedClaims` 라
  `alg:none` 과 RS256/HS256 혼동이 모두 막힙니다.
- **토큰 타입 분리 양방향 강제됨** — 필터의 `isAccessToken`, `AuthService.reissue`/`logout` 의
  `isRefreshToken`.
- **`hasRole("ADMIN")` 과 `ROLE_` 접두사 일치**, 배너/팝업 변경 API 는 전부 `/api/admin/**` 아래.
- **`handleGeneralException` 은 스택을 서버에만 남기고** 클라이언트에는 일반 메시지만 반환 —
  내부 정보 노출 없음.
- **`MDC.remove` 가 `finally` 에 있어** 스레드 재사용에서 안전.
- **`@EnableMethodSecurity` 부재는 문제 아님** — `@PreAuthorize` 를 쓰는 곳이 없습니다.
- **`/uploads/**` 공개는 통제됨** — `LocalImageStorage.resolve` 가 `../` 탈출을 막고,
  키는 서버 생성이며, `ImageInspector` 매직바이트 검증이 있고, 핸들러 자체가
  `@ConditionalOnProperty(storage.type=local)` 라 운영에는 존재하지 않습니다.
- **`Paging.normalize` 는 6개 호출부 전부 정상** (`default <= max`).

---

## 다음 배치

| 배치 | 범위 | 규모 |
|---|---|---|
| B | `chat` 전체 | 663 LOC |
| C | 이미지 처리·저장 (`exhibition/image` + `ExhibitionAsyncConfig`) | 939 LOC |
| D | 전시 계약층 (controller·dto·entity·repository + `ExhibitionLike*`) | 883 LOC |
| E | 보관함·파이프라인 오케스트레이션 | #49 머지 후 |
