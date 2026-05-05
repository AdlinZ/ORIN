package com.adlin.orin.modules.notification.service;

import com.adlin.orin.modules.notification.entity.SystemMessage;
import com.adlin.orin.modules.notification.entity.SystemMessageUserState;
import com.adlin.orin.modules.notification.repository.SystemMessageRepository;
import com.adlin.orin.modules.notification.repository.SystemMessageUserStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemNotificationServiceTest {

    @Mock
    private SystemMessageRepository messageRepository;

    @Mock
    private SystemMessageUserStateRepository stateRepository;

    @InjectMocks
    private SystemNotificationService service;

    @Test
    void mapsBroadcastReadStatePerUser() {
        SystemMessage broadcast = SystemMessage.builder()
                .id(10L)
                .title("Alert")
                .content("content")
                .scope("BROADCAST")
                .read(false)
                .build();
        SystemMessageUserState state = SystemMessageUserState.builder()
                .messageId(10L)
                .userId("user-a")
                .readAt(LocalDateTime.now())
                .build();

        when(messageRepository.findVisibleByUser(eq("user-a"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(broadcast)));
        when(stateRepository.findByUserIdAndMessageIdIn(eq("user-a"), eq(List.of(10L))))
                .thenReturn(List.of(state));

        SystemMessage result = service.getUserMessages("user-a", 0, 20, null).getContent().get(0);

        assertTrue(result.getRead());
        assertFalse(broadcast.getRead());
    }

    @Test
    void markBroadcastAsReadWritesUserStateWithoutGlobalUpdate() {
        SystemMessage broadcast = SystemMessage.builder()
                .id(10L)
                .scope("BROADCAST")
                .read(false)
                .build();
        when(messageRepository.findById(10L)).thenReturn(Optional.of(broadcast));
        when(stateRepository.findByMessageIdAndUserId(10L, "user-a")).thenReturn(Optional.empty());

        assertTrue(service.markAsRead(10L, "user-a"));

        ArgumentCaptor<SystemMessageUserState> captor = ArgumentCaptor.forClass(SystemMessageUserState.class);
        verify(stateRepository).save(captor.capture());
        assertEquals(10L, captor.getValue().getMessageId());
        assertEquals("user-a", captor.getValue().getUserId());
        assertNotNull(captor.getValue().getReadAt());
        verify(messageRepository, never()).markAsRead(anyLong(), anyString());
    }

    @Test
    void markAllAndDismissAllAreCurrentUserScoped() {
        when(messageRepository.findUnreadVisibleMessageIds("user-a")).thenReturn(List.of(1L, 2L));
        when(stateRepository.findByMessageIdAndUserId(anyLong(), eq("user-a"))).thenReturn(Optional.empty());

        int readCount = service.markAllAsRead("user-a");

        assertEquals(2, readCount);
        verify(stateRepository, times(2)).save(argThat(state -> state.getReadAt() != null
                && state.getDismissedAt() == null
                && "user-a".equals(state.getUserId())));

        clearInvocations(stateRepository);
        when(messageRepository.findVisibleMessageIds("user-a")).thenReturn(List.of(1L, 2L, 3L));
        when(stateRepository.findByMessageIdAndUserId(anyLong(), eq("user-a"))).thenReturn(Optional.empty());

        int cleared = service.dismissAllVisibleMessages("user-a");

        assertEquals(3, cleared);
        verify(stateRepository, times(3)).save(argThat(state -> state.getDismissedAt() != null
                && "user-a".equals(state.getUserId())));
    }
}
