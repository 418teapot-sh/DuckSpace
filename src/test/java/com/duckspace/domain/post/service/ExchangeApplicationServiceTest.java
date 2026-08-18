package com.duckspace.domain.post.service;

import com.duckspace.domain.post.dto.request.ExchangeApplicationRequest;
import com.duckspace.domain.post.dto.response.ExchangeApplicationResponse;
import com.duckspace.domain.post.entity.ExchangeApplication;
import com.duckspace.domain.post.entity.ExchangeApplicationStatus;
import com.duckspace.domain.post.entity.ExchangeDetail;
import com.duckspace.domain.post.entity.ItemCondition;
import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.exhibition.image.ImageStorage;
import com.duckspace.domain.post.repository.CommentRepository;
import com.duckspace.domain.post.repository.ExchangeApplicationRepository;
import com.duckspace.domain.post.repository.ExchangeDetailRepository;
import com.duckspace.domain.post.repository.PendingPostImageRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExchangeApplicationServiceTest {

    @Mock
    private ExchangeApplicationRepository exchangeApplicationRepository;
    @Mock
    private ExchangeDetailRepository exchangeDetailRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostImageRepository postImageRepository;
    @Mock
    private PostHashtagRepository postHashtagRepository;
    @Mock
    private TradeItemRepository tradeItemRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private ExchangeApplicationWriter exchangeApplicationWriter;
    @Mock
    private PendingPostImageRepository pendingPostImageRepository;
    @Mock
    private ImageStorage imageStorage;

    private ExchangeApplicationService exchangeApplicationService;

    /** postRepository는 PostService/ExchangeApplicationService가 실제 빈 구성처럼 같은 인스턴스를 공유합니다. */
    @BeforeEach
    void setUp() {
        PostService postService = new PostService(postRepository, postImageRepository, postHashtagRepository,
                exchangeDetailRepository, exchangeApplicationRepository, tradeItemRepository,
                postLikeRepository, commentRepository, userRepository, exchangeApplicationWriter,
                pendingPostImageRepository, imageStorage);
        exchangeApplicationService = new ExchangeApplicationService(exchangeApplicationRepository,
                exchangeDetailRepository, postRepository, userRepository, postService, exchangeApplicationWriter);
    }

    private Post exchangePost(Long id, Long userId) {
        Post post = Post.createExchange(userId, "제목", "본문");
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private ExchangeApplication application(Long id, Long postId, Long applicantUserId) {
        ExchangeApplication application = new ExchangeApplication(postId, applicantUserId, "인형", null,
                "브랜드A", ItemCondition.UNOPENED, "잘 부탁드려요");
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }

    @Nested
    @DisplayName("apply 메서드는")
    class Apply {

        @Test
        void 정상적으로_신청을_저장한다() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationRepository.save(any(ExchangeApplication.class))).willAnswer(invocation -> {
                ExchangeApplication application = invocation.getArgument(0);
                ReflectionTestUtils.setField(application, "id", 100L);
                return application;
            });

            Long applicationId = exchangeApplicationService.apply(20L, 1L,
                    new ExchangeApplicationRequest("인형", "offer.png", "브랜드A", ItemCondition.UNOPENED, "잘 부탁드려요"));

            assertThat(applicationId).isEqualTo(100L);
            ArgumentCaptor<ExchangeApplication> captor = ArgumentCaptor.forClass(ExchangeApplication.class);
            verify(exchangeApplicationRepository).save(captor.capture());
            assertThat(captor.getValue().getOfferedBrand()).isEqualTo("브랜드A");
            assertThat(captor.getValue().getOfferedCondition()).isEqualTo(ItemCondition.UNOPENED);
        }

        @Test
        void 본인_글이면_예외() {
            Post post = exchangePost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.apply(10L, 1L,
                            new ExchangeApplicationRequest("인형", null, null, null, null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.SELF_APPLICATION_NOT_ALLOWED);
            verify(exchangeApplicationRepository, never()).save(any());
        }

        @Test
        void 이미_완료된_글이면_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            detail.complete();
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.apply(20L, 1L,
                            new ExchangeApplicationRequest("인형", null, null, null, null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.EXCHANGE_ALREADY_COMPLETED);
            verify(exchangeApplicationRepository, never()).save(any());
        }

        @Test
        void 잡담글이면_예외() {
            Post post = Post.createCasual(10L, "본문");
            ReflectionTestUtils.setField(post, "id", 1L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.apply(20L, 1L,
                            new ExchangeApplicationRequest("인형", null, null, null, null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.INVALID_BOARD_TYPE);
        }

        @Test
        void 이미_대기중인_신청이_있으면_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationRepository.existsByPostIdAndApplicantUserIdAndStatusIn(
                    eq(1L), eq(20L), anyCollection())).willReturn(true);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.apply(20L, 1L,
                            new ExchangeApplicationRequest("인형", null, null, null, null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.ALREADY_APPLIED);
            verify(exchangeApplicationRepository, never()).save(any());
        }

        @Test
        void 이미_수락된_신청이_있어도_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationRepository.existsByPostIdAndApplicantUserIdAndStatusIn(
                    eq(1L), eq(20L), argThat(statuses -> statuses.contains(ExchangeApplicationStatus.ACCEPTED))))
                    .willReturn(true);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.apply(20L, 1L,
                            new ExchangeApplicationRequest("인형", null, null, null, null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.ALREADY_APPLIED);
            verify(exchangeApplicationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("listByPost 메서드는")
    class ListByPost {

        @Test
        void 글쓴이가_아니면_예외() {
            Post post = exchangePost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.listByPost(1L, 999L, null, null));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.NOT_POST_OWNER);
        }

        @Test
        void 글쓴이면_신청_목록을_내려준다() {
            Post post = exchangePost(1L, 10L);
            ExchangeApplication app = application(100L, 1L, 20L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeApplicationRepository.findByPostId(eq(1L), isNull(), any(Pageable.class)))
                    .willReturn(List.of(app));
            given(userRepository.findNicknamesByIds(anyCollection())).willReturn(Map.of(20L, "신청자"));

            List<ExchangeApplicationResponse> responses = exchangeApplicationService.listByPost(1L, 10L, null, null);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).applicantNickname()).isEqualTo("신청자");
        }
    }

    @Nested
    @DisplayName("listMine 메서드는")
    class ListMine {

        @Test
        void filter가_sent면_내가_신청한_목록을_조회한다() {
            given(exchangeApplicationRepository.findByApplicantUserId(eq(20L), isNull(), any(Pageable.class)))
                    .willReturn(List.of());

            exchangeApplicationService.listMine(20L, "sent", null, null);

            verify(exchangeApplicationRepository).findByApplicantUserId(eq(20L), isNull(), any(Pageable.class));
        }

        @Test
        void filter가_received면_받은_신청_목록을_조회한다() {
            given(exchangeApplicationRepository.findReceivedByUserId(eq(10L), isNull(), any(Pageable.class)))
                    .willReturn(List.of());

            exchangeApplicationService.listMine(10L, "received", null, null);

            verify(exchangeApplicationRepository).findReceivedByUserId(eq(10L), isNull(), any(Pageable.class));
        }

        @Test
        void 잘못된_filter면_예외() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.listMine(10L, "invalid", null, null));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.INVALID_APPLICATION_FILTER);
        }
    }

    @Nested
    @DisplayName("accept 메서드는")
    class Accept {

        @Test
        void 글쓴이면_수락된다() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            ExchangeApplication app = application(100L, 1L, 20L);
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationWriter.currentStatus(100L)).willReturn(ExchangeApplicationStatus.APPLIED);

            exchangeApplicationService.accept(100L, 10L);

            assertThat(app.isAccepted()).isTrue();
        }

        @Test
        void 글쓴이가_아니면_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeApplication app = application(100L, 1L, 20L);
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.accept(100L, 999L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.NOT_POST_OWNER);
        }

        @Test
        void 이미_수락된_신청이면_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeApplication app = application(100L, 1L, 20L);
            app.accept();
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.accept(100L, 10L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.EXCHANGE_APPLICATION_INVALID_STATUS);
        }

        @Test
        void 같은_글에_이미_수락된_다른_신청이_있으면_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            ExchangeApplication app = application(100L, 1L, 20L);
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationWriter.currentStatus(100L)).willReturn(ExchangeApplicationStatus.APPLIED);
            given(exchangeApplicationWriter.existsAcceptedOrCompleted(1L)).willReturn(true);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.accept(100L, 10L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.ANOTHER_APPLICATION_ALREADY_ACCEPTED);
            assertThat(app.isApplied()).isTrue();
        }
    }

    @Nested
    @DisplayName("reject 메서드는")
    class Reject {

        @Test
        void 글쓴이면_거절된다() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            ExchangeApplication app = application(100L, 1L, 20L);
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationWriter.currentStatus(100L)).willReturn(ExchangeApplicationStatus.APPLIED);

            exchangeApplicationService.reject(100L, 10L);

            assertThat(app.getStatus()).isEqualTo(ExchangeApplicationStatus.REJECTED);
        }

        @Test
        void 수락된_신청도_거절해서_되돌릴_수_있다() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            ExchangeApplication app = application(100L, 1L, 20L);
            app.accept();
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationWriter.currentStatus(100L)).willReturn(ExchangeApplicationStatus.ACCEPTED);

            exchangeApplicationService.reject(100L, 10L);

            assertThat(app.getStatus()).isEqualTo(ExchangeApplicationStatus.REJECTED);
        }

        @Test
        void 이미_완료된_신청이면_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeApplication app = application(100L, 1L, 20L);
            app.accept();
            app.complete();
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.reject(100L, 10L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.EXCHANGE_APPLICATION_INVALID_STATUS);
        }

        @Test
        void 글쓴이가_아니면_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeApplication app = application(100L, 1L, 20L);
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.reject(100L, 999L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.NOT_POST_OWNER);
        }
    }

    @Nested
    @DisplayName("complete 메서드는")
    class Complete {

        @Test
        void 수락된_신청이면_완료되고_ExchangeDetail도_완료된다() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            ExchangeApplication app = application(100L, 1L, 20L);
            app.accept();
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationWriter.currentStatus(100L)).willReturn(ExchangeApplicationStatus.ACCEPTED);

            exchangeApplicationService.complete(100L, 10L);

            assertThat(app.getStatus()).isEqualTo(ExchangeApplicationStatus.COMPLETED);
            assertThat(detail.isCompleted()).isTrue();
        }

        @Test
        void ACCEPTED_상태가_아니면_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            ExchangeApplication app = application(100L, 1L, 20L);
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationWriter.currentStatus(100L)).willReturn(ExchangeApplicationStatus.APPLIED);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.complete(100L, 10L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.EXCHANGE_APPLICATION_INVALID_STATUS);
        }

        @Test
        void 신청자는_완료_처리할_수_없다() {
            Post post = exchangePost(1L, 10L);
            ExchangeApplication app = application(100L, 1L, 20L);
            app.accept();
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.complete(100L, 20L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.NOT_POST_OWNER);
        }

        @Test
        void 글의_교환이_이미_다른_경로로_완료됐으면_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            detail.complete();
            ExchangeApplication app = application(100L, 1L, 20L);
            app.accept();
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.complete(100L, 10L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.EXCHANGE_ALREADY_COMPLETED);
            assertThat(app.getStatus()).isEqualTo(ExchangeApplicationStatus.ACCEPTED);
        }
    }

    @Nested
    @DisplayName("cancel 메서드는")
    class Cancel {

        @Test
        void 신청자_본인이면_취소된다() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            ExchangeApplication app = application(100L, 1L, 20L);
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationWriter.currentStatus(100L)).willReturn(ExchangeApplicationStatus.APPLIED);

            exchangeApplicationService.cancel(100L, 20L);

            assertThat(app.getStatus()).isEqualTo(ExchangeApplicationStatus.CANCELLED);
        }

        @Test
        void 수락된_신청도_취소할_수_있다() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            ExchangeApplication app = application(100L, 1L, 20L);
            app.accept();
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(exchangeDetailRepository.findByPostIdForUpdate(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationWriter.currentStatus(100L)).willReturn(ExchangeApplicationStatus.ACCEPTED);

            exchangeApplicationService.cancel(100L, 20L);

            assertThat(app.getStatus()).isEqualTo(ExchangeApplicationStatus.CANCELLED);
        }

        @Test
        void 본인_신청이_아니면_예외() {
            ExchangeApplication app = application(100L, 1L, 20L);
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.cancel(100L, 999L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.NOT_APPLICATION_OWNER);
        }

        @Test
        void 이미_완료된_신청이면_예외() {
            ExchangeApplication app = application(100L, 1L, 20L);
            app.accept();
            app.complete();
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.cancel(100L, 20L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.EXCHANGE_APPLICATION_INVALID_STATUS);
        }
    }
}
