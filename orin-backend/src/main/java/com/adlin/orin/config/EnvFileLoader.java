package com.adlin.orin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * .env 文件加载器
 * 使用 EnvironmentPostProcessor 在 Spring Boot 启动早期加载环境变量
 */
public class EnvFileLoader implements EnvironmentPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnvFileLoader.class);
    private static final String ENV_FILE = ".env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties props = loadEnvFile();

        if (!props.isEmpty()) {
            PropertySource<?> propertySource = new org.springframework.core.env.PropertiesPropertySource("envFile", props);
            environment.getPropertySources().addFirst(propertySource);
            logger.info("✓ 已从 .env 文件加载 {} 个环境变量", props.size());
        } else {
            logger.warn("未找到 .env 文件，请确保 .env 文件存在于项目根目录");
        }
    }

    private Properties loadEnvFile() {
        Properties props = new Properties();

        // 1. 尝试从当前工作目录加载
        Path currentDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path envPath = currentDir.resolve(ENV_FILE);

        if (!Files.exists(envPath)) {
            // 2. 尝试从父目录加载（项目根目录）
            envPath = currentDir.getParent() != null
                ? currentDir.getParent().resolve(ENV_FILE)
                : null;
        }

        if (envPath != null && Files.exists(envPath)) {
            try (InputStream is = Files.newInputStream(envPath)) {
                props.load(is);
                logger.info("从 {} 加载 .env 文件", envPath);
                return props;
            } catch (Exception e) {
                logger.error("加载 .env 文件失败: {}", e.getMessage());
            }
        }

        // 3. 尝试从 classpath 加载
        try {
            Resource resource = new ClassPathResource(ENV_FILE);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    props.load(is);
                    logger.info("从 classpath 加载 .env 文件");
                    return props;
                }
            }
        } catch (Exception e) {
            logger.debug("从 classpath 加载失败: {}", e.getMessage());
        }

        return props;
    }
}
