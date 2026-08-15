package com.duckspace.domain.exhibition.service;

import com.duckspace.domain.exhibition.entity.ExhibitionItem;
import com.duckspace.domain.exhibition.entity.ItemStatus;
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
        pending(itemId).ifPresent(item -> item.markReady(imageUrl));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long itemId, String originalImageUrl) {
        pending(itemId).ifPresent(item -> item.markFailed(originalImageUrl));
    }

    /**
     * 아직 {@code PENDING} 인 아이템만 돌려줍니다. <b>결과를 쓸 자격이 있는지</b>를 확인하는 것입니다.
     *
     * <p>같은 아이템에 대한 처리가 둘 이상 떠 있을 수 있습니다(빠른 재시도 등). 그때 늦게 끝난
     * 쪽이 먼저 끝난 쪽의 결과를 덮으면, 이미 {@code READY} 인 굿즈가 <b>이미 지워진 원본을
     * 가리키는 {@code FAILED}</b> 로 되돌아갑니다. 상태가 이미 바뀌었다면 그 결과는 버립니다.
     *
     * <p>처리 중에 사용자가 삭제한 경우도 여기서 같이 걸러집니다.
     */
    private Optional<ExhibitionItem> pending(Long itemId) {
        Optional<ExhibitionItem> found = exhibitionItemRepository.findById(itemId);
        if (found.isEmpty()) {
            log.info("상태를 기록할 아이템이 이미 없습니다. itemId={}", itemId);
            return Optional.empty();
        }
        ExhibitionItem item = found.get();
        if (item.getStatus() != ItemStatus.PENDING) {
            log.info("이미 처리가 끝난 아이템이라 결과를 버립니다. itemId={}, status={}",
                    itemId, item.getStatus());
            return Optional.empty();
        }
        return found;
    }
}
