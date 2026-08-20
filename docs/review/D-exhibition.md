# 배치 D — 전시 계약층 코드 리뷰

> 대상: `exhibition` 의 controller · dto · entity · repository + `ExhibitionLike*`, 19개 파일 883 LOC (기준 커밋 `2449c02`)
> 제외: `ExhibitionService`, `ExhibitionItemService`, `ExhibitionItem`, `ExhibitionItemRepository`, `ExhibitionErrorCode` 등
> — 미머지 PR #49 가 재작성한 파일들이라 배치 E 로 미뤘습니다
> 관련 이슈: #68 · 이전 배치: #60(A), #62(B), #67(C)

8건 전부 코드로 재확인했습니다. 전시 도메인이라 **수정도 제가 합니다.**

---

## 현재 상태 (2026-08-20 기준)

**아래 본문은 `2449c02` 시점의 스냅샷입니다.** 본문에 "#49 미머지" · "C-01 열려 있음" 으로 적힌 부분은 **전부 지난 얘기**입니다 — #49 내용은 머지됐고 C-01 도 #76 으로 닫혔습니다.

| # | 상태 | 처리 |
|---|---|---|
| D-01 `imageUrl` 무검증 | 🔶 **절반** | #100 — 외부 주소 차단. **소유 검증은 남음**, 아래 참고 |
| D-02 좋아요 일괄 삭제 N회 | ✅ | #84 — 벌크 쿼리 |
| D-03 인기 목록 전체 스캔 | ⏸ 보류 | 시연 규모에선 불필요. `like_count` 비정규화나 기간 필터가 필요해지면 |
| D-04 좋아요 중복이 락 경합에서 500 | ✅ | #85 — `DataAccessException` 으로 확대 |
| D-05 rotation 주석 모순 | ✅ | #85 |
| D-06 `viewerId()` 중복 구현 | ✅ | #85 — `AuthUser.idOrNull` 로 통일 |
| D-07 themeCode 화이트리스트 없음 | ✅ | #99 — 7개로 제한 |
| D-08 비로그인에 구매가·코멘트 노출 | ➖ **결정: 그대로** | 아래 참고 |

### D-01 의 남은 절반

"우리 저장소인가" 는 넣었고 **"누구 것인가" 는 못 넣었습니다.** 소유 검증을 하려면 키 경로(`images/{userId}/`)를 봐야 하는데, 시연 시드가 `uploads/seed/` 아래를 써서 그대로 넣으면 **시드 100개가 통째로 400** 이 됩니다.

시드 도구를 소유자별 경로로 옮기는 것과 같이 처리해야 합니다. **E-02 가 이 항목에 달려 있습니다.**

### D-08 은 의도된 동작으로 확정

덕질 장식장은 "내 컬렉션을 보여주는" 서비스라 구매가와 한 줄 코멘트도 전시의 일부입니다. 숨기면 비로그인 화면이 반쪽이 됩니다. **코드는 안 건드립니다.**

> **✅ 해결** · **🔶 절반** · **⏸ 보류**(이유 있음) · **➖ 조치 없음**(결정 또는 재현 불가) · **❌ 미해결**

---

## 먼저 — 배치 C 와 이어지는 부분

D-01 은 배치 C 의 **C-01(#66/#67)과 뿌리가 같습니다**. "URL 문자열을 검증 없이 받아두고,
나중에 그 URL 로 스토리지를 지운다" 는 같은 구조인데 경로가 둘로 갈립니다.

| | 삭제 경로 | 가드 | 현재 |
|---|---|---|---|
| **C-01** | `PostService` → `imageStorage.deleteByUrl` **직접** | 없음 | 🔴 열려 있음 |
| **D-01** | `ExhibitionItemService`/`ExhibitionService` → `ImageCleanup` | #49 가 추가 | 🟡 #49 머지되면 닫힘 |

`main` 의 `ImageCleanup` 은 참조 확인 없이 바로 지웁니다(`ImageCleanup.java:78-95`).
PR #49 가 여기에 `findReferencedUrls` 가드를 넣어서, **삭제 실행 직전에 그 URL 을 아직 가리키는
행이 있는지 다시 확인**합니다. 그래서 남의 URL 을 내 장식장에 넣었다가 지워도 원래 주인의 굿즈가
아직 참조 중이면 파일이 남습니다.

**결론: #49 를 머지하면 D-01 의 삭제 악용은 닫힙니다.** 반면 C-01 은 `ImageCleanup` 을 아예
안 거치는 경로라 그대로 남습니다 — 별도 대응이 필요합니다.

---

## 🟠 D-01. `imageUrl` 을 아무 검증 없이 받습니다

**위치** `dto/request/AddItemRequest.java:20`

```java
@NotBlank @Size(max = ExhibitionItem.IMAGE_URL_MAX_LENGTH) String imageUrl
```

검증이 "비어 있지 않고 500자 이하" 뿐입니다. **URL 형식인지, 우리 스토리지 주소인지,
호출자 것인지 아무것도 확인하지 않습니다.**

### 1) 삭제 악용 — #49 가 막습니다

`GET /api/exhibitions/{id}` 가 공개라 남의 굿즈 `imageUrl` 을 그대로 읽을 수 있고,
그걸 `POST /api/exhibitions/{내 장식장}/items` 로 넣은 뒤 삭제하면 `main` 에서는
원래 주인의 파일이 지워집니다.

앞서 적었듯 **#49 의 참조 가드가 이 경로를 닫습니다.** 확인했습니다.

### 2) 남는 문제 — #49 와 무관합니다

- **`javascript:` · `data:` 값이 그대로 통과합니다.** 프론트가 `<img src>` 에 그대로 넣는
  구조라, 저장된 값이 그대로 렌더링됩니다. `@NotBlank` 만으로는 못 막습니다.
- **남의 이미지를 내 장식장에 띄울 수 있습니다.** 파일이 지워지지는 않아도, 남의 굿즈 사진을
  내 것처럼 전시하는 건 그대로 됩니다. 참조가 늘어나면 **원래 주인이 자기 굿즈를 지워도 파일이
  안 지워집니다**(#49 가드가 "아직 참조 중" 으로 판단) — 주인 입장에서는 지웠는데 남아 있는 셈입니다.

### 대응

`POST /items` 는 원래 **보관함/업로드가 만들어준 URL** 을 받는 자리입니다.
`GoodsImage`(보관함, #49)에 그 URL 이 **내 것으로** 존재하는지 확인하면 위 셋이 한 번에 정리됩니다.
#49 가 보관함을 도입하므로 그때 같이 넣는 게 자연스럽습니다 — 배치 E 에서 다루겠습니다.
당장은 최소한 URL 스킴 화이트리스트(`http`/`https` + 우리 base URL 접두사)라도 두는 게 좋겠습니다.

---

## 🟡 D-02. 좋아요 일괄 삭제가 행 수만큼 DELETE 를 발행합니다

**위치** `repository/ExhibitionLikeRepository.java:20`

```java
void deleteByExhibitionId(Long exhibitionId);   // 파생 delete
```

파생 `deleteBy...` 는 Spring Data 가 **행을 전부 SELECT 해서 영속성 컨텍스트에 올린 뒤
한 건씩 DELETE** 합니다. `ExhibitionService.delete` 가 요청 트랜잭션 안에서 이걸 부르므로,
좋아요가 많은 장식장을 지우면 그만큼의 엔티티가 세션에 쌓이고 DELETE 문이 그 수만큼 나갑니다.

같은 파일의 `deleteByExhibitionIdAndUserId`(18행)도 같은 형태지만, 그쪽은 유니크 조합이라
많아야 한 건이어서 문제가 없습니다.

**대응** `@Modifying @Query("delete from ExhibitionLike l where l.exhibition.id = :id")`.
배치 C 의 C-06 · PR #58 3라운드와 같은 교훈입니다 — 파생 delete 는 벌크가 아닙니다.

---

## 🟡 D-03. 인기 목록이 매번 전체를 훑습니다

**위치** `repository/ExhibitionRepository.java:23`

```java
... left join ExhibitionLike l on ...
group by e.id
order by count(l.id) desc, e.id desc
```

필터가 없어서 **전체 장식장 × 좋아요 조인 → group by → 정렬** 을 다 한 뒤에야 `LIMIT` 이 걸립니다.
`Pageable` 의 limit 은 group by 아래로 못 내려갑니다.

홈 화면에서 호출되고 **비로그인 공개**라, 인증 없이 반복 호출할 수 있는 가장 비싼 엔드포인트입니다.
지금 데이터 규모에서는 문제가 아니지만, 커질 때 가장 먼저 아플 자리라 적어둡니다.

**대응 후보** `like_count` 비정규화 컬럼, 또는 기간 필터(최근 N일). 시연 규모에서는 급하지 않습니다.

---

## 🟡 D-04. 좋아요 중복 처리가 락 경합에서는 500 을 냅니다

**위치** `service/ExhibitionLikeService.java:29`

```java
} catch (DataIntegrityViolationException e) {
```

동시에 두 번 눌렀을 때 항상 duplicate-key 로 오는 건 아닙니다. 두 번째 INSERT 가 첫 트랜잭션의
인덱스 락을 기다리다가, 첫 쪽 커밋이 늦으면 **락 대기 타임아웃이나 데드락 victim** 이 됩니다.
Spring 은 이걸 `CannotAcquireLockException`(= `TransientDataAccessException`)으로 옮기는데,
이 catch 밖이라 그대로 500 이 나갑니다. javadoc 이 약속한 "여러 번 호출해도 결과가 같습니다" 가
깨집니다.

**이건 추측이 아니라 이미 우리 저장소에서 관측된 형태입니다** — PR #58(유저 검색 내역)이 3라운드에서
정확히 같은 패턴으로 `TransientDataAccessException` 을 맞고 재시도를 넣었습니다.
같은 REQUIRES_NEW writer + 유니크 제약 구조라 여기도 같은 일이 납니다.

**대응** catch 를 넓히거나(`DataAccessException` 으로 잡고 `existsBy...` 재확인은 유지),
#58 처럼 짧은 재시도를 두는 방법. 재확인 로직이 이미 있어서 catch 범위만 넓혀도 동작은 맞습니다.

---

## 🟡 D-05. rotation 주석이 수정 경로와 모순됩니다

**위치** `dto/request/PlacementRequest.java:25`

```java
/**
 * <p><b>보내지 않으면 0(회전 없음)</b>으로 저장합니다.
 */
@DecimalMin("-180.0") @DecimalMax("180.0") Double rotation
```

이 설명은 **생성에서만 맞습니다.** 같은 record 가 `UpdatePositionRequest` 에도 들어가는데,
거기서는 `ExhibitionItem.moveTo` 가 rotation 이 null 이면 **기존 각도를 그대로 둡니다**
(`ExhibitionItem.java:130-143`).

이 구분은 실제로 겪은 버그를 고치면서 만든 규칙입니다 — 회전 UI 가 없는 화면은 현재 각도를 알 수도
없어서 같이 못 보내는데, 그 화면이 단순 드래그만 해도 사용자가 돌려둔 각도가 0 으로 지워졌었습니다.
컨트롤러 문서(`ExhibitionController.java:186`)와 엔티티 주석에는 정정이 반영됐는데,
**이 DTO 만 옛 설명이 남았습니다.**

DTO 만 보고 클라이언트를 짜는 사람이 "안 보내면 0" 으로 구현하면 그 버그가 되살아납니다.
null 은 "지정하지 않음" 이고 생성/수정에서 규칙이 갈린다고 고쳐야 합니다.

---

## 🟢 D-06 ~ D-08. 나머지

| # | 위치 | 내용 |
|---|---|---|
| D-06 | `controller/ExhibitionController.java:101` | `viewerId(authUser)` 가 **이미 있는** `AuthUser.idOrNull`(`global/auth/AuthUser.java:28`, 정확히 이 용도로 만들어짐)을 재구현했습니다. `ExhibitionSearchController.java:44` 에도 같은 삼항식이 인라인돼 있어 규칙이 세 벌입니다. 비로그인 처리 방식이 바뀌면 세 군데를 찾아야 합니다 |
| D-07 | `dto/request/CreateExhibitionRequest.java:12` | `themeCode` 검증이 길이뿐입니다(`UpdateExhibitionRequest` 도 동일). 엔티티도 null/blank 만 막아서, 오타나 아무 문자열이 저장되고 **공개 상세·목록 응답에 그대로 나갑니다** — 프론트가 배경을 못 찾는 장식장이 만들어지고 서버는 그걸 알 방법이 없습니다. 프리셋 enum 이나 허용 목록이 필요합니다 |
| D-08 | `dto/response/ExhibitionItemResponse.java:25` | **구매가(`price`)와 코멘트가 비로그인 상세 응답에 그대로 나갑니다.** 뷰어에 따라 걸러지는 건 *상태*(`ItemStatus.visibleTo`)뿐이고 필드 마스킹은 없습니다. 수집 기록 성격상 구매가를 주인만 보게 할 생각이었다면 지금 새고 있고, 공개가 의도라면 `@Operation` 에 적어두는 게 좋겠습니다 — 지금은 어느 쪽이 의도인지 코드에 기록이 없습니다 |

---

## 확인했고 문제 없던 것

리뷰 중점으로 지정했던 항목들입니다.

- **비로그인 처리는 견고합니다.** 공개 3종 전부 `viewerId(authUser)` 를 태우고,
  `Exhibition.isOwnedBy(null)` 은 false, `existsByExhibitionIdAndUserId(id, null)` 은 파생 쿼리라
  `user_id IS NULL` 로 번역되며, JPQL `findLikedExhibitionIds(null, …)` 는 빈 목록입니다.
  `ExhibitionRepositoryTest:226` 이 이걸 고정하고 있습니다.
- **`GET /{id}/items` 가 `authUser.getUserId()` 를 바로 쓰는 건 NPE 가 아닙니다** —
  공개 목록에는 `/api/exhibitions/{id:[0-9]+}` 만 있고 그리드는 인증 필수입니다.
  `ExhibitionControllerSecurityTest:133` 이 익명 401 을 명시적으로 검증합니다.
- **권한 검증은 모든 변경 경로에서 `getOwnedExhibition` 을 탑니다.** 컨트롤러→서비스 인자 순서도 전부 맞습니다.
- **좋아요 멱등 설계 자체는 맞습니다** — REQUIRES_NEW writer + 유니크 제약 catch + 결과 재확인으로
  UK 와 FK 를 구분합니다(D-04 는 catch 범위 문제일 뿐 구조는 정확합니다).
  package-private `@Component` 도 CGLIB 프록시가 정상적으로 걸립니다.
- **LIKE 이스케이프가 실제로 동작합니다** — 텍스트 블록의 `escape '\\'` 는 백슬래시 하나로 해석되고,
  MySQLDialect 가 리터럴을 인라인할 때 다시 두 배로 만듭니다.
- **회전 경계값(−180/180 포함, null = 미지정)은 명세대로 동작합니다.** `PlacementRequestTest` 가 고정합니다.
  (D-05 는 동작이 아니라 문서 문제입니다)

---

## 고칠 순서 (제안)

| 순서 | 항목 | 이유 |
|---|---|---|
| 1 | D-05 | 주석 한 곳이고, 잘못 읽으면 이미 고친 버그가 되살아납니다 |
| 2 | D-04 | 이미 다른 PR 에서 관측된 실패 형태입니다 |
| 3 | D-02, D-06 | 각각 한 줄짜리입니다 |
| 4 | D-08, D-07 | 의도 확인이 먼저 필요합니다 |
| 5 | D-01 | 보관함 소유 검증은 배치 E(#49 머지 후)에서 |
| 6 | D-03 | 시연 규모에서는 급하지 않습니다 |

---

## 남은 배치

| 배치 | 범위 | 상태 |
|---|---|---|
| E | 보관함 · 파이프라인 오케스트레이션 (약 1,950 LOC) | #49 머지 후 |

배치 A~D 로 제 도메인 중 #49 와 무관한 부분(3,332 LOC)은 전부 훑었습니다.
