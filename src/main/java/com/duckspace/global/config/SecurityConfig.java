package com.duckspace.global.config;

import com.duckspace.global.auth.JwtAccessDeniedHandler;
import com.duckspace.global.auth.JwtAuthenticationEntryPoint;
import com.duckspace.global.auth.JwtAuthenticationFilter;
import com.duckspace.global.auth.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * 인증 없이 접근할 수 있는 경로.
     * 도메인 컨트롤러가 생기면(예: 로그인 API) 여기에 추가하세요.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/**",
            "/api/home",
            "/api/banners",
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            // 로컬 저장소에 올린 굿즈 이미지. <img src="/uploads/..."> 는 토큰을 붙일 수 없어서
            // 여기에 없으면 전부 401 이 됩니다. 배포는 S3 라 이 경로 자체가 없습니다.
            "/uploads/**",
    };

    /**
     * 인증 없이 <b>GET 만</b> 허용할 경로.
     *
     * <p>위 {@link #PUBLIC_ENDPOINTS} 와 나눠 둔 이유는, 전시 상세와 수정·삭제가
     * <b>같은 경로</b>({@code /api/exhibitions/{id}})를 쓰기 때문입니다. 경로만으로 열면
     * 아무나 남의 장식장을 지울 수 있게 됩니다.
     *
     * <p>id 를 숫자로 제한한 것도 의도입니다. {@code /api/exhibitions/*} 로 두면 나중에
     * 누군가 {@code /api/exhibitions/mine} 같은 걸 추가했을 때 조용히 공개됩니다.
     */
    private static final String[] PUBLIC_GET_ENDPOINTS = {
            // 검색 탭 기본 화면의 장식장 피드(GET). 같은 경로의 POST(생성)는 인증이 필요합니다.
            "/api/exhibitions",
            "/api/exhibitions/popular",
            "/api/exhibitions/{exhibitionId:[0-9]+}",
            // 프로필 화면에서 다른 유저의 대표 장식장으로 이동할 때 씁니다(#65).
            "/api/exhibitions/users/{userId:[0-9]+}/primary",
            "/api/search/exhibitions",
            // 찜(POST/DELETE .../like)과 위시리스트(.../likes)가 같은 /api/popups/** 아래 있어서
            // 조회(GET)만 여기로 열어둡니다. 통째로 열면 로그인 없이 찜을 누를 수 있게 됩니다.
            "/api/popups",
            "/api/popups/{popupId:[0-9]+}",
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // JWT를 쓰므로 세션을 만들지 않습니다. 따라서 CSRF 토큰도 필요 없습니다.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()   // CORS preflight
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )

                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)   // 401
                        .accessDeniedHandler(jwtAccessDeniedHandler)             // 403
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 폼 로그인(이메일/비밀번호) 회원가입·로그인에서 비밀번호를 암호화/검증하는 데 사용합니다. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
