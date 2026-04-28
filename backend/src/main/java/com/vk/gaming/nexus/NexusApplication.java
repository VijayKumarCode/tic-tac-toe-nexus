package com.vk.gaming.nexus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan("com.vk.gaming.nexus.config")
public class NexusApplication {
	public static void main(String[] args) {
		SpringApplication.run(NexusApplication.class, args);
	}
}
