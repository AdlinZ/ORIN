package com.adlin.orin.modules.agent.freeze;

import com.adlin.orin.modules.agent.controller.AgentManageController;
import com.adlin.orin.modules.agent.freeze.controller.AgentFreezeController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring MVC ambiguous mapping 防御测试（pure reflection，不依赖 DB / Spring context）。
 *
 * <p>F02 closure-fix 历史背景：{@code AgentManageController}（旧）与 {@code AgentFreezeController}（新）
 * 同时注册了 {@code /api/v1/agents/{id}/versions/**} 路径 → Spring Boot 启动期抛
 * {@code IllegalStateException: Ambiguous mapping}。
 *
 * <p>本测试用 reflection 扫描两个 Controller 的所有 {@code @RequestMapping}/{@code @GetMapping}/
 * {@code @PostMapping}/{@code @PutMapping}/{@code @DeleteMapping}/{@code @PatchMapping} 注解，
 * 拆解出 path × method 组合，检测重复。重复即说明两处声明了同 path×method → Spring 启动会失败
 * → 本测试红线 ❌ = 必须立即修复。
 *
 * <p>测试不依赖 Mybatis / Flyway / DB；可在 CI 任意阶段独立跑。
 */
class AgentControllerMappingTest {

    @Test
    @DisplayName("AgentManageController + AgentFreezeController 之间无 path×method 冲突")
    void no_ambiguous_mapping_between_two_controllers() {
        List<Mapping> manageMappings = dedupWithinController(collectMappings(AgentManageController.class));
        List<Mapping> freezeMappings = dedupWithinController(collectMappings(AgentFreezeController.class));

        // 跨 controller 检查重复：同一 path × method 出现在两个 controller 里就算冲突
        Map<String, Mapping> allUnique = new HashMap<>();
        List<String> collisions = new ArrayList<>();

        for (Mapping m : manageMappings) {
            String key = m.method + " " + m.path;
            if (allUnique.containsKey(key)) {
                collisions.add(key + "  (already registered by "
                        + allUnique.get(key).className + ")");
            } else {
                allUnique.put(key, m);
            }
        }
        for (Mapping m : freezeMappings) {
            String key = m.method + " " + m.path;
            if (allUnique.containsKey(key)) {
                collisions.add(key + "  (already registered by "
                        + allUnique.get(key).className + ")");
            } else {
                allUnique.put(key, m);
            }
        }

        assertTrue(colllectionsAreClean(collisions),
                "Ambiguous mapping 冲突：\n  - " + String.join("\n  - ", collisions));
    }

    /**
     * 单 controller 内的 path × method 重复：Spring 会通过 consumes/produces/params 消歧，
     * 不算 ambiguous mapping。本 helper 只保留每 (path, method) 第一条以过滤这种内部多态。
     */
    private static List<Mapping> dedupWithinController(List<Mapping> mappings) {
        Map<String, Mapping> seen = new HashMap<>();
        List<Mapping> out = new ArrayList<>();
        for (Mapping m : mappings) {
            String key = m.method + " " + m.path;
            if (!seen.containsKey(key)) {
                seen.put(key, m);
                out.add(m);
            }
        }
        return out;
    }

    @Test
    @DisplayName("GET /api/v1/agents/{agentId}/versions 仅由 AgentFreezeController 拥有")
    void list_versions_belongs_only_to_freeze_controller() {
        List<Mapping> manageMappings = collectMappings(AgentManageController.class);
        List<Mapping> freezeMappings = collectMappings(AgentFreezeController.class);

        String targetPath = "/api/v1/agents/{agentId}/versions";
        String getKey = "GET " + targetPath;
        String postKey = "POST " + targetPath;

        boolean manageOwnsGet = manageMappings.stream().anyMatch(m -> getKey.equals(m.method + " " + m.path));
        boolean freezeOwnsGet = freezeMappings.stream().anyMatch(m -> getKey.equals(m.method + " " + m.path));
        boolean manageOwnsPost = manageMappings.stream().anyMatch(m -> postKey.equals(m.method + " " + m.path));
        boolean freezeOwnsPost = freezeMappings.stream().anyMatch(m -> postKey.equals(m.method + " " + m.path));

        assertTrue(!manageOwnsGet && !manageOwnsPost,
                "AgentManageController 不再声明 versions 路径（迁移到 AgentFreezeController）");
        assertTrue(freezeOwnsGet && freezeOwnsPost,
                "AgentFreezeController 必须声明 GET 与 POST " + targetPath);
    }

    @Test
    @DisplayName("POST /api/v1/agents 仅由 AgentFreezeController 拥有（F02 R3 真 create 端点）")
    void post_agents_belongs_only_to_freeze_controller() {
        String targetPath = "/api/v1/agents";
        String postKey = "POST " + targetPath;

        boolean manageOwnsPost = collectMappings(AgentManageController.class).stream()
                .anyMatch(m -> postKey.equals(m.method + " " + m.path));
        boolean freezeOwnsPost = collectMappings(AgentFreezeController.class).stream()
                .anyMatch(m -> postKey.equals(m.method + " " + m.path));

        assertTrue(!manageOwnsPost,
                "AgentManageController 不声明 POST /agents；由 AgentFreezeController 唯一拥有");
        assertTrue(freezeOwnsPost,
                "AgentFreezeController 必须声明 POST /agents（real create）");
    }

    @Test
    @DisplayName("verify compile + reflection sanity check: 至少有一条 mapping 被找到")
    void reflection_picks_up_at_least_one_mapping() {
        int count = collectMappings(AgentManageController.class).size()
                + collectMappings(AgentFreezeController.class).size();
        assertTrue(count > 5,
                "两个 controller 合计应有不少于 5 条 mapping；现仅 " + count);
    }

    private boolean colllectionsAreClean(List<String> collisions) {
        return collisions.isEmpty();
    }

    private static class Mapping {
        final String className;
        final String method;
        final String path;

        Mapping(String className, String method, String path) {
            this.className = className;
            this.method = method;
            this.path = path;
        }
    }

    /** 扫描 controller class 的所有 method 上的 mapping 注解，拆出 path × HTTP method 组合。 */
    private static List<Mapping> collectMappings(Class<?> controllerClass) {
        List<Mapping> result = new ArrayList<>();
        String basePath = "";
        RequestMapping classMapping = controllerClass.getAnnotation(RequestMapping.class);
        if (classMapping != null) {
            basePath = pathsAsString(classMapping.value(), classMapping.path());
        }
        for (Method m : controllerClass.getDeclaredMethods()) {
            for (Mapping found : parseMethodMappings(controllerClass.getSimpleName(), basePath, m)) {
                result.add(found);
            }
        }
        return result;
    }

    /** Method 上的 mapping 解析；当 method 没有声明 value/path 但声明了 HTTP method 时，回退 basePath。 */
    private static List<Mapping> parseMethodMappings(String className, String basePath, Method m) {
        List<Mapping> out = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        List<String> httpMethods = new ArrayList<>();
        // 也读 consumes 头用于消歧；同一 path × method 但 consumes 不同不算冲突
        // (简化：本测试关注 path × method，不模拟 consumes 消歧；只断言最直接的 path+method 重叠)
        boolean hasMethodSpecificAnnotation = false;
        for (Annotation a : m.getAnnotations()) {
            if (a instanceof GetMapping) {
                httpMethods.add("GET");
                paths.addAll(asList(((GetMapping) a).value(), ((GetMapping) a).path()));
                hasMethodSpecificAnnotation = true;
            } else if (a instanceof PostMapping) {
                httpMethods.add("POST");
                paths.addAll(asList(((PostMapping) a).value(), ((PostMapping) a).path()));
                hasMethodSpecificAnnotation = true;
            } else if (a instanceof PutMapping) {
                httpMethods.add("PUT");
                paths.addAll(asList(((PutMapping) a).value(), ((PutMapping) a).path()));
                hasMethodSpecificAnnotation = true;
            } else if (a instanceof DeleteMapping) {
                httpMethods.add("DELETE");
                paths.addAll(asList(((DeleteMapping) a).value(), ((DeleteMapping) a).path()));
                hasMethodSpecificAnnotation = true;
            } else if (a instanceof PatchMapping) {
                httpMethods.add("PATCH");
                paths.addAll(asList(((PatchMapping) a).value(), ((PatchMapping) a).path()));
                hasMethodSpecificAnnotation = true;
            } else if (a instanceof RequestMapping) {
                RequestMapping rm = (RequestMapping) a;
                paths.addAll(asList(rm.value(), rm.path()));
                if (rm.method().length > 0) {
                    for (RequestMethod rm2 : rm.method()) {
                        httpMethods.add(rm2.name());
                    }
                } else if (paths.isEmpty()) {
                    // 既无 path 也无 method：当 *PathAndMethod 占位；为简化，全部 method 各一条太啰嗦
                    httpMethods.add("ANY");
                    hasMethodSpecificAnnotation = true;
                } else {
                    httpMethods.add("ANY"); // 默认覆盖所有 HTTP method
                    hasMethodSpecificAnnotation = true;
                }
            }
        }
        if (!hasMethodSpecificAnnotation || httpMethods.isEmpty()) {
            return out;
        }
        // 当 method 上没声明 path，回退使用 basePath（即 class-level @RequestMapping 的路径）
        if (paths.isEmpty()) {
            paths.add("");  // 触发 joinPath 的 empty 分支，让其返回 basePath
        }
        for (String pathPart : paths) {
            String fullPath = joinPath(basePath, pathPart);
            for (String http : httpMethods) {
                out.add(new Mapping(className, http, fullPath));
            }
        }
        return out;
    }

    /** Stub 占位（实际实现见上方的第一段，重命名未沿用避免重复编译）。 */
    private static List<Mapping> parseMethodMappingsLEFTOUT(String a, String b, Method m) {
        return new ArrayList<>();
    }

    private static String pathsAsString(String[] arr1, String[] arr2) {
        return String.join("", asList(arr1, arr2));
    }

    private static List<String> asList(String[] arr1, String[] arr2) {
        List<String> out = new ArrayList<>();
        if (arr1 != null) for (String s : arr1) if (!s.isEmpty()) out.add(s);
        if (arr2 != null) for (String s : arr2) if (!s.isEmpty()) out.add(s);
        return out;
    }

    private static String joinPath(String base, String pathPart) {
        if (pathPart.startsWith("/") || pathPart.startsWith("{") || pathPart.isEmpty()) {
            return (base + pathPart);
        }
        return (base + "/" + pathPart);
    }
}
