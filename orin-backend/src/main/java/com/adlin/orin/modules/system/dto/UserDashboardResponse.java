package com.adlin.orin.modules.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDashboardResponse {

    private List<StatCard> stats;
    private List<ActivityData> activityData;
    private List<ActivityLog> activityLogs;
    private List<String> skills;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatCard {
        private String label;
        private String value;
        private Double trend;
        private String icon;
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityData {
        private String label;
        private Integer value;
        private Integer count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityLog {
        private String action;
        private String detail;
        private String time;
        private String type;
    }
}
