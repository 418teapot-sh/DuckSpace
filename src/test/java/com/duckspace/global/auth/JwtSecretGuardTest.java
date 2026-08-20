package com.duckspace.global.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 커밋된 JWT 시크릿으로 배포 환경이 뜨는 것을 막는지 확인합니다.
 *
 * <p>{@code application.yml} 이 기본값을 안 둬서 <b>환경변수를 빠뜨린</b> 경우는 이미 부팅이
 * 실패합니다. 여기서 보는 것은 <b>잘못 채운</b> 경우 — 서버 세팅 중 저장소의 값을 복사해
 * 넣는 실수입니다. 그건 아무 에러 없이 부팅되고 로그에도 안 남습니다.
 */
class JwtSecretGuardTest {

    private static final String SAFE_SECRET =
            "an-actual-production-secret-that-is-not-in-the-repository-0000";
    private static final String COMMITTED_LOCAL_SECRET =
            "local-dev-temporary-secret-key-change-this-later-123456";

    private static JwtProperties props(String secret) {
        return new JwtProperties(secret, 1800000L, 1209600000L);
    }

    private static MockEnvironment env(String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return environment;
    }

    @Test
    @DisplayName("dev 프로필에서 커밋된 시크릿이면 부팅을 세운다")
    void dev에서_커밋된_시크릿이면_실패() {
        assertThatThrownBy(() -> new JwtSecretGuard(props(COMMITTED_LOCAL_SECRET), env("dev")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("dev 프로필이라도 저장소에 없는 시크릿이면 통과한다")
    void dev에서_안전한_시크릿이면_통과() {
        assertThatCode(() -> new JwtSecretGuard(props(SAFE_SECRET), env("dev")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("local · test 프로필은 커밋된 시크릿을 그대로 쓴다")
    void 개발_프로필은_통과() {
        // 개발자 PC 와 CI 는 이 값으로 돌아야 합니다. 여기서 막으면 아무도 못 띄웁니다.
        for (String profile : new String[]{"local", "test"}) {
            assertThatCode(() -> new JwtSecretGuard(props(COMMITTED_LOCAL_SECRET), env(profile)))
                    .as("%s 프로필", profile)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("활성 프로필이 없으면 기본값(local)으로 보고 통과한다")
    void 프로필이_비어있으면_통과() {
        // spring.profiles.default 가 local 이라 개발자 PC 가 여기 해당합니다.
        // MockEnvironment 의 기본 프로필도 "default" 가 아니라 비어 있는 상태입니다.
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("local");

        assertThatCode(() -> new JwtSecretGuard(props(COMMITTED_LOCAL_SECRET), environment))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시크릿이 비어 있으면 NPE 대신 읽을 수 있는 메시지로 세운다")
    void 시크릿이_비면_알아볼_수_있게_실패() {
        // COMMITTED_SECRETS 는 Set.of(...) 라 contains(null) 에서 NullPointerException 이 납니다.
        // 지금은 부팅 순서상 여기까지 null 이 오지 못하지만, 오게 되면 원인을 알 수 없는
        // 스택트레이스만 남습니다. (PR #95 리뷰)
        for (String blank : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> new JwtSecretGuard(props(blank), env("dev")))
                    .as("secret = %s", blank == null ? "null" : "\"" + blank + "\"")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jwt.secret");
        }
    }

    @Test
    @DisplayName("yml 에 적힌 시크릿이 감시 목록에 빠짐없이 들어 있다")
    void 감시_목록이_yml_과_일치한다() throws IOException {
        // 목록을 손으로 관리하면 조용히 낡습니다. yml 을 바꾸고 여기를 안 고치면 이 테스트가
        // 실패해서, 배포에서 통과해버리는 값이 새로 생기는 것을 막습니다.
        assertThat(secretIn(Path.of("src/main/resources/application-local.yml")))
                .as("application-local.yml 의 jwt.secret")
                .isIn(JwtSecretGuard.COMMITTED_SECRETS);

        assertThat(secretIn(Path.of("src/test/resources/application-test.yml")))
                .as("application-test.yml 의 jwt.secret")
                .isIn(JwtSecretGuard.COMMITTED_SECRETS);
    }

    /**
     * yml 에서 {@code jwt.secret} 값을 꺼냅니다.
     *
     * <p>정규식으로 직접 긁다가 <b>진짜 YAML 파서로 바꿨습니다.</b> {@code secret: "..."} 처럼
     * 따옴표를 쓰거나 줄 끝에 주석이 붙으면, 정규식은 매칭에 실패해 원인과 무관한 메시지로
     * 테스트가 죽거나 최악의 경우 엉뚱한 값을 잡습니다. 이 테스트의 목적이 "감시 목록이 낡지
     * 않게 하는 것" 인데, 파싱이 부정확하면 그 목적 자체가 무너집니다. (PR #95 리뷰)
     */
    private static String secretIn(Path yml) throws IOException {
        assertThat(yml).as("설정 파일이 있어야 합니다").exists();

        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(yml)) {
            root = new Yaml().load(in);
        }

        assertThat(root).as("%s 이 비어 있습니다", yml).isNotNull();
        assertThat(root.get("jwt")).as("%s 에 jwt 블록이 없습니다", yml).isInstanceOf(Map.class);

        Object secret = ((Map<?, ?>) root.get("jwt")).get("secret");
        assertThat(secret).as("%s 에 jwt.secret 이 없습니다", yml).isNotNull();
        return secret.toString();
    }
}
