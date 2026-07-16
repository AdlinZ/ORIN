package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.dto.UserResponseDTO;
import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 用户管理列表查询服务。
 *
 * <p>所有筛选条件必须在数据库分页之前生效，保证 data、total 与分页器使用同一口径。</p>
 */
@Service
@RequiredArgsConstructor
public class UserManagementQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getUserList(
            int page,
            int size,
            String search,
            String role,
            Long departmentId,
            String status) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(
                normalizedPage,
                normalizedSize,
                Sort.by(Sort.Direction.DESC, "userId"));

        Page<SysUser> userPage = userRepository.findAllFiltered(
                blankToNull(search),
                blankToNull(role),
                departmentId,
                normalizeStatus(status),
                pageable);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", userPage.getContent().stream().map(UserResponseDTO::fromEntity).toList());
        result.put("total", userPage.getTotalElements());
        result.put("page", userPage.getNumber());
        result.put("size", userPage.getSize());
        result.put("totalPages", userPage.getTotalPages());
        return result;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String status) {
        String value = blankToNull(status);
        if (value == null || "all".equalsIgnoreCase(value)) {
            return null;
        }

        return switch (value.toUpperCase(Locale.ROOT)) {
            case "ACTIVE", "ENABLED" -> "ENABLED";
            case "INACTIVE", "DISABLED" -> "DISABLED";
            default -> null;
        };
    }
}
