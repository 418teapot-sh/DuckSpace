package com.duckspace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 테스트는 local 프로필(MySQL) 대신 test 프로필(H2 인메모리)로 돌립니다.
 * Docker 없이도 ./gradlew test 와 CI가 동작하게 하기 위해서입니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class DuckspaceApplicationTests {

	@Test
	void contextLoads() {
	}

}
