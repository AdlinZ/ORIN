package com.adlin.orin.security;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.runner.service.RunnerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentTokenAuthFilterTest {

    @Mock
    private RunnerService runnerService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validatesWithoutConsumingAndKeepsPlaintextOnlyForCurrentRequest() throws Exception {
        String plaintext = "sk-enroll-etk_1234567890abcdef1234567890abcdef.secret";
        when(runnerService.validateEnrollmentToken(plaintext)).thenReturn(
                new RunnerService.ValidatedEnrollmentToken(
                        "etk_1234567890abcdef1234567890abcdef", "runner-a", "admin-1"));
        EnrollmentTokenAuthFilter filter = new EnrollmentTokenAuthFilter(
                runnerService, new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", EnrollmentTokenAuthFilter.ENROLL_PATH);
        request.addHeader("Authorization", "Enrollment " + plaintext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reachedController = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> {
            reachedController.set(true);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            EnrollmentTokenPrincipal principal = assertInstanceOf(
                    EnrollmentTokenPrincipal.class, authentication.getPrincipal());
            assertEquals("admin-1", principal.getCreatedBy());
            assertEquals("runner-a", principal.getExpectedRunnerName());
            assertEquals(plaintext, authentication.getCredentials());
        });

        assertTrue(reachedController.get());
        verify(runnerService).validateEnrollmentToken(plaintext);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void usedOrInvalidTokenReturns422WithoutCallingController() throws Exception {
        String plaintext = "sk-enroll-etk_1234567890abcdef1234567890abcdef.secret";
        when(runnerService.validateEnrollmentToken(plaintext)).thenThrow(
                new BusinessException(ErrorCode.ENROLLMENT_TOKEN_INVALID, "已使用"));
        EnrollmentTokenAuthFilter filter = new EnrollmentTokenAuthFilter(
                runnerService, new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", EnrollmentTokenAuthFilter.ENROLL_PATH);
        request.addHeader("Authorization", "Enrollment " + plaintext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reachedController = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> reachedController.set(true));

        assertEquals(422, response.getStatus());
        assertTrue(response.getContentAsString().contains(ErrorCode.ENROLLMENT_TOKEN_INVALID.getCode()));
        assertEquals(false, reachedController.get());
    }
}
