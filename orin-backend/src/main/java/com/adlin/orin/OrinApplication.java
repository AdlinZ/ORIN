package com.adlin.orin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Enable scheduling for monitoring tasks
@org.springframework.scheduling.annotation.EnableAsync // Enable async execution
@Import(com.adlin.orin.config.DifyConfig.class) // Import Dify configuration
public class OrinApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrinApplication.class, args);
    }

}
