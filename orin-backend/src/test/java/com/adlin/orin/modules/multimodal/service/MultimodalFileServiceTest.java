package com.adlin.orin.modules.multimodal.service;

import com.adlin.orin.modules.multimodal.entity.MultimodalFile;
import com.adlin.orin.modules.multimodal.repository.MultimodalFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultimodalFileServiceTest {

    @Mock
    private MultimodalFileRepository fileRepository;

    @InjectMocks
    private MultimodalFileService fileService;

    @Test
    void testGetAllFiles() {
        MultimodalFile file = MultimodalFile.builder()
                .id("file-1")
                .fileName("test.jpg")
                .fileType("IMAGE")
                .build();

        when(fileRepository.findAll()).thenReturn(Arrays.asList(file));

        List<MultimodalFile> result = fileService.getAllFiles();

        assertEquals(1, result.size());
        assertEquals("test.jpg", result.get(0).getFileName());
    }

    @Test
    void testGetFilesByType() {
        MultimodalFile file = MultimodalFile.builder()
                .id("file-1")
                .fileName("test.jpg")
                .fileType("IMAGE")
                .build();

        when(fileRepository.findByFileTypeOrderByUploadedAtDesc("IMAGE"))
                .thenReturn(Arrays.asList(file));

        List<MultimodalFile> result = fileService.getFilesByType("IMAGE");

        assertEquals(1, result.size());
        assertEquals("IMAGE", result.get(0).getFileType());
    }

    @Test
    void testGetFile() {
        MultimodalFile file = MultimodalFile.builder()
                .id("file-1")
                .fileName("test.jpg")
                .build();

        when(fileRepository.findById("file-1")).thenReturn(Optional.of(file));

        MultimodalFile result = fileService.getFile("file-1");

        assertNotNull(result);
        assertEquals("test.jpg", result.getFileName());
    }

    @Test
    void testGetStats() {
        when(fileRepository.count()).thenReturn(10L);
        when(fileRepository.countByFileType("IMAGE")).thenReturn(5L);
        when(fileRepository.countByFileType("AUDIO")).thenReturn(2L);
        when(fileRepository.countByFileType("VIDEO")).thenReturn(1L);
        when(fileRepository.countByFileType("DOCUMENT")).thenReturn(2L);

        MultimodalFileService.FileStats stats = fileService.getStats();

        assertEquals(10L, stats.totalFiles());
        assertEquals(5L, stats.imageCount());
        assertEquals(2L, stats.audioCount());
    }
}
