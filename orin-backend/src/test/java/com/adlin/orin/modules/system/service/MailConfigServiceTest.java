package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.MailConfigEntity;
import com.adlin.orin.modules.system.repository.MailConfigRepository;
import com.adlin.orin.modules.system.repository.MailSendLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailConfigServiceTest {

    @Mock
    private MailConfigRepository mailConfigRepository;

    @Mock
    private MailSendLogRepository mailSendLogRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private RestTemplate restTemplate;

    @Test
    void saveConfigPreservesMaskedSecrets() {
        MailConfigEntity existing = new MailConfigEntity();
        existing.setId(1L);
        existing.setApiKey("real-api-key");
        existing.setPassword("real-smtp-password");
        existing.setImapPassword("real-imap-password");
        existing.setMailerType("resend");

        MailConfigEntity incoming = new MailConfigEntity();
        incoming.setId(1L);
        incoming.setApiKey("••••••••");
        incoming.setPassword("••••••••");
        incoming.setImapPassword("••••••••");
        incoming.setMailerType("resend");
        incoming.setFromEmail("noreply@example.com");
        incoming.setEnabled(true);

        when(mailConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(mailConfigRepository.save(any(MailConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MailConfigService service = new MailConfigService(mailConfigRepository, mailSendLogRepository, mailSender, restTemplate);
        service.saveConfig(incoming);

        ArgumentCaptor<MailConfigEntity> captor = ArgumentCaptor.forClass(MailConfigEntity.class);
        verify(mailConfigRepository).save(captor.capture());
        assertEquals("real-api-key", captor.getValue().getApiKey());
        assertEquals("real-smtp-password", captor.getValue().getPassword());
        assertEquals("real-imap-password", captor.getValue().getImapPassword());
    }
}
