package com.app.sme_health_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.app")
public class SmeHealthBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmeHealthBackendApplication.class, args);
	}

}
