package com.duckspace.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 교환 품목 한 건. {@link TradeItemSide#OFFERED}(내가 가진 굿즈) /
 * {@link TradeItemSide#WANTED}(내가 원하는 굿즈)로 구분되며, 교환 글 하나당 각 한 행씩 존재합니다.
 */
@Entity
@Table(name = "trade_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private ExchangeDetail exchangeDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, updatable = false)
    private TradeItemSide side;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "brand")
    private String brand;

    /** OFFERED(내가 가진 굿즈)만 사용합니다. WANTED는 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition")
    private ItemCondition condition;

    private TradeItem(ExchangeDetail exchangeDetail, TradeItemSide side, String imageUrl,
                       String itemName, String brand, ItemCondition condition) {
        this.exchangeDetail = exchangeDetail;
        this.side = side;
        this.imageUrl = imageUrl;
        this.itemName = itemName;
        this.brand = brand;
        this.condition = condition;
    }

    public static TradeItem offered(ExchangeDetail exchangeDetail, String imageUrl, String itemName,
                                     String brand, ItemCondition condition) {
        return new TradeItem(exchangeDetail, TradeItemSide.OFFERED, imageUrl, itemName, brand, condition);
    }

    public static TradeItem wanted(ExchangeDetail exchangeDetail, String imageUrl, String itemName, String brand) {
        return new TradeItem(exchangeDetail, TradeItemSide.WANTED, imageUrl, itemName, brand, null);
    }
}
