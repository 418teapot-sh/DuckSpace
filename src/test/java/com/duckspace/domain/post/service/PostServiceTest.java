package com.duckspace.domain.post.service;

import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.post.dto.request.CasualPostRequest;
import com.duckspace.domain.post.dto.request.ExchangePostRequest;
import com.duckspace.domain.post.dto.request.OfferedItemRequest;
import com.duckspace.domain.post.dto.request.WantedItemRequest;
import com.duckspace.domain.post.dto.response.CasualPostSummaryResponse;
import com.duckspace.domain.post.dto.response.ExchangePostSummaryResponse;
import com.duckspace.domain.post.dto.response.PostDetailResponse;
import com.duckspace.domain.post.entity.ExchangeApplication;
import com.duckspace.domain.post.entity.ExchangeApplicationStatus;
import com.duckspace.domain.post.entity.ExchangeDetail;
import com.duckspace.domain.post.entity.ItemCondition;
import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.entity.PostImage;
import com.duckspace.domain.post.entity.TradeItem;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.CommentRepository;
import com.duckspace.domain.post.repository.ExchangeApplicationRepository;
import com.duckspace.domain.post.repository.ExchangeDetailRepository;
import com.duckspace.domain.post.repository.PendingPostImageRepository;
import com.duckspace.domain.post.repository.PostHashtagRepository;
import com.duckspace.domain.post.repository.PostImageRepository;
import com.duckspace.domain.post.repository.PostLikeRepository;
import com.duckspace.domain.post.repository.PostRepository;
import com.duckspace.domain.post.repository.PostThumbnail;
import com.duckspace.domain.post.repository.TradeItemRepository;
import com.duckspace.domain.user.repository.UserRepository;
import com.duckspace.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostImageRepository postImageRepository;
    @Mock
    private PostHashtagRepository postHashtagRepository;
    @Mock
    private ExchangeDetailRepository exchangeDetailRepository;
    @Mock
    private ExchangeApplicationRepository exchangeApplicationRepository;
    @Mock
    private TradeItemRepository tradeItemRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ExchangeApplicationWriter exchangeApplicationWriter;
    @Mock
    private PendingPostImageRepository pendingPostImageRepository;
    @Mock
    private ImageStorage imageStorage;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, postImageRepository, postHashtagRepository,
                exchangeDetailRepository, exchangeApplicationRepository, tradeItemRepository,
                postLikeRepository, commentRepository, userRepository, exchangeApplicationWriter,
                pendingPostImageRepository, imageStorage);
    }

    private Post casualPost(Long id, Long userId) {
        Post post = Post.createCasual(userId, "본문");
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private Post exchangePost(Long id, Long userId) {
        Post post = Post.createExchange(userId, "제목", "본문");
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    @Nested
    @DisplayName("createCasual 메서드는")
    class CreateCasual {

        @Test
        void 글과_사진_해시태그를_함께_저장한다() {
            given(postRepository.save(any(Post.class))).willAnswer(invocation -> {
                Post post = invocation.getArgument(0);
                ReflectionTestUtils.setField(post, "id", 1L);
                return post;
            });

            Long postId = postService.createCasual(10L,
                    new CasualPostRequest("본문", List.of("a.png", "b.png"), List.of("귀엽다")));

            assertThat(postId).isEqualTo(1L);
            verify(postImageRepository, times(2)).save(any());
            verify(postHashtagRepository, times(1)).save(any());
        }

        @Test
        void 이미지를_쓰면_대기중_표시를_지운다() {
            // PostImageService.upload가 남겨둔 "아직 안 쓰였다" 표시를, 실제로 글에 담기면 지워야
            // PendingPostImageCleaner가 방금 쓴 이미지까지 고아로 오인해 지우지 않습니다.
            given(postRepository.save(any(Post.class))).willAnswer(invocation -> {
                Post post = invocation.getArgument(0);
                ReflectionTestUtils.setField(post, "id", 1L);
                return post;
            });

            postService.createCasual(10L, new CasualPostRequest("본문", List.of("a.png", "b.png"), null));

            verify(pendingPostImageRepository).deleteByImageUrlIn(List.of("a.png", "b.png"));
        }

        @Test
        void 이미지가_없으면_대기중_표시를_건드리지_않는다() {
            given(postRepository.save(any(Post.class))).willAnswer(invocation -> {
                Post post = invocation.getArgument(0);
                ReflectionTestUtils.setField(post, "id", 1L);
                return post;
            });

            postService.createCasual(10L, new CasualPostRequest("본문", null, null));

            verify(pendingPostImageRepository, never()).deleteByImageUrlIn(any());
        }

        @Test
        void content가_빈_문자열이면_예외() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.createCasual(10L, new CasualPostRequest(" ", null, null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.CONTENT_REQUIRED);
            verify(postRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("listCasual 메서드는")
    class ListCasual {

        @Test
        void 키워드의_LIKE_와일드카드를_이스케이프해서_넘긴다() {
            given(postRepository.search(any(), any(), any(), any(), any())).willReturn(List.of());

            postService.listCasual("50%_off", null, null, null);

            verify(postRepository).search(any(), any(), eq("50\\%\\_off"), any(), any());
        }

        @Test
        void authorId를_그대로_리포지토리에_넘긴다() {
            given(postRepository.search(any(), any(), any(), any(), any())).willReturn(List.of());

            postService.listCasual(null, null, null, 42L);

            verify(postRepository).search(any(), any(), any(), eq(42L), any());
        }

        @Test
        void keyword와_authorId를_동시에_넘기면_둘_다_그대로_전달된다() {
            given(postRepository.search(any(), any(), any(), any(), any())).willReturn(List.of());

            postService.listCasual("치이카와", null, null, 42L);

            verify(postRepository).search(any(), any(), eq("치이카와"), eq(42L), any());
        }

        @Test
        @DisplayName("대표 이미지를 thumbnailUrl 로 실어 보낸다 — 사진 없는 글은 null")
        void 대표_이미지를_실어보낸다() {
            // 이게 없어서 잡담 목록에 사진이 안 보였습니다. 프론트는 마이페이지에서만
            // 카드마다 상세 API 를 불러 메우고 있었고(그것도 N+1), 잡담 목록엔 그 보완이
            // 없어서 클릭 전까지 아무 사진도 없었습니다.
            Post withImage = casualPost(1L, 10L);
            Post withoutImage = casualPost(2L, 10L);

            given(postRepository.search(any(), any(), any(), any(), any()))
                    .willReturn(List.of(withImage, withoutImage));
            given(userRepository.findNicknamesByIds(List.of(10L))).willReturn(Map.of(10L, "글쓴이"));
            given(postImageRepository.findThumbnails(List.of(1L, 2L), PostImage.THUMBNAIL_SORT_ORDER))
                    .willReturn(List.of(thumbnail(1L, "https://img/first.png")));

            List<CasualPostSummaryResponse> result = postService.listCasual(null, null, null, null);

            assertThat(result).extracting(CasualPostSummaryResponse::thumbnailUrl)
                    .as("사진이 있는 글만 값이 차고, 없는 글은 null 이어야 합니다")
                    .containsExactly("https://img/first.png", null);
        }

        @Test
        @DisplayName("글이 몇 개든 이미지 조회는 한 번만 나간다")
        void 이미지_조회는_한_번만() {
            // 글마다 따로 조회하면 목록 크기만큼 쿼리가 나갑니다. 지금 프론트가 하고 있던
            // 방식이 정확히 그것이라, 서버에서 같은 실수를 반복하지 않도록 못박습니다.
            List<Post> posts = List.of(
                    casualPost(1L, 10L), casualPost(2L, 10L), casualPost(3L, 11L));

            given(postRepository.search(any(), any(), any(), any(), any())).willReturn(posts);
            given(userRepository.findNicknamesByIds(any())).willReturn(Map.of(10L, "A", 11L, "B"));
            given(postImageRepository.findThumbnails(any(), anyInt())).willReturn(List.of());

            postService.listCasual(null, null, null, null);

            verify(postImageRepository, times(1))
                    .findThumbnails(List.of(1L, 2L, 3L), PostImage.THUMBNAIL_SORT_ORDER);
            verify(postImageRepository, never()).findByPost_IdOrderBySortOrderAsc(any());
        }

        private PostThumbnail thumbnail(Long postId, String imageUrl) {
            return new PostThumbnail() {
                @Override
                public Long getPostId() {
                    return postId;
                }

                @Override
                public String getImageUrl() {
                    return imageUrl;
                }
            };
        }
    }

    @Nested
    @DisplayName("createExchange 메서드는")
    class CreateExchange {

        @Test
        void 교환상세와_품목_두_건을_저장한다() {
            given(postRepository.save(any(Post.class))).willAnswer(invocation -> {
                Post post = invocation.getArgument(0);
                ReflectionTestUtils.setField(post, "id", 1L);
                return post;
            });
            given(exchangeDetailRepository.save(any(ExchangeDetail.class))).willAnswer(invocation -> invocation.getArgument(0));

            ExchangePostRequest request = new ExchangePostRequest(
                    "제목", "본문",
                    new OfferedItemRequest("offer.png", "인형", "브랜드A", ItemCondition.UNOPENED),
                    new WantedItemRequest("want.png", "키링", "브랜드B"),
                    "직거래만 가능", "치이카와 in 성수", "260809", "12시부터14시까지");

            Long postId = postService.createExchange(10L, request);

            assertThat(postId).isEqualTo(1L);
            verify(tradeItemRepository, times(2)).save(any());
        }

        @Test
        void offeredItem과_wantedItem의_이미지_대기중_표시를_지운다() {
            given(postRepository.save(any(Post.class))).willAnswer(invocation -> {
                Post post = invocation.getArgument(0);
                ReflectionTestUtils.setField(post, "id", 1L);
                return post;
            });
            given(exchangeDetailRepository.save(any(ExchangeDetail.class))).willAnswer(invocation -> invocation.getArgument(0));

            ExchangePostRequest request = new ExchangePostRequest(
                    "제목", "본문",
                    new OfferedItemRequest("offer.png", "인형", "브랜드A", ItemCondition.UNOPENED),
                    new WantedItemRequest("want.png", "키링", "브랜드B"),
                    null, null, null, null);

            postService.createExchange(10L, request);

            verify(pendingPostImageRepository).deleteByImageUrlIn(List.of("offer.png", "want.png"));
        }

        @Test
        void wantedItem에_이미지가_없어도_offeredItem_이미지는_대기중_표시를_지운다() {
            // wantedItem.imageUrl은 선택이라 null이 흔합니다. null까지 리포지토리로 넘기면 안 됩니다.
            given(postRepository.save(any(Post.class))).willAnswer(invocation -> {
                Post post = invocation.getArgument(0);
                ReflectionTestUtils.setField(post, "id", 1L);
                return post;
            });
            given(exchangeDetailRepository.save(any(ExchangeDetail.class))).willAnswer(invocation -> invocation.getArgument(0));

            ExchangePostRequest request = new ExchangePostRequest(
                    "제목", "본문",
                    new OfferedItemRequest("offer.png", "인형", "브랜드A", ItemCondition.UNOPENED),
                    new WantedItemRequest(null, "키링", null),
                    null, null, null, null);

            postService.createExchange(10L, request);

            verify(pendingPostImageRepository).deleteByImageUrlIn(List.of("offer.png"));
        }
    }

    @Nested
    @DisplayName("listExchange 메서드는")
    class ListExchange {

        @Test
        void ExchangeDetail이_없는_글은_목록에서_건너뛴다() {
            Post postWithoutDetail = exchangePost(1L, 10L);
            given(postRepository.search(any(), any(), any(), any(), any())).willReturn(List.of(postWithoutDetail));
            given(exchangeDetailRepository.findAllById(any())).willReturn(List.of());
            given(tradeItemRepository.findByExchangeDetail_PostIdIn(any())).willReturn(List.of());

            List<ExchangePostSummaryResponse> responses = postService.listExchange(null, null, null, null);

            assertThat(responses).isEmpty();
        }

        @Test
        void authorId를_그대로_리포지토리에_넘긴다() {
            given(postRepository.search(any(), any(), any(), any(), any())).willReturn(List.of());

            postService.listExchange(null, null, null, 42L);

            verify(postRepository).search(any(), any(), any(), eq(42L), any());
        }

        @Test
        void keyword와_authorId를_동시에_넘기면_둘_다_그대로_전달된다() {
            given(postRepository.search(any(), any(), any(), any(), any())).willReturn(List.of());

            postService.listExchange("치이카와", null, null, 42L);

            verify(postRepository).search(any(), any(), eq("치이카와"), eq(42L), any());
        }
    }

    @Nested
    @DisplayName("getDetail 메서드는")
    class GetDetail {

        @Test
        void 존재하지_않는_글이면_예외() {
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.getDetail(1L, 10L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND);
        }

        @Test
        void 교환글이면_exchangeInfo를_채운다() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, "택배비 반반", "치이카와 in 성수", "260809", "12시부터14시까지");
            TradeItem offered = TradeItem.offered(detail, "offer.png", "인형", "브랜드A", ItemCondition.LIGHTLY_USED);
            TradeItem wanted = TradeItem.wanted(detail, null, "키링", null);

            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findById(1L)).willReturn(Optional.of(detail));
            given(tradeItemRepository.findByExchangeDetail_PostIdOrderBySideAsc(1L)).willReturn(List.of(offered, wanted));

            PostDetailResponse response = postService.getDetail(1L, 99L);

            assertThat(response.exchangeInfo()).isNotNull();
            assertThat(response.exchangeInfo().offeredItem().itemName()).isEqualTo("인형");
            assertThat(response.exchangeInfo().wantedItem().itemName()).isEqualTo("키링");
            assertThat(response.exchangeInfo().preferredPopupName()).isEqualTo("치이카와 in 성수");
            assertThat(response.mine()).isFalse();
            assertThat(response.imageUrls()).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateCasual 메서드는")
    class UpdateCasual {

        @Test
        void 본인_잡담글이면_수정된다() {
            Post post = casualPost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            postService.updateCasual(1L, 10L, new CasualPostRequest("수정된 본문", null, null));

            assertThat(post.getContent()).isEqualTo("수정된 본문");
        }

        @Test
        void 이미지와_해시태그를_안_보내면_기존_값을_건드리지_않는다() {
            Post post = casualPost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            postService.updateCasual(1L, 10L, new CasualPostRequest("본문만 수정", null, null));

            verify(postImageRepository, never()).deleteByPost_Id(any());
            verify(postHashtagRepository, never()).deleteByPost_Id(any());
        }

        @Test
        void content를_안_보내면_기존_값을_그대로_둔다() {
            Post post = casualPost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            postService.updateCasual(1L, 10L, new CasualPostRequest(null, List.of("new.png"), null));

            assertThat(post.getContent()).isEqualTo("본문");
        }

        @Test
        void content가_빈_문자열이면_예외() {
            Post post = casualPost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.updateCasual(1L, 10L, new CasualPostRequest(" ", null, null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.CONTENT_REQUIRED);
        }

        @Test
        void 이미지를_보내면_기존_이미지를_교체한다() {
            Post post = casualPost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            postService.updateCasual(1L, 10L, new CasualPostRequest("본문", List.of("new.png"), null));

            verify(postImageRepository).deleteByPost_Id(1L);
            verify(postImageRepository).save(any());
            verify(postHashtagRepository, never()).deleteByPost_Id(any());
        }

        @Test
        void 목록에서_빠진_이미지는_실제_파일도_지운다() {
            // 안 지우면 PostImage 행도 없고 이미 claimImages로 PendingPostImage에서도 빠진
            // 완전한 영구 고아가 됩니다 — 리뷰(Yun-pix)가 지적한 지점.
            Post post = casualPost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(postImageRepository.findByPost_IdOrderBySortOrderAsc(1L)).willReturn(List.of(
                    new PostImage(post, url("posts/10/keep.png"), 0),
                    new PostImage(post, url("posts/10/remove.png"), 1)));
            givenOurStorage();

            postService.updateCasual(1L, 10L,
                    new CasualPostRequest("본문", List.of(url("posts/10/keep.png")), null));

            verify(imageStorage).deleteByUrl(url("posts/10/remove.png"));
            verify(imageStorage, never()).deleteByUrl(url("posts/10/keep.png"));
        }

        @Test
        void 이미지를_전부_빼면_전부_지운다() {
            Post post = casualPost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(postImageRepository.findByPost_IdOrderBySortOrderAsc(1L)).willReturn(List.of(
                    new PostImage(post, url("posts/10/a.png"), 0),
                    new PostImage(post, url("posts/10/b.png"), 1)));
            givenOurStorage();

            postService.updateCasual(1L, 10L, new CasualPostRequest("본문", List.of(), null));

            verify(imageStorage).deleteByUrl(url("posts/10/a.png"));
            verify(imageStorage).deleteByUrl(url("posts/10/b.png"));
        }

        @Test
        void 남의_이미지_주소는_지우지_않는다() {
            // 남의 URL 을 내 글에 넣고 imageUrls: [] 로 PATCH 하면 그 파일이 저장소에서
            // 지워지던 구멍입니다(C-01). saveImages 가 소유를 확인하지 않아서 남의 주소가
            // 그대로 저장될 수 있으므로, 지우는 쪽에서 키 접두사로 한 번 더 거릅니다.
            Post post = casualPost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(postImageRepository.findByPost_IdOrderBySortOrderAsc(1L)).willReturn(List.of(
                    new PostImage(post, url("posts/10/mine.png"), 0),
                    new PostImage(post, url("users/42/victim-profile.png"), 1),
                    new PostImage(post, url("posts/42/victim-post.png"), 2),
                    new PostImage(post, url("exhibitions/7/victim-goods.png"), 3),
                    new PostImage(post, "https://other-host/x.png", 4)));
            givenOurStorage();

            postService.updateCasual(1L, 10L, new CasualPostRequest("본문", List.of(), null));

            verify(imageStorage).deleteByUrl(url("posts/10/mine.png"));
            verify(imageStorage, never()).deleteByUrl(url("users/42/victim-profile.png"));
            verify(imageStorage, never()).deleteByUrl(url("posts/42/victim-post.png"));
            verify(imageStorage, never()).deleteByUrl(url("exhibitions/7/victim-goods.png"));
            verify(imageStorage, never()).deleteByUrl("https://other-host/x.png");
        }

        @Test
        void 같은_이미지를_그대로_다시_보내면_지우지_않는다() {
            Post post = casualPost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(postImageRepository.findByPost_IdOrderBySortOrderAsc(1L)).willReturn(List.of(
                    new PostImage(post, "keep.png", 0)));

            postService.updateCasual(1L, 10L, new CasualPostRequest("본문", List.of("keep.png"), null));

            verify(imageStorage, never()).deleteByUrl(any());
        }

        @Test
        void 타인_글이면_예외() {
            Post post = casualPost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.updateCasual(1L, 999L, new CasualPostRequest("수정", null, null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.NOT_POST_OWNER);
        }

        @Test
        void 교환글이면_예외() {
            Post post = exchangePost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.updateCasual(1L, 10L, new CasualPostRequest("수정", null, null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.INVALID_BOARD_TYPE);
        }
    }

    @Nested
    @DisplayName("delete 메서드는")
    class Delete {

        @Test
        void 타인_글이면_예외() {
            Post post = casualPost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.delete(1L, 999L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.NOT_POST_OWNER);
            assertThat(post.isDeleted()).isFalse();
        }

        @Test
        void 본인_글이면_소프트_삭제된다() {
            Post post = casualPost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            postService.delete(1L, 10L);

            assertThat(post.isDeleted()).isTrue();
            verify(postRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("completeExchange 메서드는")
    class CompleteExchange {

        @Test
        void 정상적으로_완료_처리한다() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));

            postService.completeExchange(1L, 10L);

            assertThat(detail.isCompleted()).isTrue();
        }

        @Test
        void ACCEPTED_상태_신청이_있으면_같이_완료된다() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            ExchangeApplication application = new ExchangeApplication(1L, 20L, "인형", null, null, null, null);
            application.accept();
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationWriter.findAcceptedByPostId(1L)).willReturn(Optional.of(application));

            postService.completeExchange(1L, 10L);

            assertThat(detail.isCompleted()).isTrue();
            assertThat(application.getStatus()).isEqualTo(ExchangeApplicationStatus.COMPLETED);
            verify(exchangeApplicationRepository).save(application);
        }

        @Test
        void 이미_완료된_글이면_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            detail.complete();
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.completeExchange(1L, 10L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.EXCHANGE_ALREADY_COMPLETED);
        }

        @Test
        void 잡담글이면_예외() {
            Post post = casualPost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> postService.completeExchange(1L, 10L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.INVALID_BOARD_TYPE);
        }
    }

    private static final String BASE_URL = "https://cdn.duckspace.test";

    private static String url(String key) {
        return BASE_URL + "/" + key;
    }

    /** 실제 저장소처럼 base URL 을 떼어 키를 돌려줍니다. 남의 호스트면 null 입니다. */
    private void givenOurStorage() {
        given(imageStorage.keyOf(any())).willAnswer(invocation -> {
            String given = invocation.getArgument(0);
            return (given != null && given.startsWith(BASE_URL + "/"))
                    ? given.substring(BASE_URL.length() + 1)
                    : null;
        });
    }
}
