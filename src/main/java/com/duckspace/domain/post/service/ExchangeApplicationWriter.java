package com.duckspace.domain.post.service;

import com.duckspace.domain.post.entity.ExchangeApplication;
import com.duckspace.domain.post.entity.ExchangeApplicationStatus;
import com.duckspace.domain.post.exception.PostErrorCode;
import com.duckspace.domain.post.repository.ExchangeApplicationRepository;
import com.duckspace.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;

/**
 * accept()/reject()/cancel()/complete()가 lockPost()로 기다렸다 얻은 시점의 최신 상태를 다시 확인할 때 씁니다.
 *
 * <p>lockPost()를 거친 뒤에도, 같은 트랜잭션에서 일반 SELECT로 다시 조회하면 MySQL REPEATABLE READ의
 * 스냅샷(트랜잭션 첫 읽기 시점에 고정됨) 때문에 lockPost()가 기다려준 다른 트랜잭션의 커밋을 못 볼 수
 * 있습니다. {@code REQUIRES_NEW}로 새 트랜잭션을 열어야 그 시점의 최신 커밋 상태를 정확히 봅니다.
 *
 * <p><b>별도 빈인 이유:</b> 같은 빈 안에서 호출하면 프록시를 타지 않아 {@code REQUIRES_NEW}가 적용되지 않습니다.
 */
@Component
@RequiredArgsConstructor
class ExchangeApplicationWriter {

    private static final EnumSet<ExchangeApplicationStatus> IN_PROGRESS_OR_DONE =
            EnumSet.of(ExchangeApplicationStatus.ACCEPTED, ExchangeApplicationStatus.COMPLETED);

    private final ExchangeApplicationRepository exchangeApplicationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ExchangeApplicationStatus currentStatus(Long applicationId) {
        return exchangeApplicationRepository.findById(applicationId)
                .map(ExchangeApplication::getStatus)
                .orElseThrow(() -> new BusinessException(PostErrorCode.EXCHANGE_APPLICATION_NOT_FOUND));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean existsAcceptedOrCompleted(Long postId) {
        return exchangeApplicationRepository.existsByPostIdAndStatusIn(postId, IN_PROGRESS_OR_DONE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ExchangeApplication> findAcceptedByPostId(Long postId) {
        return exchangeApplicationRepository.findByPostIdAndStatus(postId, ExchangeApplicationStatus.ACCEPTED);
    }
}
