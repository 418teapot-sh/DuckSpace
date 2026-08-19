package com.duckspace.domain.exhibition.image;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로컬 저장소의 <b>예외 계약</b>을 고정합니다.
 *
 * <p>삭제는 절대 던지지 않아야 합니다 — 정리 배치가 목록을 {@code forEach} 로 돌면서 지우는데,
 * 하나라도 던지면 남은 대상 전체가 중단됩니다. 읽기는 반대로 {@link ImageStorage} javadoc 이
 * {@code UncheckedIOException} 을 약속합니다.
 */
class LocalImageStorageTest {

    private static final String BASE_URL = "http://localhost:8080/uploads";

    private LocalImageStorage storage(Path root) {
        return new LocalImageStorage(root.toString(), BASE_URL);
    }

    private String url(String key) {
        return BASE_URL + "/" + key;
    }

    @Test
    @DisplayName("올린 파일을 URL 로 다시 지울 수 있다")
    void 올리고_지운다(@TempDir Path root) {
        LocalImageStorage storage = storage(root);

        String imageUrl = storage.upload("posts/1/a.png", new byte[]{1, 2, 3}, "image/png");
        assertThat(root.resolve("posts/1/a.png")).exists();

        storage.deleteByUrl(imageUrl);
        assertThat(root.resolve("posts/1/a.png")).doesNotExist();
    }

    @Test
    @DisplayName("경로를 벗어나는 URL 을 지우려 해도 예외를 던지지 않는다")
    void 삭제는_절대_던지지_않는다(@TempDir Path root) {
        LocalImageStorage storage = storage(root);

        // resolve() 가 루트 밖으로 나가는 키를 BusinessException 으로 막는데, 예전에는
        // catch 가 IOException 만 잡아서 그게 그대로 새어나갔습니다. 저장된 URL 하나만
        // 이상해도 정리 배치가 통째로 멈추는 상태였습니다.
        assertThatCode(() -> storage.deleteByUrl(url("../../etc/passwd")))
                .doesNotThrowAnyException();

        // 없는 파일도 조용히 넘어갑니다.
        assertThatCode(() -> storage.deleteByUrl(url("posts/1/none.png")))
                .doesNotThrowAnyException();

        // 우리 주소가 아닌 것도 마찬가지입니다.
        assertThatCode(() -> storage.deleteByUrl("https://other-host/x.png"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("읽기는 계약대로 UncheckedIOException 으로 실패한다")
    void 읽기_실패는_UncheckedIOException(@TempDir Path root) {
        LocalImageStorage storage = storage(root);

        assertThatThrownBy(() -> storage.download(url("posts/1/none.png")))
                .isInstanceOf(UncheckedIOException.class);

        // 경로 탈출도 같은 타입으로 나와야 호출부가 한 가지만 처리하면 됩니다.
        assertThatThrownBy(() -> storage.download(url("../../etc/passwd")))
                .isInstanceOf(UncheckedIOException.class);

        assertThatThrownBy(() -> storage.download("https://other-host/x.png"))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("우리 저장소 주소만 키로 풀어준다")
    void 남의_주소는_키가_안_나온다(@TempDir Path root) throws Exception {
        LocalImageStorage storage = storage(root);
        Files.createDirectories(root);

        assertThat(storage.keyOf(url("users/42/a.png"))).isEqualTo("users/42/a.png");
        assertThat(storage.keyOf("https://other-host/users/42/a.png")).isNull();
        assertThat(storage.keyOf(null)).isNull();
    }
}
