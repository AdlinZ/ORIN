package com.adlin.orin.modules.skill.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.security.BaseOwnershipResolver;
import com.adlin.orin.modules.skill.entity.SkillEntity;
import com.adlin.orin.modules.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Owner ACL for user-created skills; system skills remain shared and read-only. */
@Component
@RequiredArgsConstructor
public class SkillOwnershipService extends BaseOwnershipResolver {

    private final SkillRepository repository;

    public String currentUserKey() {
        return resolveFromCurrentRequest().toString();
    }

    public boolean canRead(String createdBy) {
        return isCurrentUserPrivileged()
                || createdBy == null
                || "system".equalsIgnoreCase(createdBy)
                || currentUserKey().equals(createdBy);
    }

    public void assertCanRead(Long skillId) {
        SkillEntity skill = find(skillId);
        if (!canRead(skill.getCreatedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该 Skill");
        }
    }

    public void assertCanManage(Long skillId) {
        SkillEntity skill = find(skillId);
        if (isCurrentUserPrivileged() || currentUserKey().equals(skill.getCreatedBy())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该 Skill");
    }

    public void assertCanCreateType(SkillEntity.SkillType skillType) {
        if (isCurrentUserPrivileged()) {
            return;
        }
        if (skillType == SkillEntity.SkillType.SHELL || skillType == SkillEntity.SkillType.API) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "普通用户不能创建 Shell 或任意 API 类型的 Skill");
        }
    }

    public void assertCanExecute(Long skillId) {
        SkillEntity skill = find(skillId);
        if (!canRead(skill.getCreatedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权执行该 Skill");
        }
        if (!isCurrentUserPrivileged() && skill.getSkillType() == SkillEntity.SkillType.SHELL) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Shell Skill 仅限管理员执行");
        }
    }

    private SkillEntity find(Long skillId) {
        return repository.findById(skillId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Skill 不存在"));
    }
}
