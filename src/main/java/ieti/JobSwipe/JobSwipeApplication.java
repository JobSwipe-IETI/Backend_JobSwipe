package ieti.JobSwipe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@EnableScheduling
public class JobSwipeApplication {
    private static final Logger log = LoggerFactory.getLogger(JobSwipeApplication.class);
	public static void main(String[] args) {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
        } catch (Exception e) {
            log.warn("No .env file found or error loading it. Proceeding with environment variables.");
        }
		SpringApplication.run(JobSwipeApplication.class, args);
	}

}
