package com.adlin.orin.security;

import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.runner.entity.RunnerCredential;
import com.adlin.orin.modules.runner.service.RunnerCredentialService;
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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunnerCredentialAuthFilterTest {

    @Mock
    private RunnerCredentialService credentialService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeCredentialBuildsRunnerPrincipalForMachineRequest() throws Exception {
        String plaintext = "sk-runner-rcred_1.secret";
        RunnerCredential credential = RunnerCredential.builder()
                .runnerId("run_1")
                .credentialId("rcred_1")
                .status(RunnerCredential.Status.ACTIVE)
                .build();
        when(credentialService.validateCredential(plaintext)).thenReturn(Optional.of(credential));
        RunnerCredentialAuthFilter filter = new RunnerCredentialAuthFilter(
                credentialService, new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = machineRequest(plaintext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reachedController = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> {
            reachedController.set(true);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            RunnerPrincipal principal = assertInstanceOf(
                    RunnerPrincipal.class, authentication.getPrincipal());
            assertEquals("run_1", principal.getRunnerId());
            assertEquals("rcred_1", principal.getCredentialId());
        });

        assertTrue(reachedController.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void unknownCredentialReturns401() throws Exception {
        String plaintext = "sk-runner-rcred_1.secret";
        when(credentialService.validateCredential(plaintext)).thenReturn(Optional.empty());
        RunnerCredentialAuthFilter filter = new RunnerCredentialAuthFilter(
                credentialService, new ObjectMapper().findAndRegisterModules());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(machineRequest(plaintext), response,
                (req, res) -> { throw new AssertionError("controller must not be called"); });

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains(
                ErrorCode.RUNNER_CREDENTIAL_INVALID.getCode()));
    }

    @Test
    void revokedCredentialReturns403() throws Exception {
        String plaintext = "sk-runner-rcred_1.secret";
        RunnerCredential credential = RunnerCredential.builder()
                .runnerId("run_1")
                .credentialId("rcred_1")
                .status(RunnerCredential.Status.REVOKED)
                .build();
        when(credentialService.validateCredential(plaintext)).thenReturn(Optional.of(credential));
        RunnerCredentialAuthFilter filter = new RunnerCredentialAuthFilter(
                credentialService, new ObjectMapper().findAndRegisterModules());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(machineRequest(plaintext), response,
                (req, res) -> { throw new AssertionError("controller must not be called"); });

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains(ErrorCode.RUNNER_REVOKED.getCode()));
    }

    private MockHttpServletRequest machineRequest(String plaintext) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/system/runners/run_1/heartbeat");
        request.addHeader("Authorization", "Runner " + plaintext);
        return request;
    }
}
