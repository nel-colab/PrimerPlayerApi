package cl.primerplayer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PrimerPlayerApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(PrimerPlayerApiApplication.class, args);
	}

}
