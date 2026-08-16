package com.duckspace.domain.exhibition.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회전 각도의 허용 범위를 계약으로 고정합니다.
 *
 * <p>프론트(Konva)가 {@code -180 ~ 0 ~ 180} 으로 다루기로 해서 그 범위를 그대로 받습니다.
 * Spring 컨텍스트 없이 검증기만 직접 돌립니다.
 */
class PlacementRequestTest {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    private PlacementRequest withRotation(Double rotation) {
        return new PlacementRequest(0.25, 0.4, 0.2, 0.3, rotation);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180.0, -90.0, -0.5, 0.0, 45.0, 179.9, 180.0})
    @DisplayName("-180 ~ 180 은 허용된다")
    void 허용_범위(double rotation) {
        assertThat(VALIDATOR.validate(withRotation(rotation))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180.1, -360.0, 180.1, 270.0})
    @DisplayName("범위를 벗어나면 거부한다")
    void 범위를_벗어나면_거부(double rotation) {
        assertThat(VALIDATOR.validate(withRotation(rotation)))
                .as("범위 밖 각도는 400 으로 걸러져야 합니다")
                .isNotEmpty();
    }

    @Test
    @DisplayName("회전을 생략하면 통과하고 0 으로 저장된다")
    void 생략하면_0() {
        PlacementRequest request = withRotation(null);

        assertThat(VALIDATOR.validate(request))
                .as("회전은 선택 항목이라 안 보내도 400 이 나면 안 됩니다")
                .isEmpty();
        assertThat(request.toPlacement().rotation()).isEqualTo(0.0);
    }
}
