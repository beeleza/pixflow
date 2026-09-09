package com.beeleza.pixflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class PixflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(PixflowApplication.class, args);
	}

}
