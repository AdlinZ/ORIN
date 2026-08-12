package com.adlin.orin.modules.agent.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import com.adlin.orin.modules.system.repository.SysUserRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentOwnershipResolverTest {

    private final AgentOwnershipResolver resolver = new AgentOwnershipResolver(
            mock(SysUserRoleRepository.class),
            mock(SysUserRepository.class)
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void operatorCanOnlyAccessOwnResource() {
        authenticate("42", "ROLE_OPERATOR");

        assertTrue(resolver.canAccessOwnedResource(42L));
        assertFalse(resolver.canAccessOwnedResource(99L));
        assertFalse(resolver.canAccessOwnedResource(null));

        AgentMetadata owned = AgentMetadata.builder().agentId("a1").ownerUserId(42L).build();
        AgentMetadata foreign = AgentMetadata.builder().agentId("a2").ownerUserId(99L).build();
        assertDoesNotThrow(() -> resolver.assertCanAccessAgent(owned));
        assertThrows(BusinessException.class, () -> resolver.assertCanAccessAgent(foreign));
    }

    @Test
    void adminCanAccessAnyResource() {
        authenticate("1", "ROLE_ADMIN");

        assertTrue(resolver.canAccessOwnedResource(99L));
        assertTrue(resolver.canAccessOwnedResource(null));
        assertDoesNotThrow(() -> resolver.assertCanAccessOwnedResource(77L, "知识库"));
    }

    private void authenticate(String userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId,
                "n/a",
                List.of(new SimpleGrantedAuthority(role))
        ));
    }
}
