package com.duckspace.domain.exhibition.image;

/**
 * 공개 URL 과 저장 경로(key) 사이를 오갑니다.
 *
 * <p>DB 에는 저장 경로가 아니라 화면에 그대로 쓰는 URL 만 남기고 있어서, 지우거나 다시 읽으려면
 * base URL 을 떼어내 경로를 복원해야 합니다. 로컬·S3 구현이 같은 규칙을 쓰므로 여기 모읍니다.
 */
final class StorageUrls {

    private StorageUrls() {
    }

    /**
     * {@code publicBaseUrl} 로 시작하는 URL 에서 저장 경로만 뽑습니다.
     *
     * <p>우리가 만든 URL 이 아니면 {@code null} 을 돌려줍니다. 남의 주소를 받아 지우거나 읽는
     * 일이 없도록 하기 위한 것입니다.
     */
    static String keyFrom(String publicBaseUrl, String imageUrl) {
        if (imageUrl == null || publicBaseUrl == null) {
            return null;
        }
        String prefix = publicBaseUrl + "/";
        return imageUrl.startsWith(prefix) ? imageUrl.substring(prefix.length()) : null;
    }

    /** 끝에 붙은 슬래시를 떼어 base URL 표기를 통일합니다. */
    static String normalizeBaseUrl(String publicBaseUrl) {
        return publicBaseUrl.replaceAll("/+$", "");
    }
}
