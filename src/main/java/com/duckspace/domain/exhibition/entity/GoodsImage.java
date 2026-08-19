package com.duckspace.domain.exhibition.entity;

import com.duckspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

/**
 * 보관함의 굿즈 사진. <b>장식장과 무관하게 유저가 소유</b>합니다.
 *
 * <p>기획 플로우가 "찍은 사진을 누끼 따서 보관함에 모아두고 → 원하는 장식장에 배치" 라서,
 * 이미지의 소유를 굿즈({@link ExhibitionItem})가 아니라 유저에게 둡니다. 배치는 보관함
 * 이미지의 URL 을 {@code POST /items} 로 넘기는 것뿐이라 별도 연결 테이블이 없습니다.
 *
 * <p>처리 상태는 굿즈와 완전히 같은 의미라 {@link ItemStatus} 를 공유합니다.
 * {@code FAILED} 일 때 {@code imageUrl} 에 원본 주소가 들어가는 규칙도 같습니다(재시도용).
 */
@Entity
@Table(
        name = "goods_image",
        indexes = {
                // 보관함 조회가 user_id 기준 최신순 커서라 (user_id, id) 로 둡니다.
                @Index(name = "idx_goods_image_user", columnList = "user_id, id"),
                // 공유 이미지 삭제 보호가 URL 로 소유를 확인합니다. 없으면 삭제마다 풀스캔입니다.
                @Index(name = "idx_goods_image_image_url", columnList = "image_url")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsImage extends BaseTimeEntity {

    public static final int IMAGE_URL_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** 처리된(누끼) 이미지 주소. 처리 전엔 null, 실패 시엔 원본 주소가 들어갑니다. */
    @Column(name = "image_url", length = IMAGE_URL_MAX_LENGTH)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false)
    private ItemStatus status;

    public GoodsImage(Long userId) {
        this.userId = userId;
        this.status = ItemStatus.PENDING;
    }

    public boolean belongsTo(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    /** 재시도로 다시 처리 대기에 들어갑니다. 원본 주소는 재처리에 필요하니 그대로 둡니다. */
    public void markPending() {
        this.status = ItemStatus.PENDING;
    }

    public void markReady(String imageUrl) {
        this.imageUrl = imageUrl;
        this.status = ItemStatus.READY;
    }

    /** 원본이라도 남겨서 사용자가 올린 사진이 사라지지 않게 합니다. (굿즈와 같은 규칙) */
    public void markFailed(String originalImageUrl) {
        this.imageUrl = originalImageUrl;
        this.status = ItemStatus.FAILED;
    }
}
