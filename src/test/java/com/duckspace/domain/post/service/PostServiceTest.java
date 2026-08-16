package com.duckspace.domain.post.service;

import com.duckspace.domain.post.dto.request.CasualPostRequest;
import com.duckspace.domain.post.dto.request.ExchangePostRequest;
import com.duckspace.domain.post.dto.request.OfferedItemRequest;
import com.duckspace.domain.post.dto.request.WantedItemRequest;
import com.duckspace.domain.post.dto.response.ExchangePostSummaryResponse;
import com.duckspace.domain.post.dto.response.PostDetailResponse;
import com.duckspace.domain.post.entity.ExchangeApplication;
import com.duckspace.domain.post.entity.ExchangeApplicationStatus;
import com.duckspace.domain.post.entity.ExchangeDetail;
import com.duckspace.domain.post.entity.ItemCondition;
import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.entity.TradeItem;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.CommentRepository;
import com.duckspace.domain.post.repository.ExchangeApplicationRepository;
import com.duckspace.domain.post.repository.ExchangeDetailRepository;
import com.duckspace.domain.post.repository.PostHashtagRepository;
import com.duckspace.domain.post.repository.PostImageRepository;
import com.duckspace.domain.post.repository.PostLikeRepository;
import com.duckspace.domain.post.repository.PostRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, postImageRepository, postHashtagRepository,
                exchangeDetailRepository, exchangeApplicationRepository, tradeItemRepository,
                postLikeRepository, commentRepository, userRepository);
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
            given(exchangeDetailRepository.findById(1L)).willReturn(Optional.of(detail));

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
            given(exchangeDetailRepository.findById(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationRepository.findByPostIdAndStatus(1L, ExchangeApplicationStatus.ACCEPTED))
                    .willReturn(Optional.of(application));

            postService.completeExchange(1L, 10L);

            assertThat(detail.isCompleted()).isTrue();
            assertThat(application.getStatus()).isEqualTo(ExchangeApplicationStatus.COMPLETED);
        }

        @Test
        void 이미_완료된_글이면_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            detail.complete();
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findById(1L)).willReturn(Optional.of(detail));

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
}
