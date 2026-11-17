package cl.primerplayer;

import org.springframework.boot.SpringApplication;

public class TestPrimerPlayerApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(PrimerPlayerApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
