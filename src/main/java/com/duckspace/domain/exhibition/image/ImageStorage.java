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

    /**
     * 저장된 이미지를 지웁니다. 없는 이미지를 지워도 성공으로 봅니다.
     *
     * <p><b>key 가 아니라 URL 을 받는 이유:</b> DB 에는 저장 경로가 아니라 화면에 그대로 쓰는
     * URL 만 남기고 있습니다. 여기서 각 구현이 자기 base URL 을 떼어내 경로를 복원합니다.
     * (컬럼을 하나 더 만들지 않으려는 선택입니다.)
     *
     * @param imageUrl {@link #upload} 가 돌려줬던 URL. {@code null} 이면 아무것도 하지 않습니다.
     */
    void deleteByUrl(String imageUrl);

    /**
     * 저장된 이미지를 다시 읽습니다. <b>실패한 굿즈를 재처리할 때</b> 씁니다.
     *
     * <p>업로드 바이트는 처리 후 메모리에서 사라지므로, 재시도하려면 실패 시 남겨둔 원본을
     * 여기서 다시 가져와야 합니다.
     *
     * <p><b>느릴 수 있는 호출입니다.</b> S3 왕복이라 요청 스레드나 트랜잭션 안에서 부르지 마세요.
     *
     * @param imageUrl {@link #upload} 가 돌려줬던 URL
     * @return 저장된 바이트
     * @throws java.io.UncheckedIOException 읽지 못한 경우
     */
    byte[] download(String imageUrl);

    /**
     * URL 에서 저장 키를 복원합니다. <b>우리가 만든 URL 이 아니면 {@code null}</b> 입니다.
     *
     * <p>확인해 주는 것은 "이 주소가 우리 저장소를 가리키는가" 까지입니다 —
     * <b>누구 것인지는 알려주지 않습니다.</b> 키에 소유자가 들어 있는 규칙
     * ({@code posts/{userId}/…}, {@code users/{userId}/…}, {@code images/{userId}/…})을
     * 아는 쪽에서 접두사로 확인하세요.
     *
     * <p>클라이언트가 준 URL 로 무언가를 지우기 전에 반드시 거쳐야 합니다. 접두사 확인 없이
     * 지우면 같은 버킷의 남의 파일을 지울 수 있습니다.
     */
    String keyOf(String imageUrl);
}
