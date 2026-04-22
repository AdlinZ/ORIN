package com.adlin.orin.common.service;

import com.adlin.orin.common.storage.ObjectStorageProvider;
import com.adlin.orin.common.storage.StorageBackend;
import com.adlin.orin.common.storage.StorageProperties;
import com.adlin.orin.common.storage.StorageProviderRegistry;
import com.adlin.orin.common.storage.StorageReplicationTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DualFileStorageServiceTest {

    private StorageProviderRegistry providerRegistry;
    private StorageReplicationTaskRepository taskRepository;
    private ObjectStorageProvider localProvider;
    private ObjectStorageProvider minioProvider;
    private DualFileStorageService service;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.setMode("dual");
        props.setPrimary("local");
        props.setSecondary("minio");
        props.setReadFallback(true);
        props.setWriteAsyncRepair(true);

        providerRegistry = mock(StorageProviderRegistry.class);
        taskRepository = mock(StorageReplicationTaskRepository.class);
        localProvider = mock(ObjectStorageProvider.class);
        minioProvider = mock(ObjectStorageProvider.class);

        when(providerRegistry.provider(StorageBackend.LOCAL)).thenReturn(localProvider);
        when(providerRegistry.provider(StorageBackend.MINIO)).thenReturn(minioProvider);

        service = new DualFileStorageService(props, providerRegistry, taskRepository);
    }

    @Test
    void storeBytes_secondaryWriteFails_marksPendingRepairAndEnqueuesTask() throws Exception {
        when(localProvider.put(anyString(), any(), anyLong(), anyString(), anyMap())).thenReturn("/tmp/a.txt");
        when(minioProvider.put(anyString(), any(), anyLong(), anyString(), anyMap()))
                .thenThrow(new IOException("minio down"));

        FileStorageService.StoredFile stored = service.storeBytesDetailed(
                "hello".getBytes(),
                "a.txt",
                "knowledge/kb-1/raw",
                "text/plain");

        assertEquals("/tmp/a.txt", stored.locator());
        assertEquals("PENDING_REPAIR", stored.replicationStatus());
        verify(taskRepository, atLeastOnce()).save(any());
    }

    @Test
    void openStream_primaryFails_fallbackSucceeds() throws Exception {
        when(localProvider.get(anyString())).thenThrow(new IOException("local down"));
        byte[] data = "fallback".getBytes();
        when(minioProvider.get(anyString())).thenReturn(new ByteArrayInputStream(data));

        byte[] actual = service.openStream("minio:test/path.txt").readAllBytes();
        assertArrayEquals(data, actual);
    }

    @Test
    void generateDownloadUrl_usesPrimaryThenFallback() {
        when(localProvider.presignGetUrl(anyString(), any())).thenReturn(null);
        when(minioProvider.presignGetUrl(anyString(), any())).thenReturn("https://signed-url");

        String url = service.generateDownloadUrl("/tmp/a.txt", Duration.ofMinutes(10));
        assertEquals("https://signed-url", url);
    }
}
