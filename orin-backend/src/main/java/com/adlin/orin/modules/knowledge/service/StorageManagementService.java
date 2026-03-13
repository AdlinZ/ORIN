package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.parser.ParserFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 存储管理服务
 * 管理知识库的文件存储目录结构
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageManagementService {

    @Value("${knowledge.storage.path:storage/knowledge}")
    private String storageRoot;

    private final ParserFactory parserFactory;

    /**
     * 获取知识库的根目录
     */
    public Path getKnowledgeBasePath(String knowledgeBaseId) {
        return Paths.get(storageRoot, knowledgeBaseId).toAbsolutePath().normalize();
    }

    /**
     * 获取原始文件存储目录
     */
    public Path getDataPath(String knowledgeBaseId) {
        return getKnowledgeBasePath(knowledgeBaseId).resolve("data");
    }

    /**
     * 获取解析后文本存储目录
     */
    public Path getParsedPath(String knowledgeBaseId) {
        return getKnowledgeBasePath(knowledgeBaseId).resolve("parsed");
    }

    /**
     * 获取元数据存储目录
     */
    public Path getMetaPath(String knowledgeBaseId) {
        return getKnowledgeBasePath(knowledgeBaseId).resolve("meta");
    }

    /**
     * 根据文件类型获取具体的子目录
     */
    public Path getDataTypePath(String knowledgeBaseId, String fileType) {
        String category = ParserFactory.getMediaCategory(fileType);
        return getDataPath(knowledgeBaseId).resolve(category);
    }

    /**
     * 初始化知识库存储目录
     */
    public void initializeKnowledgeBase(String knowledgeBaseId) throws IOException {
        Path kbPath = getKnowledgeBasePath(knowledgeBaseId);

        // 创建目录结构：data/, parsed/, meta/
        Files.createDirectories(kbPath);
        Files.createDirectories(getDataPath(knowledgeBaseId));
        Files.createDirectories(getParsedPath(knowledgeBaseId));
        Files.createDirectories(getMetaPath(knowledgeBaseId));

        // 创建子目录
        for (String type : new String[]{"pdf", "images", "audio", "video", "text"}) {
            Files.createDirectories(getDataPath(knowledgeBaseId).resolve(type));
        }

        // 创建 meta 子目录（用于存储索引文件信息、版本历史等）
        Files.createDirectories(getMetaPath(knowledgeBaseId).resolve("index"));
        Files.createDirectories(getMetaPath(knowledgeBaseId).resolve("history"));

        log.info("Initialized storage for knowledge base: {}", knowledgeBaseId);
    }

    /**
     * 保存文件到存储
     * @param knowledgeBaseId 知识库 ID
     * @param fileType 文件类型/扩展名
     * @param content 文件内容
     * @param originalFilename 原始文件名
     * @return 存储路径和生成的唯一文件名
     */
    public StorageResult storeFile(String knowledgeBaseId, String fileType, byte[] content, String originalFilename) throws IOException {
        // 初始化目录
        initializeKnowledgeBase(knowledgeBaseId);

        // 生成唯一文件名
        String uniqueFilename = UUID.randomUUID().toString() + "." + fileType.toLowerCase();
        String category = ParserFactory.getMediaCategory(fileType);

        // 确定存储路径
        Path targetDir = getDataPath(knowledgeBaseId).resolve(category);
        Files.createDirectories(targetDir);

        Path targetPath = targetDir.resolve(uniqueFilename);

        // 写入文件
        Files.write(targetPath, content);

        log.info("Stored file: {} -> {}", originalFilename, targetPath);

        return new StorageResult(
                targetPath.toString(),
                uniqueFilename,
                targetDir.toString(),
                category
        );
    }

    /**
     * 删除知识库的所有存储文件
     */
    public void deleteKnowledgeBaseStorage(String knowledgeBaseId) throws IOException {
        Path kbPath = getKnowledgeBasePath(knowledgeBaseId);
        if (Files.exists(kbPath)) {
            deleteDirectory(kbPath);
            log.info("Deleted storage for knowledge base: {}", knowledgeBaseId);
        }
    }

    /**
     * 删除文档的存储文件
     */
    public void deleteDocumentStorage(String storagePath) throws IOException {
        if (storagePath != null) {
            Path path = Paths.get(storagePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("Deleted document storage: {}", storagePath);
            }
        }
    }

    /**
     * 获取解析后文本的路径
     */
    public Path getParsedFilePath(String knowledgeBaseId, String documentId, String fileType) {
        return getParsedPath(knowledgeBaseId).resolve(documentId + ".txt");
    }

    /**
     * 获取索引元数据文件路径
     */
    public Path getIndexMetaPath(String knowledgeBaseId, String documentId) {
        return getMetaPath(knowledgeBaseId).resolve("index").resolve(documentId + ".json");
    }

    /**
     * 获取版本历史文件路径
     */
    public Path getHistoryMetaPath(String knowledgeBaseId, String documentId) {
        return getMetaPath(knowledgeBaseId).resolve("history").resolve(documentId + ".json");
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteDirectory(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    /**
     * 存储结果
     */
    public record StorageResult(
            String fullPath,
            String uniqueFilename,
            String directory,
            String category
    ) {}
}
