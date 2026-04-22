package com.adlin.orin.common.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {
    private String mode = "dual";
    private String primary = "local";
    private String secondary = "minio";
    private boolean readFallback = true;
    private boolean writeAsyncRepair = true;
    private long presignTtlSeconds = 600;

    private Local local = new Local();
    private Minio minio = new Minio();
    private Repair repair = new Repair();

    @Data
    public static class Local {
        private String root = "storage/uploads";
    }

    @Data
    public static class Minio {
        private String endpoint = "";
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "orin-files";
        private boolean secure = false;
    }

    @Data
    public static class Repair {
        private boolean enabled = true;
        private int maxRetries = 8;
        private long fixedDelayMs = 30000;
    }
}

