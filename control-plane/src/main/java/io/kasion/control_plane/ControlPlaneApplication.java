package io.kasion.control_plane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication

@EnableAsync
public class ControlPlaneApplication {

	public static void main(String[] args) {
		SpringApplication.run(ControlPlaneApplication.class, args);
	}

}
