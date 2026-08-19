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
     * <p>우리가 만든 URL 이 아니면 {@code null} 을 돌려줍니다 — <b>다른 호스트의 주소</b>로
     * 지우거나 읽는 일을 막습니다.
     *
     * <p><b>여기서 확인하지 않는 것:</b> 같은 저장소 안에서 <b>누구 것인지</b>는 보지 않습니다.
     * base URL 뒤는 그대로 키가 되므로, 접두사를 확인하지 않고 이 값을 삭제에 쓰면
     * 같은 버킷의 남의 파일을 지울 수 있습니다. 소유 확인은 키 규칙을 아는 호출부의 몫입니다
     * ({@link ImageStorage#keyOf} 참고).
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
