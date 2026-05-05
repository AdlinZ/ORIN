package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.audit.service.AuditLogService;
import com.adlin.orin.modules.system.entity.MailConfigEntity;
import com.adlin.orin.modules.system.entity.MailSendLog;
import com.adlin.orin.modules.system.repository.MailConfigRepository;
import com.adlin.orin.modules.system.repository.MailSendLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MailConfigRepository mailConfigRepository;

    @Mock
    private MailSendLogRepository mailSendLogRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AuditLogService auditLogService;

    private MailService service;

    @BeforeEach
    void setUp() {
        service = new MailService(mailSender, mailConfigRepository, mailSendLogRepository, restTemplate, auditLogService);
    }

    @Test
    void alertBatchUsesMailerSendProvider() {
        MailConfigEntity config = config("mailersend");
        when(mailConfigRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc()).thenReturn(Optional.of(config));
        when(restTemplate.exchange(eq("https://api.mailersend.com/v1/email"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.ACCEPTED));

        assertTrue(service.sendAlertEmail(new String[]{"a@example.com", "b@example.com"}, "Subject", "Body"));

        verify(restTemplate).exchange(eq("https://api.mailersend.com/v1/email"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verifySuccessLog("mailersend");
    }

    @Test
    void alertBatchUsesResendProvider() {
        MailConfigEntity config = config("resend");
        when(mailConfigRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc()).thenReturn(Optional.of(config));
        when(restTemplate.exchange(eq("https://api.resend.com/emails"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        assertTrue(service.sendAlertEmail(new String[]{"a@example.com", "b@example.com"}, "Subject", "Body"));

        verify(restTemplate, times(2)).exchange(eq("https://api.resend.com/emails"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verifySuccessLog("resend");
    }

    @Test
    void alertBatchUsesSmtpProviderWithoutApiFallback() {
        MailConfigEntity config = config("smtp");
        MailService spyService = spy(service);
        when(mailConfigRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc()).thenReturn(Optional.of(config));
        doReturn(mailSender).when(spyService).createSmtpSender(config);
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        assertTrue(spyService.sendAlertEmail(new String[]{"a@example.com", "b@example.com"}, "Subject", "Body"));

        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
        verifySuccessLog("smtp");
    }

    private MailConfigEntity config(String mailerType) {
        MailConfigEntity config = new MailConfigEntity();
        config.setEnabled(true);
        config.setMailerType(mailerType);
        config.setApiKey("key");
        config.setFromEmail("noreply@example.com");
        config.setFromName("ORIN");
        config.setSmtpHost("smtp.example.com");
        config.setSmtpPort(587);
        config.setUsername("user");
        config.setPassword("password");
        config.setSslEnabled(true);
        return config;
    }

    private void verifySuccessLog(String mailerType) {
        ArgumentCaptor<MailSendLog> captor = ArgumentCaptor.forClass(MailSendLog.class);
        verify(mailSendLogRepository).save(captor.capture());
        assertEquals(MailSendLog.STATUS_SUCCESS, captor.getValue().getStatus());
        assertEquals(mailerType, captor.getValue().getMailerType());
    }
}
