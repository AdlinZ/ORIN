package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.dto.UserResponseDTO;
import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementQueryServiceTest {

    @Mock
    private SysUserRepository userRepository;

    private UserManagementQueryService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementQueryService(userRepository);
    }

    @Test
    void appliesFiltersBeforePaginationAndReturnsFilteredTotal() {
        SysUser user = new SysUser();
        user.setUserId(7L);
        user.setUsername("alice");
        user.setEmail("alice@example.test");
        user.setRole("ROLE_USER");
        user.setDepartmentId(3L);
        user.setStatus("ENABLED");

        when(userRepository.findAllFiltered(
                eq("alice"),
                eq("ROLE_USER"),
                eq(3L),
                eq("ENABLED"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(1, 20), 41));

        Map<String, Object> result = service.getUserList(
                1,
                20,
                " alice ",
                " ROLE_USER ",
                3L,
                "active");

        assertEquals(41L, result.get("total"));
        assertEquals(1, result.get("page"));
        assertEquals(20, result.get("size"));
        @SuppressWarnings("unchecked")
        List<UserResponseDTO> data = (List<UserResponseDTO>) result.get("data");
        assertEquals(1, data.size());
        assertEquals(7L, data.get(0).getId());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAllFiltered(
                eq("alice"),
                eq("ROLE_USER"),
                eq(3L),
                eq("ENABLED"),
                pageableCaptor.capture());
        assertEquals("userId: DESC", pageableCaptor.getValue().getSort().toString());
    }

    @Test
    void normalizesEmptyFiltersStatusAndPagingBounds() {
        when(userRepository.findAllFiltered(
                eq(null),
                eq(null),
                eq(null),
                eq("DISABLED"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        Map<String, Object> result = service.getUserList(-4, 500, " ", "", null, "inactive");

        assertEquals(0, result.get("page"));
        assertEquals(100, result.get("size"));
        @SuppressWarnings("unchecked")
        List<UserResponseDTO> data = (List<UserResponseDTO>) result.get("data");
        assertEquals(List.of(), data);
    }

    @Test
    void treatsAllAndUnknownStatusAsNoFilter() {
        when(userRepository.findAllFiltered(
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.getUserList(0, 20, null, null, null, "all");
        service.getUserList(0, 20, null, null, null, "not-a-status");

        verify(userRepository, org.mockito.Mockito.times(2)).findAllFiltered(
                eq(null), eq(null), eq(null), eq(null), any(Pageable.class));
    }
}
