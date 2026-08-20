package com.duckspace.global.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 저장소에 커밋된 JWT 시크릿으로 배포 환경이 뜨는 것을 막습니다.
 *
 * <p>{@code application.yml} 은 {@code jwt.secret: ${JWT_SECRET}} 처럼 기본값을 두지 않아서,
 * 환경변수를 <b>빠뜨리면</b> 부팅이 실패합니다. 하지만 <b>잘못 채운 경우</b>는 못 막습니다 —
 * 서버를 세팅하면서 {@code application-local.yml} 의 값을 그대로 복사해 넣는 실수가 그렇습니다.
 *
 * <p>그러면 아무 에러 없이 정상 부팅하고, 겉보기에도 멀쩡히 동작합니다. 문제는
 * <b>리포지터리를 볼 수 있는 누구나 서명 키를 안다</b>는 것입니다. 아무 {@code userId} 로나
 * 액세스 토큰을 만들어 관리자 API 까지 통과할 수 있습니다. 로그로도 안 드러나서,
 * 실제로 악용되기 전에는 알아챌 방법이 없습니다.
 *
 * <p>그래서 {@code local} · {@code test} 가 아닌 프로필로 뜰 때 커밋된 값이면 부팅을 세웁니다.
 * 조용히 뚫린 채로 도는 것보다 배포가 실패하는 편이 낫습니다.
 *
 * <p><b>이 검사가 막아주지 않는 것:</b> 여기 목록에 없는 값은 아무리 약해도 통과합니다.
 * "커밋된 값을 복사해 넣는 실수" 하나만 겨냥한 방어이며, 시크릿 관리 자체를 대신하지 않습니다.
 * (길이가 짧은 키는 {@link io.jsonwebtoken.security.Keys#hmacShaKeyFor} 가 부팅 시점에
 * {@code WeakKeyException} 으로 이미 막아줍니다.)
 */
@Slf4j
@Component
public class JwtSecretGuard {

    /**
     * 저장소에 그대로 들어 있는 시크릿들.
     *
     * <p>값을 바꾸면 {@code JwtSecretGuardTest} 가 yml 을 읽어 대조하므로, 여기를 갱신하지 않으면
     * 테스트가 실패합니다. 목록이 조용히 낡는 것을 막기 위한 장치입니다.
     */
    static final Set<String> COMMITTED_SECRETS = Set.of(
            "local-dev-temporary-secret-key-change-this-later-123456",    // application-local.yml
            "test-only-secret-key-do-not-use-in-production-0123456789"    // application-test.yml
    );

    /** 커밋된 값을 써도 되는 프로필. 개발자 PC 와 CI 입니다. */
    private static final Profiles DEVELOPMENT_PROFILES = Profiles.of("local", "test");

    public JwtSecretGuard(JwtProperties properties, Environment environment) {
        // 활성 프로필이 없으면 spring.profiles.default(= local)로 판단합니다.
        if (environment.acceptsProfiles(DEVELOPMENT_PROFILES)) {
            return;
        }

        String secret = properties.secret();

        // Set.of(...) 는 contains(null) 에서 NullPointerException 을 던집니다. 지금은 부팅 순서상
        // 여기까지 null 이 오지 못하지만(jwt.secret 에 기본값이 없어 플레이스홀더 해석에서 먼저
        // 실패합니다), 만약 오게 되면 아래의 친절한 메시지 대신 원인을 알 수 없는 NPE 스택트레이스가
        // 남습니다. 한 줄로 막아둡니다.
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret 이 비어 있습니다. dev/prod 는 JWT_SECRET 환경변수로 주입해야 합니다.");
        }

        if (COMMITTED_SECRETS.contains(secret)) {
            throw new IllegalStateException("""
                    JWT_SECRET 이 저장소에 커밋된 값입니다. 이 키를 아는 사람은 누구나 토큰을 위조할 수 있습니다.
                    /etc/duckspace/app.env 의 JWT_SECRET 을 외부에 공개되지 않은 값으로 바꾸고 다시 배포하세요.
                    (활성 프로필: %s)""".formatted(String.join(", ", environment.getActiveProfiles())));
        }

        log.info("JWT 시크릿 확인 완료 — 커밋된 값이 아닙니다.");
    }
}
