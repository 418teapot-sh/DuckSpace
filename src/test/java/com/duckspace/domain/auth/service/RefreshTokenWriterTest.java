package com.duckspace.domain.auth.service;

import com.duckspace.domain.auth.entity.RefreshToken;
import com.duckspace.domain.auth.repository.RefreshTokenRepository;
import com.duckspace.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PR #12 리뷰에서 지적된 버그의 재현/검증용 테스트입니다.
 *
 * <p>같은 트랜잭션·세션 안에서 saveAndFlush가 유니크 제약 위반으로 실패하면, 실패한 엔티티가
 * 세션에 여전히 남아 있는 상태라 이후 같은 세션에서의 조회가 auto-flush를 유발해
 * {@code AssertionFailure}로 죽습니다(mock으로는 재현되지 않고 실제 Hibernate 세션이 있어야 드러남).
 * {@link RefreshTokenWriter}는 INSERT를 REQUIRES_NEW 트랜잭션으로 분리해서, 실패해도
 * 호출부 세션은 오염되지 않아야 합니다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, RefreshTokenWriter.class})
@ActiveProfiles("test")
class RefreshTokenWriterTest {

    @Autowired
    private RefreshTokenWriter refreshTokenWriter;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("INSERT가 유니크 제약에 걸려도 호출부 세션은 오염되지 않아 재조회가 정상 동작한다")
    void insert가_실패해도_이어서_조회할_수_있다() {
        refreshTokenWriter.insert(1L, "hash-a");

        assertThrows(DataIntegrityViolationException.class,
                () -> refreshTokenWriter.insert(1L, "hash-b"));

        // 세션이 오염됐다면(고쳐지기 전 버그) 이 줄에서 AssertionFailure가 납니다.
        Optional<RefreshToken> found = refreshTokenRepository.findByUserId(1L);

        assertThat(found).isPresent();
        assertThat(found.get().getTokenHash()).isEqualTo("hash-a");
    }
}
