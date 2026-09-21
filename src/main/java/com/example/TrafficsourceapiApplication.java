package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    exclude = UserDetailsServiceAutoConfiguration.class
)
@EnableJpaAuditing
@EnableScheduling
public class TrafficsourceapiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TrafficsourceapiApplication.class, args);
	}

}
