package com.wwings.iau;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TestfabricdemoApplication {
	public static void main(String[] args) {
		SpringApplication.run(TestfabricdemoApplication.class, args);
	}
	@Bean
	public CheckRunner checkRunner(){
		return  new CheckRunner();
	}
}
