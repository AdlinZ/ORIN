package com.adlin.orin.modules.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 多模态内容解析服务
 * 负责将不同类型的文件转换为可检索的文本内容
 * 
 * 支持的文件类型:
 * - 文档: PDF, DOCX, DOC, TXT, Markdown
 * - 图片: PNG, JPG, JPEG, GIF, BMP, WebP
 * - 音频: MP3, WAV, M4A, AAC, OGG
 * - 视频: MP4, MOV, AVI, MKV, WebM
 */
@Service
@Slf4j
public class MultimodalContentParserService {

    private final com.adlin.orin.modules.multimodal.service.VisualAnalysisService visualAnalysisService;
    private final com.adlin.orin.modules.model.service.SiliconFlowIntegrationService siliconFlowService;
    private final com.adlin.orin.modules.model.service.ModelConfigService modelConfigService;

    @Value("${knowledge.storage.root:storage/knowledge}")
    private String storageRoot;

    // 支持的文件类型
    private static final List<String> DOCUMENT_TYPES = Arrays.asList("pdf", "docx", "doc", "txt", "md", "markdown");
    private static final List<String> IMAGE_TYPES = Arrays.asList("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static final List<String> AUDIO_TYPES = Arrays.asList("mp3", "wav", "m4a", "aac", "ogg", "flac");
    private static final List<String> VIDEO_TYPES = Arrays.asList("mp4", "mov", "avi", "mkv", "webm", "flv");

    public MultimodalContentParserService(
            com.adlin.orin.modules.multimodal.service.VisualAnalysisService visualAnalysisService,
            com.adlin.orin.modules.model.service.SiliconFlowIntegrationService siliconFlowService,
            com.adlin.orin.modules.model.service.ModelConfigService modelConfigService) {
        this.visualAnalysisService = visualAnalysisService;
        this.siliconFlowService = siliconFlowService;
        this.modelConfigService = modelConfigService;
    }

    /**
     * 判断文件类型
     */
    public String getFileCategory(String fileType) {
        if (fileType == null) return "UNKNOWN";
        String ext = fileType.toLowerCase();
        
        if (DOCUMENT_TYPES.contains(ext)) return "DOCUMENT";
        if (IMAGE_TYPES.contains(ext)) return "IMAGE";
        if (AUDIO_TYPES.contains(ext)) return "AUDIO";
        if (VIDEO_TYPES.contains(ext)) return "VIDEO";
        
        return "UNKNOWN";
    }

    /**
     * 解析文件内容为文本
     * 
     * @param filePath 文件路径
     * @param fileType 文件扩展名
     * @return 解析后的文本内容
     */
    public String parseToText(String filePath, String fileType) {
        String category = getFileCategory(fileType);
        
        return switch (category) {
            case "DOCUMENT" -> parseDocument(filePath, fileType);
            case "IMAGE" -> parseImage(filePath);
            case "AUDIO" -> parseAudio(filePath);
            case "VIDEO" -> parseVideo(filePath);
            default -> {
                log.warn("Unsupported file type: {}", fileType);
                yield "";
            }
        };
    }

    /**
     * 异步解析文件内容
     */
    public CompletableFuture<String> parseToTextAsync(String filePath, String fileType) {
        return CompletableFuture.supplyAsync(() -> parseToText(filePath, fileType));
    }

    /**
     * 解析文档文件
     */
    private String parseDocument(String filePath, String fileType) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                log.error("File not found: {}", filePath);
                return "";
            }

            return switch (fileType.toLowerCase()) {
                case "txt", "md", "markdown" -> Files.readString(path);
                case "pdf" -> parsePdf(path);
                case "docx" -> parseDocx(path);
                case "doc" -> parseDoc(path);
                default -> {
                    log.warn("Unsupported document type: {}", fileType);
                    yield "";
                }
            };
        } catch (Exception e) {
            log.error("Failed to parse document: {}", filePath, e);
            return "";
        }
    }

    private String parsePdf(Path path) {
        try (var document = org.apache.pdfbox.Loader.loadPDF(path.toFile())) {
            var stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(document).trim();
        } catch (Exception e) {
            log.error("PDF parsing failed: {}", path, e);
            return "[PDF解析失败]";
        }
    }

    private String parseDocx(Path path) {
        try (var is = Files.newInputStream(path);
             var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(is)) {
            var sb = new StringBuilder();
            for (var p : doc.getParagraphs()) {
                sb.append(p.getText()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("DOCX parsing failed: {}", path, e);
            return "[DOCX解析失败]";
        }
    }

    private String parseDoc(Path path) {
        try (var is = Files.newInputStream(path);
             var doc = new org.apache.poi.hwpf.HWPFDocument(is)) {
            var extractor = new org.apache.poi.hwpf.extractor.WordExtractor(doc);
            var sb = new StringBuilder();
            for (String para : extractor.getParagraphText()) {
                sb.append(para).append("\n");
            }
            extractor.close();
            return sb.toString();
        } catch (Exception e) {
            log.error("DOC parsing failed: {}", path, e);
            return "[DOC解析失败]";
        }
    }

    /**
     * 解析图片文件 - 使用 VLM 进行 OCR 和内容描述
     */
    private String parseImage(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "[图片文件不存在]";
            }

            // 转换为 base64 或使用文件 URL
            String imageUrl = path.toUri().toString();
            
            // 使用 VLM 进行详细的图像描述（可作为 OCR 使用）
            // 提示词强调识别文字
            String prompt = "请详细识别并提取这张图片中的所有文字内容。如果图片中有文字，请完整转录。如果图片是截图、文档扫描件、表格或其他包含文字的图片，请尽可能准确地识别所有文字。";
            
            String result = visualAnalysisService.analyzeImage(imageUrl, null);
            return result != null ? result : "[图片解析失败]";
        } catch (Exception e) {
            log.error("Image parsing failed: {}", filePath, e);
            return "[图片解析失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 解析音频文件 - 使用语音识别
     */
    private String parseAudio(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "[音频文件不存在]";
            }

            byte[] audioData = Files.readAllBytes(path);
            String filename = path.getFileName().toString();

            // 使用 SiliconFlow ASR
            var config = modelConfigService.getConfig();
            String apiKey = config != null ? config.getSiliconFlowApiKey() : null;
            String endpoint = config != null ? config.getSiliconFlowEndpoint() : null;
            
            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("SiliconFlow API key not configured, audio transcription skipped");
                return "[音频转写失败: 未配置 SiliconFlow API Key]";
            }
            
            // 默认使用 Paraformer 模型
            String model = "paraformer-zh";

            var result = siliconFlowService.transcribeAudio(endpoint, apiKey, model, audioData, filename);
            
            if (result.isPresent()) {
                // 解析返回结果
                Object res = result.get();
                if (res instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) res;
                    Object text = map.get("text");
                    if (text != null) {
                        return text.toString();
                    }
                }
                // 如果返回的是字符串
                if (res instanceof String) {
                    return res.toString();
                }
            }
            
            return "[音频转写失败 - 未返回有效结果]";
        } catch (Exception e) {
            log.error("Audio parsing failed: {}", filePath, e);
            return "[音频解析失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 解析视频文件 - 提取音频后转写
     * 注意: 视频解析需要 FFmpeg 支持提取音频
     */
    private String parseVideo(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "[视频文件不存在]";
            }

            // 尝试使用 FFmpeg 提取音频
            String audioPath = extractAudio(path);
            
            if (audioPath != null && Files.exists(Paths.get(audioPath))) {
                try {
                    // 解析提取的音频
                    String result = parseAudio(audioPath);
                    // 清理临时音频文件
                    try {
                        Files.deleteIfExists(Paths.get(audioPath));
                    } catch (Exception e) {
                        log.warn("Failed to delete temp audio file: {}", audioPath);
                    }
                    return result;
                } catch (Exception e) {
                    log.error("Failed to transcribe extracted audio", e);
                    return "[视频音频转写失败: " + e.getMessage() + "]";
                }
            }
            
            // 如果无法提取音频，返回提示信息
            return "[视频解析需要 FFmpeg 支持，或请上传单独的音频文件]";
        } catch (Exception e) {
            log.error("Video parsing failed: {}", filePath, e);
            return "[视频解析失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 使用 FFmpeg 提取视频中的音频
     * 返回临时音频文件路径
     */
    private String extractAudio(Path videoPath) {
        try {
            // 检查 FFmpeg 是否可用
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            Process p = pb.start();
            int exitCode = p.waitFor();
            
            if (exitCode != 0) {
                log.warn("FFmpeg not available");
                return null;
            }

            // 创建临时音频文件
            Path tempAudio = Files.createTempFile("video_audio_", ".wav");
            
            // 使用 FFmpeg 提取音频
            pb = new ProcessBuilder(
                "ffmpeg", "-i", videoPath.toString(), 
                "-vn", "-acodec", "pcm_s16le", 
                "-ar", "16000", "-ac", "1",
                "-y", tempAudio.toString()
            );
            pb.redirectErrorStream(true);
            p = pb.start();
            
            // 等待完成，最多 5 分钟
            boolean finished = p.waitFor(300, java.util.concurrent.TimeUnit.SECONDS);
            
            if (finished && p.exitValue() == 0) {
                return tempAudio.toString();
            } else {
                log.warn("FFmpeg extraction failed with code: {}", p.exitValue());
                Files.deleteIfExists(tempAudio);
                return null;
            }
        } catch (Exception e) {
            log.error("Audio extraction failed", e);
            return null;
        }
    }

    /**
     * 获取知识库的存储根目录
     */
    public String getStorageRoot() {
        return storageRoot;
    }

    /**
     * 获取知识库的完整目录结构路径
     * 结构: {storageRoot}/{kbId}/
     *   ├── raw/           # 原始上传文件
     *   ├── parsed/        # 解析后的文本内容
     *   ├── metadata/      # 元数据信息
     *   └── chunks/        # 分块后的文本
     */
    public Path getKnowledgeBasePath(String kbId) {
        return Paths.get(storageRoot, kbId);
    }

    public Path getRawPath(String kbId) {
        return Paths.get(storageRoot, kbId, "raw");
    }

    public Path getParsedPath(String kbId) {
        return Paths.get(storageRoot, kbId, "parsed");
    }

    public Path getMetadataPath(String kbId) {
        return Paths.get(storageRoot, kbId, "metadata");
    }

    public Path getChunksPath(String kbId) {
        return Paths.get(storageRoot, kbId, "chunks");
    }

    /**
     * 初始化知识库目录结构
     */
    public void initKnowledgeBaseDirectories(String kbId) throws IOException {
        Path kbPath = getKnowledgeBasePath(kbId);
        Files.createDirectories(kbPath);
        Files.createDirectories(getRawPath(kbId));
        Files.createDirectories(getParsedPath(kbId));
        Files.createDirectories(getMetadataPath(kbId));
        Files.createDirectories(getChunksPath(kbId));
        log.info("Initialized knowledge base directory structure: {}", kbPath);
    }

    /**
     * 保存解析后的文本
     */
    public String saveParsedText(String kbId, String documentId, String content) throws IOException {
        Path parsedPath = getParsedPath(kbId);
        Files.createDirectories(parsedPath);
        
        Path textFile = parsedPath.resolve(documentId + ".txt");
        Files.writeString(textFile, content);
        
        return textFile.toString();
    }

    /**
     * 读取解析后的文本
     */
    public String readParsedText(String kbId, String documentId) throws IOException {
        Path textFile = getParsedPath(kbId).resolve(documentId + ".txt");
        if (Files.exists(textFile)) {
            return Files.readString(textFile);
        }
        return null;
    }
}
