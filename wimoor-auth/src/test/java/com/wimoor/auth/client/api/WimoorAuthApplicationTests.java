package com.wimoor.auth.client.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WimoorAuthApplicationTests {


	@Value("${mysql.password}")
	String password;

	@Test
	void contextLoads() {
	System.out.println("test");
	System.out.println(password);
	}

}
