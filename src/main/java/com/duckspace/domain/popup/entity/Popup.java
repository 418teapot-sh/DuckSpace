package com.duckspace.domain.popup.entity;

import com.duckspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Popup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String imageUrl;

    @Lob
    private String description;

    private String location;

    /** 팝업 시작일 — 목록을 일정순으로 정렬할 때 기준이 되는 값 */
    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    /** OpenAI가 생성한 일정 요약. 등록/수정 시점에 서버가 채워 넣는다. */
    @Lob
    private String aiSummary;

    /** 혜택/굿즈 소개 이미지. 상세 화면 "혜택 및 굿즈" 섹션용이라 목록 응답엔 안 실음 — 없어도 되는 값이라 nullable. */
    private String benefitImageUrl;

    @Lob
    private String benefitDescription;

    /**
     * 운영시간 자유 텍스트. {@code location}과 같은 이유로 구조화(LocalTime 쌍)하지 않고 문자열로 둠 —
     * "11:00~22:00(30분 단위 운영)"처럼 시각 외의 운영 방식 안내가 같이 붙는 경우가 흔해서다.
     */
    private String operatingHours;

    @Builder
    private Popup(String title, String imageUrl, String description, String location,
                  LocalDate startDate, LocalDate endDate, String aiSummary,
                  String benefitImageUrl, String benefitDescription, String operatingHours) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.description = description;
        this.location = location;
        this.startDate = startDate;
        this.endDate = endDate;
        this.aiSummary = aiSummary;
        this.benefitImageUrl = benefitImageUrl;
        this.benefitDescription = benefitDescription;
        this.operatingHours = operatingHours;
    }

    public void update(String title, String imageUrl, String description, String location,
                        LocalDate startDate, LocalDate endDate, String aiSummary,
                        String benefitImageUrl, String benefitDescription, String operatingHours) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.description = description;
        this.location = location;
        this.startDate = startDate;
        this.endDate = endDate;
        this.aiSummary = aiSummary;
        this.benefitImageUrl = benefitImageUrl;
        this.benefitDescription = benefitDescription;
        this.operatingHours = operatingHours;
    }

    public PopupStatus getStatus() {
        return PopupStatus.of(startDate, endDate);
    }
}