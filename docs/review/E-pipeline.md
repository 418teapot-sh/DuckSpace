# 배치 E — 보관함 · 파이프라인 오케스트레이션 코드 리뷰

> 대상: A~D 에서 제외했던 21개 파일 **1,962 LOC** (기준 커밋 `c1803ae`, #49 머지 후)
> 분량이 A~D(660~940 LOC)의 2~3배라 **E1(파이프라인 805) / E2(보관함·서비스 1,157)** 로 나눠 감사
> 관련 이슈: #72 · 이전 배치: #60(A) · #62(B) · #67(C) · #69(D)

14건이 나왔고, 심각한 5건은 코드로 직접 재확인했습니다. 전시 도메인이라 **수정도 제가 합니다.**

#49 3라운드에서 이미 고친 것들(재시도 방치시계·잠금 조회·배치 참조 조회·`deleteOrphan` 분리)은
스코프에서 제외해 중복 보고가 없습니다.

---

## 🔴 E-01. 인터럽트 처리 순서 때문에 복구가 원천적으로 불가능합니다

**위치** `service/ExhibitionImageProcessor.java:246`

```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();          // ← 먼저 인터럽트 플래그를 세우고
    log.warn("이미지 처리가 중단되었습니다. id={}", logId);
    failKeepingSource(logId, keyPrefix, data, existingSourceUrl, recorder);   // ← 그 뒤 복구 I/O
}
```

인터럽트를 삼키지 않는 것 자체는 맞습니다. 문제는 **순서**입니다.
`failKeepingSource` 는 같은 스레드에서 **저장소 업로드 + DB 트랜잭션**을 합니다.
그 스레드는 이미 인터럽트 플래그가 서 있습니다.

| 하는 일 | 인터럽트된 스레드에서 |
|---|---|
| `recorder.failed(keep)` → 커넥션 획득 | HikariCP 가 `ConcurrentBag.borrow` 의 `InterruptedException` 을 잡아 **`SQLException("Interrupted during connection acquisition")` 으로 재던집니다** |
| `LocalImageStorage.upload` → `Files.write` | 인터럽트 가능 채널이라 **파일을 만들고 truncate 한 뒤** `ClosedByInterruptException` |
| S3 동기 클라이언트 | 인터럽트된 스레드에서 요청을 중단합니다 |

**즉 실패 기록이 반드시 실패합니다.** `written=false` 가 되고, 행은
`PENDING` · `imageUrl=null` 로 영구히 남습니다. 그리고 그 상태는 **재시도도 안 됩니다**(E-08 참조) —
사용자는 삭제하고 다시 올리는 수밖에 없습니다. 로컬 저장소면 0바이트 고아 파일도 하나 남습니다.

**언제 밟나** 배포입니다. 종료 대기(60초)가 만료되면 `shutdownNow()` 가 워커를 인터럽트하는데,
그때 `HttpClient.send` 에서 대기 중이던 작업이 정확히 이 경로로 들어옵니다. C-04(종료 대기가
작업 최악 시간보다 짧음)와 겹쳐서 **배포할 때마다 재현될 수 있는 조합**입니다.

**기존 테스트가 잘못된 안심을 주고 있었습니다** — `ExhibitionImageProcessorTest:140`
(`인터럽트는_삼키지_않는다`)은 `imageStorage` 와 `statusWriter` 가 목이라 인터럽트 상태를
무시합니다. 그래서 통과합니다.

**수정** 복구를 먼저 하고 플래그를 마지막에 세웁니다. 또는 `Thread.interrupted()` 로 잠시 지웠다가
`failKeepingSource` 후에 복원합니다. 테스트는 목이 아니라 인터럽트에 반응하는 스텁이 필요합니다.

---

## 🟠 E-02. 남의 이미지를 "고정" 해서 못 지우게 만들 수 있습니다

**위치** `image/ImageCleanup.java:139` (javadoc `:30`) · `service/ExhibitionItemService.java:48-56`

`POST /items` 가 `imageUrl` 의 소유를 확인하지 않는다는 건 이미 알고 있었습니다(**D-01**,
프론트가 아직 목 URL 을 써서 의도적으로 미뤄둔 상태). 그런데 **삭제 악용의 반대 방향**이 있습니다.

참조 확인(`findReferencedUrls`)과 409 판단(`GoodsImageService:122` 의 `existsByImageUrl`)이
**둘 다 전체 사용자 대상**입니다. 그래서 —

1. 공개 장식장 상세로 A 의 굿즈 `imageUrl` 을 읽습니다
2. B 가 그 URL 을 **자기 장식장**에 배치합니다 (소유 검증이 없으므로 통과)
3. 이제 **A 의 `DELETE /api/images/{id}` 가 영원히 409 `IMAGE_IN_USE`** 입니다
4. A 가 자기 굿즈·보관함 항목을 전부 지워도, 참조 가드가 **B 의 참조를 보고 파일을 남깁니다**
5. A 의 사진은 **B 의 장식장에서 계속 공개로 서빙됩니다**

**"삭제" 가 삭제가 아니게 됩니다.** 사용자 입장에서는 지웠는데 남의 화면에 계속 떠 있습니다.

리뷰 요청에 "공유 배치가 정식 기능이 된 지금 수용 리스크 전제가 타당한지" 를 물었는데,
답은 **문서화된 수 ms 창은 여전히 작지만, 전제가 못 덮는 건 이 교차 사용자 고정 쪽**이라는 것입니다.

**수정** 배치 시점에 `imageUrl` 소유를 검증하는 것(D-01)이 유일하게 맞는 지점입니다.
정리 쪽 확인만 소유 범위로 좁히면 **오히려 위험합니다** — 남의 참조를 무시하고 지우게 되니까요.
D-01 을 미루는 비용이 생각보다 크다는 점을 기록해 둡니다.

---

## 🟠 E-03. 큐 거절 처리가 이미 성공한 업로드를 500 으로 만들 수 있습니다

**위치** `service/ExhibitionImageProcessor.java:179`

```java
} catch (RejectedExecutionException e) {
    log.error("이미지 처리 큐가 가득 찼습니다. id={}", logId, e);
    recorder.failed(fallbackUrl);          // REQUIRES_NEW 트랜잭션
}
```

이 코드는 `@TransactionalEventListener(AFTER_COMMIT)` 안에서 돕니다.
**`AFTER_COMMIT` 콜백의 예외는 삼켜지지 않고 `commit()` 밖으로 전파됩니다.**

**시나리오** 부하가 몰려 큐(20)가 가득 차는 순간은 커넥션 풀도 같이 빡빡한 순간입니다.
`recorder.failed(...)` 가 `CannotCreateTransactionException` 을 던지면 —
**행은 이미 커밋돼 사용자 화면에 `PENDING` 으로 보이는데, 응답은 500** 입니다.

부수적으로 `REQUIRES_NEW` 가 방금 커밋된 바깥 트랜잭션(커넥션이 아직 바인딩된 상태)을 suspend 하므로,
거절된 업로드마다 **커넥션을 2개** 잡습니다. 하필 풀이 가장 빡빡할 때입니다.

**수정** `recorder.failed(...)` 를 자체 try/catch 로 감싸 로그만 남깁니다.
큐가 찼다는 사실은 이미 `log.error` 로 남으므로 정보 손실이 없습니다.

---

## 🟠 E-04. `deleteOrphan` 이 실제로 참조된 파일을 지울 수 있는 경로가 하나 있습니다

**위치** `service/ExhibitionImageProcessor.java:225`

```java
try {
    written = recorder.ready(url);
} catch (Exception e) {
    imageCleanup.deleteOrphan(url);   // 참조 확인 없이 삭제
    throw e;
}
```

`deleteOrphan` 은 "이 URL 은 DB 에 존재할 수 없다" 는 전제로 가드를 건너뜁니다.
`!written` 분기(232행)에서는 그 전제가 맞습니다. **여기서는 아닙니다.**

예외가 `commit()` 자체에서 날 수 있습니다 — MySQL 은 커밋했는데 응답 전에 커넥션이 끊기는
경우(커밋 모호성)입니다. 그러면 **행은 `READY` 이고 `url` 을 가리키는데 파일만 지워집니다.**

결과: 굿즈가 `READY` 인 채로 이미지가 깨지고, **복구도 안 됩니다** — 재시도는 `FAILED` 만 받고,
뒤이은 `failKeepingSource` 도 `pending()` 가드에 막혀 no-op 입니다.

**수정** 이 분기만 가드 있는 `delete()` 로 바꿉니다. 인덱스 조회 한 번이면 닫힙니다.
(`!written` 분기는 `deleteOrphan` 유지 — 거기서는 전제가 성립합니다)

---

## 🟠 E-05. 보관함 삭제의 409 판단이 비원자적입니다

**위치** `service/GoodsImageService.java:122`

```java
if (url != null && exhibitionItemRepository.existsByImageUrl(url)) {
    throw new BusinessException(ExhibitionErrorCode.IMAGE_IN_USE);
}
goodsImageRepository.delete(image);
imageCleanup.deleteAfterCommit(url);
```

확인과 삭제 사이에 동시 `POST /items` 가 끼면, 그 배치는 이 트랜잭션 스냅샷에 안 보여서
**409 가 안 나고 그대로 삭제로 진행**됩니다. 파일은 `ImageCleanup` 가드가 지켜주지만
(배치가 커밋됐다면 참조가 잡힘), **보관함 행은 사라집니다** — 장식장에는 떠 있는데 보관함에는 없는 상태.

같은 클래스의 `retry()` 는 정확히 이 부류의 경합 때문에 `findOwnedForUpdate` 로 잠그고 읽습니다.
**`delete()` 도 같게 하면 됩니다.**

---

## 🟡 E-06. 51번째 장식장부터 마이페이지에서 접근할 수 없습니다

**위치** `service/ExhibitionService.java:100`

```java
List<Long> ids = exhibitionRepository.findIdsByUserId(
        userId, PageRequest.of(0, Paging.normalize(limit, MINE_DEFAULT_LIMIT, MAX_LIMIT)));
```

`MAX_LIMIT` 이 50 이고 **커서가 없습니다**(`PageRequest.of(0, …)` 로 첫 페이지 고정).
정렬은 `id asc` 라 오래된 것부터 50개만 나옵니다.

`ExhibitionRepository.findIdsByUserId` javadoc 이 이렇게 적어뒀습니다 —
*"이게 없으면 사용자가 자기 장식장을 다시 찾을 방법이 없습니다."*
그런데 **51번째부터는 그 유일한 경로에서도 안 보입니다.**

시연 규모에서는 안 밟지만, 문서가 약속한 것과 코드가 다릅니다. 다른 목록은 전부 커서 페이징이라
`Paging.slice` 를 그대로 쓰면 됩니다.

---

## 🟡 E-07. 정리 배치 재시도에 backoff 가 없어 3회가 순식간에 소진됩니다

**위치** `image/ImageCleanup.java:148`

```java
submit(() -> deleteUnreferenced(imageUrls, retriesLeft - 1));
```

단일 스레드 실행기의 큐는 보통 비어 있어서, 세 번의 시도가 **수 마이크로초 안에** 연달아 실행됩니다.
커넥션 풀 고갈이나 MySQL 페일오버 같은 "일시적" 오류는 그 사이에 사라지지 않습니다 —
**곧장 "수동 회수 대상" 에러 로그로 직행합니다.**

반대로 큐가 가득 차 있으면 `submit` 의 인라인 폴백(118행)이 걸려 **같은 스택에서 즉시 재귀 실행**이
되어 지연이라는 목적이 더 사라집니다.

**수정** 정리 스레드에서 수백 ms 쉬거나(단일 스레드라 다른 작업을 막지만 원래 지연 허용 경로입니다),
스케줄러로 재제출.

---

## 🟡 E-08. 방치 PENDING 허용이 javadoc 이 말하는 케이스를 못 살립니다

**위치** `service/AbandonedPending.java:30` · `ExhibitionItemService:121-128` · `GoodsImageService:94-101`

`AbandonedPending` javadoc 은 *"강제 종료(OOM 등)로 처리가 끊기면 PENDING 이 영원히 남는데,
재시도가 FAILED 만 받으면 사용자가 복구할 방법이 없습니다"* 를 근거로 15분 기준을 둡니다.

그런데 **첫 업로드가 중단된 행은 `imageUrl` 이 null** 입니다. 재시도 흐름은 이렇습니다.

```java
if (status != FAILED && !AbandonedPending.isAbandoned(...)) throw ITEM_NOT_RETRYABLE;  // 통과
String source = item.getImageUrl();
if (source == null || source.isBlank()) throw RETRY_SOURCE_MISSING;                    // 여기서 막힘
```

**`isAbandoned` 를 통과시켜 놓고 바로 다음 가드가 거부합니다.**
즉 이 장치가 실제로 살리는 건 이미 URL 이 있는 행(재시도의 재시도)뿐입니다.

복구 경로가 아주 없지는 않습니다 — `RETRY_SOURCE_MISSING` 은 "삭제 후 재업로드" 안내용이니
사용자는 그렇게 복구합니다. 다만 **javadoc 이 실제보다 많은 걸 약속하고 있어서**,
나중에 이 기준값을 튜닝하려는 사람이 잘못 이해합니다. E-01 이 만드는 상태도 정확히 이 경우입니다.

**수정** 접수 시점에 원본을 먼저 저장하거나, javadoc 에 한계를 명시.

---

## 🟡 E-09 ~ E-14

| # | 위치 | 내용 |
|---|---|---|
| E-09 | `image/MultipartImageValidator.java:44` | Content-Type 을 **완전 일치**로 비교해서 `image/jpeg; charset=UTF-8` 이나 뒤에 `;` 가 붙은 정상 헤더를 400 `UNSUPPORTED_IMAGE_TYPE` 으로 거부합니다. 일부 모바일 클라이언트가 이렇게 보냅니다. 바이트는 `ImageInspector` 가 통과시킬 정상 JPEG 인데도요. `MediaType.parseMediaType` 으로 type/subtype 만 비교하면 됩니다 |
| E-10 | `image/ImageCleanup.java:155` | `deleteUnreferenced` 가 목록을 중복 제거 없이 순회하고, `findImageUrlsByExhibitionId` 에도 `distinct` 가 없습니다(`findReferencedUrls` 에는 있습니다). **같은 사진을 한 장식장에 여러 번 놓는 게 정식 기능**이 된 지금, 그 장식장을 지우면 같은 키에 DELETE 를 배치 수만큼 보냅니다. `clean()` 에 `.distinct()` 하나면 모든 호출부가 해결됩니다 |
| E-11 | `service/ExhibitionImageProcessor.java:211` | remove.bg 가 꺼져 있거나 실패하면 `ImageInspector.read` 로 원본을 디코딩하는데, `MAX_PIXELS` 가 4천만이라 `TYPE_INT_ARGB` 로 약 160MB 입니다. `ExhibitionAsyncConfig` javadoc 이 가정한 48MB 와 다릅니다. **무료 쿼터 50회를 넘기면 이후 모든 업로드가 이 경로**라 예외적 상황이 아닙니다. C-02(변환 순서)·C-03(업로드 시점 검증)과 함께 숫자를 다시 맞춰야 합니다 |
| E-12 | `service/ExhibitionService.java:91` | 장식장 통째 삭제가 URL 목록을 **상한 없이·중복 제거 없이** `ImageCleanup` 에 넘깁니다. 굿즈가 많으면 거대한 `IN` 절이 되고(플랜 캐시 흔들림), 큐가 넘치면 인라인 폴백으로 **HTTP 요청 스레드에서** 전부 처리됩니다 |
| E-13 | `controller/ExhibitionController.java:221` | `listItems` 만 `authUser.getUserId()` 를 직접 씁니다(다른 조회는 전부 `viewerId(authUser)`). `/{id}` 는 공개인데 `/{id}/items` 는 비공개라, **비로그인 사용자는 상세는 보는데 "더보기" 에서 401** 을 받습니다. 나중에 이 경로를 공개로 열면 그 줄이 바로 NPE 입니다 |
| E-14 | `service/ExhibitionImageProcessor.java:174` | `exhibitionCleanupExecutor` 가 나중에 생성돼 **먼저 종료**됩니다. 이미지 작업은 아직 최대 60초 배수 시간이 남아 있어서, 그 사이 `imageCleanup.delete/deleteOrphan` 이 전부 거절돼 인라인 폴백을 탑니다 — 배수 예산을 잡아둔 바로 그 구간에 DB+저장소 왕복이 이미지 스레드로 들어옵니다. 동작은 degrade 로 끝나지만 순서가 우연에 의존합니다 |

---

## 확인했고 문제 없던 것

- **고아 회수 장부가 모든 분기에서 맞습니다** — `process` / `failKeepingSource` 의 세 `deleteOrphan`
  호출부는 (E-04 의 커밋 모호성 경로를 빼면) 방금 만든 URL 만 넘기고, 공유 가능한 URL 은 전부
  가드 있는 `deleteUnreferenced` 를 탑니다. 처리 중 삭제 · 재시도 후 삭제 · 여러 장식장 중복 배치
  모두 일관된 상태로 끝납니다
- **이벤트 페이로드에 detached 엔티티가 없습니다** — 전부 값(id · 바이트 · URL)만 넘깁니다
- **`submit` 의 `RejectedExecutionException` catch 가 실제로 동작합니다** — Spring 의
  `TaskRejectedException` 이 이를 상속합니다
- **권한**: 보관함 전 경로가 소유자 범위(`getOwned` / `findOwnedForUpdate` 의 JPQL 에 `userId` 포함 —
  남의 행은 **잠금조차 못 겁니다**), 굿즈 쓰기는 전부 `getOwnedExhibition`, `getItemOf` 가 교차 장식장 id 차단
- **비로그인**: `isOwnedBy(null)` · `visibleTo(false)` 정상, 파생 쿼리의 `IS NULL` 변환과 JPQL `= :userId`
  둘 다 빈 결과
- **커서 페이징**: `Paging.slice` 의 size+1 / `subList` / `nextCursor` 경계가 두 서비스 모두 정확하고,
  커서가 항상 소유자·장식장으로 스코프됩니다
- **트랜잭션·이벤트**: 리스너 4개 전부 `AFTER_COMMIT`, StatusWriter 는 `REQUIRES_NEW` +
  `PESSIMISTIC_WRITE` + `PENDING` 가드, `touchUpdatedAt` 은 flush 순서와 무관하게 맞습니다
- **장식장 피드(#64)**: `findRecentIds` · `getRecent` · `recent()` · `PUBLIC_GET_ENDPOINTS` 항목 모두 정확 —
  메서드 스코프 매처라 `POST /api/exhibitions` 는 인증 유지, `/me` 는 패턴에 안 걸리고, `viewerId` 사용
- **경로 조작**: `LocalImageStorage.resolve` + `StorageUrls.keyFrom` 으로 차단

---

## 고칠 순서 (제안)

| 순서 | 항목 | 이유 |
|---|---|---|
| 1 | **E-01** | 배포할 때마다 재현될 수 있고, 결과가 복구 불가 상태입니다 |
| 2 | E-03, E-04, E-05 | 각각 수 줄이고 사용자에게 보이는 고장입니다 |
| 3 | E-09, E-10, E-13 | 한 줄~몇 줄짜리입니다 |
| 4 | E-06, E-07, E-08, E-12, E-14 | 구조를 조금 건드립니다 |
| 5 | E-11 | C-02 · C-03 과 함께 메모리 예산을 한 번에 |
| — | **E-02** | D-01(배치 시점 소유 검증)과 같은 수정입니다 — **프론트가 실제 URL 로 전환한 뒤** |

---

## 리뷰 전체 마무리

| 배치 | 범위 | LOC | 건수 |
|---|---|---|---|
| A | `global/**` | 885 | 20 |
| B | `chat` | 663 | 5 |
| C | 이미지 처리·저장 | 939 | 9 |
| D | 전시 계약층 | 883 | 8 |
| E | 보관함·파이프라인 | 1,962 | 14 |
| | | **5,332** | **56** |

리뷰 이력이 없던 `global/` 부터 #49 로 막 들어온 보관함까지 전부 훑었습니다.
