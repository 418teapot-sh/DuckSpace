package com.duckspace.domain.post.service;

import com.duckspace.domain.post.dto.request.ExchangeApplicationRequest;
import com.duckspace.domain.post.dto.response.ExchangeApplicationResponse;
import com.duckspace.domain.post.entity.ExchangeApplication;
import com.duckspace.domain.post.entity.ExchangeApplicationStatus;
import com.duckspace.domain.post.entity.ExchangeDetail;
import com.duckspace.domain.post.entity.Post;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.CommentRepository;
import com.duckspace.domain.post.repository.ExchangeApplicationRepository;
import com.duckspace.domain.post.repository.ExchangeDetailRepository;
import com.duckspace.domain.post.repository.PostHashtagRepository;
import com.duckspace.domain.post.repository.PostImageRepository;
import com.duckspace.domain.post.repository.PostLikeRepository;
import com.duckspace.domain.post.repository.PostRepository;
import com.duckspace.domain.post.repository.TradeItemRepository;
import com.duckspace.domain.user.entity.User;
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

    private ExchangeApplicationService exchangeApplicationService;

    /** postRepository는 PostService/ExchangeApplicationService가 실제 빈 구성처럼 같은 인스턴스를 공유합니다. */
    @BeforeEach
    void setUp() {
        PostService postService = new PostService(postRepository, postImageRepository, postHashtagRepository,
                exchangeDetailRepository, tradeItemRepository, postLikeRepository, commentRepository, userRepository);
        exchangeApplicationService = new ExchangeApplicationService(
                exchangeApplicationRepository, exchangeDetailRepository, postRepository, userRepository, postService);
    }

    private Post exchangePost(Long id, Long userId) {
        Post post = Post.createExchange(userId, "제목", "본문");
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private ExchangeApplication application(Long id, Long postId, Long applicantUserId) {
        ExchangeApplication application = new ExchangeApplication(postId, applicantUserId, "인형", null, "잘 부탁드려요");
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }

    private User user(Long id, String nickname) {
        User user = User.builder().email(id + "@duckspace.com").nickname(nickname).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Nested
    @DisplayName("apply 메서드는")
    class Apply {

        @Test
        void 정상적으로_신청을_저장한다() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findById(1L)).willReturn(Optional.of(detail));
            given(exchangeApplicationRepository.save(any(ExchangeApplication.class))).willAnswer(invocation -> {
                ExchangeApplication application = invocation.getArgument(0);
                ReflectionTestUtils.setField(application, "id", 100L);
                return application;
            });

            Long applicationId = exchangeApplicationService.apply(20L, 1L,
                    new ExchangeApplicationRequest("인형", "offer.png", "잘 부탁드려요"));

            assertThat(applicationId).isEqualTo(100L);
        }

        @Test
        void 본인_글이면_예외() {
            Post post = exchangePost(1L, 10L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.apply(10L, 1L, new ExchangeApplicationRequest("인형", null, null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.SELF_APPLICATION_NOT_ALLOWED);
            verify(exchangeApplicationRepository, never()).save(any());
        }

        @Test
        void 이미_완료된_글이면_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeDetail detail = new ExchangeDetail(post, null, null, null, null);
            detail.complete();
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeDetailRepository.findById(1L)).willReturn(Optional.of(detail));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.apply(20L, 1L, new ExchangeApplicationRequest("인형", null, null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.EXCHANGE_ALREADY_COMPLETED);
            verify(exchangeApplicationRepository, never()).save(any());
        }

        @Test
        void 잡담글이면_예외() {
            Post post = Post.createCasual(10L, "본문");
            ReflectionTestUtils.setField(post, "id", 1L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.apply(20L, 1L, new ExchangeApplicationRequest("인형", null, null)));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.INVALID_BOARD_TYPE);
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
                    () -> exchangeApplicationService.listByPost(1L, 999L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.NOT_POST_OWNER);
        }

        @Test
        void 글쓴이면_신청_목록을_내려준다() {
            Post post = exchangePost(1L, 10L);
            ExchangeApplication app = application(100L, 1L, 20L);
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));
            given(exchangeApplicationRepository.findByPostIdOrderByAppliedAtDesc(1L)).willReturn(List.of(app));
            given(userRepository.findAllById(any())).willReturn(List.of(user(20L, "신청자")));

            List<ExchangeApplicationResponse> responses = exchangeApplicationService.listByPost(1L, 10L);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).applicantNickname()).isEqualTo("신청자");
        }
    }

    @Nested
    @DisplayName("listMine 메서드는")
    class ListMine {

        @Test
        void filter가_sent면_내가_신청한_목록을_조회한다() {
            given(exchangeApplicationRepository.findByApplicantUserIdOrderByAppliedAtDesc(20L)).willReturn(List.of());

            exchangeApplicationService.listMine(20L, "sent");

            verify(exchangeApplicationRepository).findByApplicantUserIdOrderByAppliedAtDesc(20L);
        }

        @Test
        void filter가_received면_받은_신청_목록을_조회한다() {
            given(exchangeApplicationRepository.findReceivedByUserId(10L)).willReturn(List.of());

            exchangeApplicationService.listMine(10L, "received");

            verify(exchangeApplicationRepository).findReceivedByUserId(10L);
        }

        @Test
        void 잘못된_filter면_예외() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.listMine(10L, "invalid"));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.INVALID_APPLICATION_FILTER);
        }
    }

    @Nested
    @DisplayName("accept 메서드는")
    class Accept {

        @Test
        void 글쓴이면_수락된다() {
            Post post = exchangePost(1L, 10L);
            ExchangeApplication app = application(100L, 1L, 20L);
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

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
            given(exchangeDetailRepository.findById(1L)).willReturn(Optional.of(detail));

            exchangeApplicationService.complete(100L, 10L);

            assertThat(app.getStatus()).isEqualTo(ExchangeApplicationStatus.COMPLETED);
            assertThat(detail.isCompleted()).isTrue();
        }

        @Test
        void ACCEPTED_상태가_아니면_예외() {
            Post post = exchangePost(1L, 10L);
            ExchangeApplication app = application(100L, 1L, 20L);
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));
            given(postRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

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
    }

    @Nested
    @DisplayName("cancel 메서드는")
    class Cancel {

        @Test
        void 신청자_본인이면_취소된다() {
            ExchangeApplication app = application(100L, 1L, 20L);
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));

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
        void 이미_수락된_신청이면_예외() {
            ExchangeApplication app = application(100L, 1L, 20L);
            app.accept();
            given(exchangeApplicationRepository.findById(100L)).willReturn(Optional.of(app));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exchangeApplicationService.cancel(100L, 20L));

            assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.EXCHANGE_APPLICATION_INVALID_STATUS);
        }
    }
}
