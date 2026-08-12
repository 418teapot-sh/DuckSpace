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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 슬롯에 배치된 굿즈.
 *
 * <p>한 장식장의 같은 슬롯에는 굿즈가 하나만 놓입니다. 슬롯 식별자 문자열은 프론트와 합의된 값이라
 * 서버가 값 자체를 검증하지는 않지만, <b>중복 배치는 제약으로 막습니다.</b>
 */
@Entity
@Table(
        name = "exhibition_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exhibition_item_slot",
                columnNames = {"exhibition_id", "slot_id"}
        ),
        indexes = {
                // 검색이 아이템 이름·브랜드를 훑으므로 상태로 먼저 걸러냅니다.
                @Index(name = "idx_exhibition_item_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExhibitionItem extends BaseTimeEntity {

    public static final int SLOT_ID_MAX_LENGTH = 50;
    public static final int IMAGE_URL_MAX_LENGTH = 500;
    public static final int ITEM_NAME_MAX_LENGTH = 50;
    public static final int BRAND_MAX_LENGTH = 50;
    public static final int DESCRIPTION_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exhibition_id", nullable = false, updatable = false)
    private Exhibition exhibition;

    /** 프론트와 합의된 슬롯 식별자. 예: {@code SHELF_1}, {@code TABLE_1}. */
    @Column(name = "slot_id", nullable = false, updatable = false, length = SLOT_ID_MAX_LENGTH)
    private String slotId;

    /** 배경이 제거된 이미지 주소. 처리 전에는 비어 있을 수 있습니다. */
    @Column(name = "image_url", length = IMAGE_URL_MAX_LENGTH)
    private String imageUrl;

    @Column(name = "item_name", nullable = false, length = ITEM_NAME_MAX_LENGTH)
    private String itemName;

    @Column(name = "brand", length = BRAND_MAX_LENGTH)
    private String brand;

    @Column(name = "description", length = DESCRIPTION_MAX_LENGTH)
    private String description;

    // Hibernate 6+ 는 @Enumerated(STRING) 을 MySQL 네이티브 enum 컬럼으로 만듭니다.
    // ddl-auto: update 는 enum 정의를 바꾸지 않아서, 나중에 상수를 추가하면 저장이 실패합니다.
    // varchar 로 고정해 그 문제를 피합니다.
    @Enumerated(EnumType.STRING)
    // columnDefinition 으로 varchar 를 강제합니다. @JdbcTypeCode(VARCHAR) 는 H2 에서
    // 잘못된 check 제약을 만들어 저장이 실패합니다.
    @Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
    private ItemStatus status;

    public ExhibitionItem(Exhibition exhibition, String slotId, String imageUrl,
                          String itemName, String brand, String description, ItemStatus status) {
        this.exhibition = exhibition;
        this.slotId = slotId;
        this.imageUrl = imageUrl;
        this.itemName = itemName;
        this.brand = brand;
        this.description = description;
        this.status = status;
    }

    public boolean belongsTo(Long exhibitionId) {
        return exhibition.getId().equals(exhibitionId);
    }
}
