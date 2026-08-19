# 배치 C — 이미지 처리·저장 코드 리뷰

> 대상: `exhibition/image` 8개 파일 + `ExhibitionAsyncConfig`, 총 9개 파일 939 LOC (기준 커밋 `2449c02`)
> 제외: `ImageCleanup`, `MultipartImageValidator` — 미머지 PR #49 가 재작성했고 3라운드 리뷰를 받았습니다
> 관련 이슈: #66 · 이전 배치: #60(A: global), #62(B: chat)

9건 전부 코드로 재확인했습니다. `exhibition/image` 는 제 도메인이라 **수정도 제가 합니다** —
이 문서는 무엇을 어떤 순서로 고칠지 합의하기 위한 것입니다.

---

## 🔴 C-01. 로그인한 아무나 남의 이미지를 스토리지에서 지울 수 있습니다

**위치** `image/StorageUrls.java:20` (근본 원인) · `post/service/PostService.java:207-217, 286-295` (도달 경로)

### 원인

```java
static String keyFrom(String publicBaseUrl, String imageUrl) {
    String prefix = publicBaseUrl + "/";
    return imageUrl.startsWith(prefix) ? imageUrl.substring(prefix.length()) : null;
}
```

javadoc 은 *"남의 주소를 받아 지우거나 읽는 일이 없도록 하기 위한 것입니다"* 라고 적혀 있지만,
실제로 확인하는 건 **base URL 접두사뿐**입니다. 그 뒤 문자열은 통째로 S3 key(로컬은 파일 경로)가
되므로, **같은 버킷 안의 모든 객체가 주소로 지정 가능**합니다. 호출자와 key 를 묶는 장치가 없습니다.

### 도달 경로 (전부 확인함)

| 단계 | 위치 | 사실 |
|---|---|---|
| 1. 남의 URL 을 알아낸다 | `PostDetailResponse.imageUrls`<br>`UserProfileResponse.profileImageUrl` | 응답에 그대로 담겨 나갑니다 |
| 2. 그 URL 을 내 글에 심는다 | `PostService.saveImages:286-295` | 클라이언트가 준 `imageUrls` 를 **검증 없이** 그대로 저장합니다 |
| 3. 글에서 빼면 삭제된다 | `PostService.update:207-217` | 빠진 URL 을 `removed` 로 계산해 `deleteImagesAfterCommit` → `imageStorage.deleteByUrl` |

즉 **남의 이미지 URL 로 잡담 글을 하나 만들고 `imageUrls: []` 로 PATCH** 하면 그 파일이 지워집니다.
대상은 프로필 사진, 남의 게시글 사진, 장식장 굿즈 이미지 전부입니다.
S3(`S3ImageStorage:73`)와 로컬(`LocalImageStorage:50`) 모두 같은 `keyFrom` 을 타서 동작이 같습니다.

DB 행은 그대로 남고 파일만 사라지므로, 화면에는 **깨진 이미지**로 나타납니다.
삭제 실패는 로그만 남기고 삼키는 설계라(`S3ImageStorage:81-84`) 서버에서도 눈에 잘 안 띕니다.

### 대응

키에 이미 소유자가 박혀 있는 게 다행입니다 —
`users/{userId}/…`, `posts/{userId}/…`, `exhibitions/{exhibitionId}/…`.

1. **`keyFrom` 에 기대 접두사를 받게 한다** — 호출자가 "이 소유자의 것만" 을 명시하도록.
   근본 원인 쪽 수정이라 앞으로 생길 호출부까지 같이 막힙니다.
2. **`PostService.saveImages` 에서 URL 소유를 검증한다** — 이미 `PendingPostImage` 로
   "내가 방금 올린 URL" 을 추적하고 있으니(`claimImages`), 그 목록에 없는 URL 은 거부하면 됩니다.

1번은 제 도메인이라 제가 하고, 2번은 `post` 도메인이라 @418teapot-sh 님 확인이 필요합니다.
**둘 중 하나만 해도 이 경로는 막히지만, 둘 다 하는 쪽을 권합니다** — 1번은 다른 도메인이 같은 실수를
반복하는 걸 막고, 2번은 "남의 URL 을 내 글에 심는 것" 자체를 막습니다.

---

## 🟠 C-02. 메모리 보호 단계가 가장 큰 할당 뒤에 옵니다

**위치** `image/GoodsImageProcessor.java:90`

```java
BufferedImage img = resizeToFit(toArgb(src), o.maxWorkingSize());
```

클래스 주석은 1단계를 *"작업 크기로 리사이즈 — 메모리 보호(4000x3000 원본은 펼치면 한 장에 48MB)"*
라고 설명하는데, 실제로는 **`toArgb` 가 먼저** 원본 해상도 그대로 `TYPE_INT_ARGB` 사본을 만듭니다.

`ImageInspector.MAX_PIXELS`(4천만 픽셀) 한계치 이미지라면

- 디코딩된 원본(`TYPE_3BYTE_BGR`): 약 120MB
- `toArgb` 사본(4바이트/픽셀): 약 160MB
- 둘이 **동시에** 살아 있음 → 한 작업에 약 280MB

스레드가 2개라 최악 560MB 이고, 여기에 `ExhibitionAsyncConfig` 가 문서화한 큐 200MB 가 더해집니다.
**설정 주석이 근거로 든 상한(200MB)이 실제와 크게 다릅니다.**

`resizeToFit` 이 어차피 `TYPE_INT_ARGB` 대상에 그리므로, **순서만 바꾸면**
(`toArgb(resizeToFit(src, max))`) 큰 쪽 사본이 사라집니다. 리사이즈가 일어나지 않는
작은 이미지에서는 지금과 동일합니다.

---

## 🟠 C-03. 픽셀 수 상한이 업로드 시점에는 확인되지 않습니다

**위치** `image/ImageInspector.java:74`

```java
public static boolean isSupported(byte[] data) {
    return detectFormat(data).filter(SUPPORTED_FORMATS::contains).isPresent();   // 포맷만 봅니다
}
```

`MAX_PIXELS` 는 `read()` 안에서만 확인합니다(`:96`). 그런데 업로드 경로가 실제로 쓰는 검증은
`isSupported` 라서, **압축이 잘 되는 20000×20000 PNG 는 4MB 짜리로 통과**합니다.

결과: 요청은 200 으로 성공하고, 원본 바이트를 든 채 큐에 들어갔다가, 백그라운드 스레드에서야
거부되어 `FAILED` 로 끝납니다. 사용자는 **왜 실패했는지 알 수 없습니다.**

크기 검증은 요청 경로에 있어야 400 으로 이유를 알려줄 수 있습니다.
겸사겸사 40MP 라는 한계치 자체도 C-02 의 메모리 예산과 함께 다시 보면 좋겠습니다.

---

## 🟠 C-04. 종료 대기 시간이 작업 하나보다 짧습니다

**위치** `service/ExhibitionAsyncConfig.java:55`

```java
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(60);
```

작업 하나의 최악 시간은 그보다 깁니다 — remove.bg 연결 10초(`RemoveBgClient:50`)
\+ 요청 60초(`:78`) + S3 30초(`S3ImageStorage:35`) ≈ **100초**.
큐가 찼다면 20개 / 스레드 2개 × 100초 ≈ 900초입니다.

시간이 지나면 `ExecutorConfigurationSupport` 는 경고만 찍고 `shutdownNow()` 없이 반환합니다.
그 뒤 DataSource·EntityManagerFactory 가 닫히는데 **작업 스레드는 아직 돌고 있어서**,
결과 기록(`ExhibitionImageProcessor` 의 `markReady`/`markFailed`)이 닫힌 커넥션 풀을 만납니다.
→ 굿즈가 **PENDING 인 채로 영원히 남습니다.** 이 설정이 막으려던 상황 그 자체입니다.

큐·타임아웃을 줄여 창 안에 들어오게 하거나, 대기 시간을 실제 최악에 맞추는 선택이 필요합니다.
(방치 PENDING 재시도가 15분 뒤 열리므로 완전히 복구 불가는 아니지만, 사용자가 직접 눌러야 합니다)

---

## 🟡 C-05. S3 업로드만 예외를 안 감싸서 운영과 로컬 응답이 다릅니다

**위치** `image/S3ImageStorage.java:59`

`upload` 에 try/catch 가 없어 `S3Exception`/`SdkClientException` 이 그대로 올라갑니다.
반면 `LocalImageStorage.upload:44` 는 `BusinessException` 으로 감쌉니다.

같은 "저장 실패" 가 `storage.type` 에 따라 다르게 보입니다 —
로컬은 구조화된 에러 응답, 운영(S3)은 `GlobalExceptionHandler` 의 마지막 그물에 걸려
일반 500 "예상치 못한 오류가 발생했습니다". 호출부(`ProfileImageService`, `PostImageService`)는
둘 다 try/catch 가 없습니다.

같은 클래스의 `deleteByUrl`·`download` 는 양쪽 다 감싸져 있어서 **`upload` 만 비대칭**입니다.

---

## 🟡 C-06. 로컬 삭제가 예외를 던져 정리 배치를 중단시킬 수 있습니다

**위치** `image/LocalImageStorage.java:50`

```java
try {
    Files.deleteIfExists(resolve(key));   // resolve 는 BusinessException 을 던집니다
} catch (IOException e) {                  // IOException 만 잡습니다
```

`resolve(:77-82)` 가 경로 탈출을 막으려고 `BusinessException` 을 던지는데, 그게 `IOException`
catch 를 그냥 지나쳐 밖으로 나갑니다.

호출부는 이 메서드가 안 던진다고 **명시적으로 가정**하고 있습니다 —
`PendingPostImageCleaner:50` 주석에 *"실패해도 내부에서 로그만 남기고 삼키므로 여기서 따로 감쌀
필요가 없습니다"* 라고 적고 `abandoned.forEach(...)` 로 돕니다. 저장된 URL 하나만 이상해도
**남은 정리 대상 전체가 중단**됩니다. `download` 도 `ImageStorage` javadoc(`:40`)이 약속한
`UncheckedIOException` 과 실제가 어긋납니다.

---

## 🟡 C-07 ~ C-09. 나머지

| # | 위치 | 내용 |
|---|---|---|
| C-07 | `LocalImageStorage.java:44` | 원인 `IOException` 을 로그도 없이 버리고, 게다가 user·post 도메인이 함께 쓰는 공용 저장소가 **전시 도메인 에러코드**(`ExhibitionErrorCode.IMAGE_PROCESSING_FAILED`)를 던집니다. 프로필 사진 업로드가 디스크 문제로 실패해도 "이미지 처리에 실패했습니다" 만 남고 원인이 로그에 없습니다 |
| C-08 | `RemoveBgClient.java:82` | 응답 본문을 `ofByteArray()` 로 **상한 없이** 버퍼링합니다. 게다가 비정상 응답이면 본문 **전체**를 예외 메시지에 넣고(`:89-90`), 그게 로그로 나갑니다 — 프록시 오설정 시 수백 KB 짜리 HTML 이 통째로 찍힙니다. 본문 상한 + 에러 본문 절단(앞 512바이트 정도)이 필요합니다 |
| C-09 | `RemoveBgClient.java:49` | `HttpClient`(Java 21 부터 `AutoCloseable`)를 닫는 곳이 없습니다. 싱글턴이라 운영에서는 한 번이지만, 테스트에서 스프링 컨텍스트가 새로 뜰 때마다 셀렉터 스레드와 커넥션 풀이 쌓입니다 |

---

## 고칠 순서 (제안)

| 순서 | 항목 | 이유 |
|---|---|---|
| 1 | **C-01** | 배포 서버에서 지금 가능한 데이터 삭제입니다. 나머지와 성격이 다릅니다 |
| 2 | C-02 + C-03 | 같은 메모리 예산 이야기라 함께 봐야 숫자가 맞습니다 |
| 3 | C-04 | 배포 때마다 조용히 PENDING 이 쌓일 수 있습니다 |
| 4 | C-05 ~ C-09 | 사용자에게 보이는 증상이 작습니다 |

---

## 다음 배치

| 배치 | 범위 | 규모 |
|---|---|---|
| D | 전시 계약층 (controller·dto·entity·repository + `ExhibitionLike*`) | 883 LOC |
| E | 보관함·파이프라인 오케스트레이션 | #49 머지 후 |
