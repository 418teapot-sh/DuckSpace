package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.repository.ExhibitionItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 이미지 처리 결과만 <b>짧은 트랜잭션</b>으로 기록합니다.
 *
 * <p>{@link ExhibitionImageProcessor} 와 별도 빈인 이유는 두 가지입니다.
 *
 * <ol>
 *   <li><b>트랜잭션이 외부 API 왕복을 물지 않도록.</b> remove.bg 응답은 1~3초, 느리면 타임아웃인
 *       60초까지 갑니다. 그 시간 내내 DB 커넥션을 붙잡고 있으면 커넥션 풀이 먼저 마릅니다.
 *       무거운 작업은 트랜잭션 밖에서 하고, 결과를 쓸 때만 여기로 들어옵니다.
 *   <li><b>자기 호출은 프록시를 타지 않아서.</b> 같은 빈 안의 메서드를 부르면
 *       {@code @Transactional} 이 적용되지 않습니다.
 * </ol>
 *
 * <p>{@code REQUIRES_NEW} 인 이유는 호출부가 트랜잭션 없는 백그라운드 스레드라, 여기서
 * 새로 열어야 하기 때문입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ExhibitionItemStatusWriter {

    private final ExhibitionItemRepository exhibitionItemRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReady(Long itemId, String imageUrl) {
        find(itemId).ifPresent(item -> item.markReady(imageUrl));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long itemId, String originalImageUrl) {
        find(itemId).ifPresent(item -> item.markFailed(originalImageUrl));
    }

    /** 처리 중에 사용자가 삭제했을 수 있습니다. 실패가 아니라 정상 흐름이라 조용히 넘어갑니다. */
    private Optional<ExhibitionItem> find(Long itemId) {
        Optional<ExhibitionItem> item = exhibitionItemRepository.findById(itemId);
        if (item.isEmpty()) {
            log.info("상태를 기록할 아이템이 이미 없습니다. itemId={}", itemId);
        }
        return item;
    }
}
