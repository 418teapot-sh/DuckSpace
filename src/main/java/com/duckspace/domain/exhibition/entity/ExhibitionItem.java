package com.duckspace.domain.exhibition.entity;

import com.duckspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 장식장에 배치된 굿즈.
 *
 * <p>정해진 슬롯이 아니라 <b>자유 배치</b>입니다. 사용자가 드래그로 옮기고 크기를 조절하므로
 * 위치와 크기를 그대로 저장합니다.
 *
 * <p>좌표계는 <b>배경 대비 비율(0.0 ~ 1.0)</b>입니다. 픽셀 좌표로 저장하면 화면 크기가 다른 기기에서
 * 배치가 어긋나기 때문입니다. 예를 들어 {@code posX = 0.5} 는 배경 가로 한가운데를 뜻합니다.
 */
@Entity
@Table(
        name = "exhibition_item",
        indexes = {
                // 검색이 아이템 이름을 훑으므로 상태로 먼저 걸러냅니다.
                @Index(name = "idx_exhibition_item_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExhibitionItem extends BaseTimeEntity {

    public static final int IMAGE_URL_MAX_LENGTH = 500;
    public static final int ITEM_NAME_MAX_LENGTH = 50;
    public static final int COMMENT_MAX_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exhibition_id", nullable = false, updatable = false)
    private Exhibition exhibition;

    /** 배경 가로 대비 비율(0.0 ~ 1.0). 왼쪽 위 모서리 기준입니다. */
    @Column(name = "pos_x", nullable = false)
    private Double posX;

    /** 배경 세로 대비 비율(0.0 ~ 1.0). */
    @Column(name = "pos_y", nullable = false)
    private Double posY;

    /** 배경 가로 대비 너비 비율. */
    @Column(name = "width", nullable = false)
    private Double width;

    /** 배경 세로 대비 높이 비율. */
    @Column(name = "height", nullable = false)
    private Double height;

    /** 배경이 제거된 이미지 주소. 처리 전에는 비어 있을 수 있습니다. */
    @Column(name = "image_url", length = IMAGE_URL_MAX_LENGTH)
    private String imageUrl;

    @Column(name = "item_name", nullable = false, length = ITEM_NAME_MAX_LENGTH)
    private String itemName;

    /** 구매가(원). 선택 항목입니다. */
    @Column(name = "price")
    private Integer price;

    /** 한 줄 코멘트. */
    @Column(name = "comment", length = COMMENT_MAX_LENGTH)
    private String comment;

    // Hibernate 6+ 는 @Enumerated(STRING) 을 MySQL 네이티브 enum 컬럼으로 만듭니다.
    // ddl-auto: update 는 enum 정의를 바꾸지 않아서, 나중에 상수를 추가하면 저장이 실패합니다.
    // varchar 로 매핑해 그 문제를 피합니다. (덕톡라운지 엔티티들도 같은 방식입니다)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false)
    private ItemStatus status;

    public ExhibitionItem(Exhibition exhibition, Placement placement, String imageUrl,
                          String itemName, Integer price, String comment, ItemStatus status) {
        this.exhibition = exhibition;
        applyPlacement(placement);
        this.imageUrl = imageUrl;
        this.itemName = itemName;
        this.price = price;
        this.comment = comment;
        this.status = status;
    }

    public boolean belongsTo(Long exhibitionId) {
        return exhibition.getId().equals(exhibitionId);
    }

    /** 드래그 이동·크기 조절 결과를 반영합니다. */
    public void moveTo(Placement placement) {
        applyPlacement(placement);
    }

    private void applyPlacement(Placement placement) {
        this.posX = placement.posX();
        this.posY = placement.posY();
        this.width = placement.width();
        this.height = placement.height();
    }

    /** 배경 대비 비율로 나타낸 배치 정보. */
    public record Placement(Double posX, Double posY, Double width, Double height) {
    }

    /** 배경 제거·후처리가 끝나 화면에 표시할 수 있게 된 상태로 바꿉니다. */
    public void markReady(String imageUrl) {
        this.imageUrl = imageUrl;
        this.status = ItemStatus.READY;
    }

    /**
     * 처리에 실패한 상태로 바꿉니다.
     *
     * <p>원본 이미지는 남겨서 사용자가 올린 사진이 사라지지 않게 합니다.
     * 실패해도 화면에 "처리 실패" 로 보여주고 다시 시도할 수 있습니다.
     */
    public void markFailed(String originalImageUrl) {
        this.imageUrl = originalImageUrl;
        this.status = ItemStatus.FAILED;
    }
}
