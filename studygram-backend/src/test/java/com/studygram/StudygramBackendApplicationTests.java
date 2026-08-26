package com.studygram;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/*
 * The smoke test: does the application start at all?
 *
 * It looks trivial, and it catches a surprising amount - a bean that cannot be
 * built, two beans competing for the same role, a property referenced in code
 * but missing from configuration, a broken @Query that Hibernate validates at
 * startup. If this fails, nothing else is worth reading.
 *
 * @ActiveProfiles("test") points it at the in-memory database. Without it the
 * test would need a real PostgreSQL running, which would mean it failed on a
 * fresh clone and in CI.
 */
@SpringBootTest
@ActiveProfiles("test")
class StudygramBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
