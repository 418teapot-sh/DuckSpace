package com.duckspace.domain.exhibition.image;

/**
 * 처리된 이미지를 저장하고 접근 URL 을 돌려줍니다.
 *
 * <p>배포는 S3, 로컬은 파일시스템을 씁니다. <b>EC2 에는 IAM 역할이 붙어 있지만 개발자 PC 에는 없으므로</b>
 * 로컬에서도 개발이 가능하도록 구현을 나눴습니다.
 */
public interface ImageStorage {

    /**
     * @param key         저장 경로. 예: {@code exhibitions/12/9f8c....png}
     * @param content     저장할 바이트
     * @param contentType MIME 타입
     * @return 이미지에 접근할 수 있는 URL
     */
    String upload(String key, byte[] content, String contentType);
}
