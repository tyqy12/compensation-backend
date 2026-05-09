package com.yiyundao.compensation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class CompensationAssistantSystemApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void scheduledTaskTablesShouldBeInitializedInTests() {
		Integer tableCount = jdbcTemplate.queryForObject(
				"select count(*) from scheduled_task",
				Integer.class
		);

		assertEquals(0, tableCount);
	}

}
