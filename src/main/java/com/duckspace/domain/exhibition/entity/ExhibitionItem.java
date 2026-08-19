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
                @Index(name = "idx_exhibition_item_status", columnList = "status"),
                // 공유 이미지 삭제 보호가 URL 로 참조를 확인합니다. 없으면 삭제마다 풀스캔입니다.
                @Index(name = "idx_exhibition_item_image_url", columnList = "image_url")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExhibitionItem extends BaseTimeEntity {

    public static final int IMAGE_URL_MAX_LENGTH = 500;
    public static final int ITEM_NAME_MAX_LENGTH = 50;
    public static final int COMMENT_MAX_LENGTH = 20;

    /** 회전하지 않은 상태. */
    public static final double NO_ROTATION = 0.0;

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

    /**
     * 회전 각도(도). {@code -180 ~ 180} 이며 <b>0 이 기본(회전 없음)</b>입니다.
     * 양수가 시계 방향, 음수가 반시계 방향입니다.
     *
     * <p>비율인 다른 배치 값과 달리 <b>각도는 화면 크기와 무관</b>해서 그대로 저장합니다.
     *
     * <p>기본값을 컬럼에 박아둔 이유는, 이미 굿즈가 들어있는 DB 에 이 컬럼이 추가될 때
     * 기존 행을 채울 값이 필요하기 때문입니다. ({@code ddl-auto: update})
     */
    @Column(name = "rotation", columnDefinition = "double not null default 0")
    private Double rotation;

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
        // 처음 놓을 때 회전을 안 보내면 "회전 없음" 입니다. 아직 지킬 이전 값이 없습니다.
        this.rotation = (placement.rotation() == null) ? NO_ROTATION : placement.rotation();
        this.imageUrl = imageUrl;
        this.itemName = itemName;
        this.price = price;
        this.comment = comment;
        this.status = status;
    }

    public boolean belongsTo(Long exhibitionId) {
        return exhibition.getId().equals(exhibitionId);
    }

    /**
     * 드래그 이동·크기 조절·회전 결과를 반영합니다.
     *
     * <p><b>회전은 안 보내면 그대로 둡니다.</b> 위치·크기와 다르게 다루는 이유가 있습니다 —
     * 회전 UI 가 없는 화면은 <b>현재 각도를 알 수도 없어서</b> 같이 보낼 수가 없습니다.
     * 그런 화면이 단순 드래그만 해도 사용자가 돌려둔 각도가 0 으로 지워지면 안 됩니다.
     *
     * <p>위치·크기는 {@code @NotNull} 이라 항상 넘어오므로 이런 문제가 없습니다.
     */
    public void moveTo(Placement placement) {
        applyPlacement(placement);
        if (placement.rotation() != null) {
            this.rotation = placement.rotation();
        }
    }

    /** 위치와 크기만 반영합니다. 회전은 생성·수정에서 규칙이 달라 각자 처리합니다. */
    private void applyPlacement(Placement placement) {
        this.posX = placement.posX();
        this.posY = placement.posY();
        this.width = placement.width();
        this.height = placement.height();
    }

    /**
     * 배치 정보. 위치·크기는 <b>배경 대비 비율</b>, 회전은 <b>도(degree)</b> 입니다.
     *
     * @param rotation {@code -180 ~ 180}, 양수가 시계 방향입니다.
     *                 <b>{@code null} 은 "지정하지 않음"</b>이라 생성에서는 0, 수정에서는
     *                 기존 값 유지로 갈립니다. 여기서 0 으로 바꿔버리면 그 구분이 사라집니다.
     */
    public record Placement(Double posX, Double posY, Double width, Double height, Double rotation) {

        /** 회전을 명시적으로 "없음(0)" 으로 두는 편의 생성자. */
        public Placement(Double posX, Double posY, Double width, Double height) {
            this(posX, posY, width, height, NO_ROTATION);
        }
    }

    /**
     * 다시 처리 대기 상태로 되돌립니다. 실패한 굿즈를 재시도할 때 씁니다.
     *
     * <p><b>{@code imageUrl} 은 일부러 그대로 둡니다.</b> 그 주소가 재처리할 원본이라
     * 여기서 지우면 다시 시도할 방법이 사라지고, 처리되는 동안 화면에 보여줄 그림도 없어집니다.
     */
    public void markPending() {
        this.status = ItemStatus.PENDING;
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
