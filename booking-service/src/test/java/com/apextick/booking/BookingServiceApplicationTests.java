package com.apextick.booking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(IntegrationTestConfig.class)
class BookingServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}