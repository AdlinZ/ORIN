package com.adlin.orin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

// 导入 EnvFileLoader 以确保在 Spring 启动前加载 .env 文件
import com.adlin.orin.config.EnvFileLoader;

@SpringBootApplication
@EnableScheduling // Enable scheduling for monitoring tasks
@org.springframework.scheduling.annotation.EnableAsync // Enable async execution
@Import({com.adlin.orin.config.DifyConfig.class, EnvFileLoader.class}) // Import configurations
public class OrinApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrinApplication.class, args);
    }

}
