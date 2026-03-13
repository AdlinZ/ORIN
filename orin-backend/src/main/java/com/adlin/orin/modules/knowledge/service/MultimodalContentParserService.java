package com.adlin.orin.modules.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
        return parseToText(filePath, fileType, null);
    }

    /**
     * 解析文件内容为文本（带配置）
     *
     * @param filePath 文件路径
     * @param fileType 文件扩展名
     * @param config   解析配置，可包含 ocr_model, asr_model, richText 等
     * @return 解析后的文本内容
     */
    public String parseToText(String filePath, String fileType, Map<String, String> config) {
        // 判断是否启用富文本解析
        boolean richText = config != null && "true".equalsIgnoreCase(config.get("richText"));

        String category = getFileCategory(fileType);

        return switch (category) {
            case "DOCUMENT" -> richText ? parseDocument(filePath, fileType, true) : parseDocument(filePath, fileType, false);
            case "IMAGE" -> {
                try {
                    yield parseImage(filePath, config);
                } catch (Exception e) {
                    log.error("Image parsing failed: {}", filePath, e);
                    yield "[图片解析失败: " + e.getMessage() + "]";
                }
            }
            case "AUDIO" -> parseAudio(filePath, config);
            case "VIDEO" -> parseVideo(filePath, config);
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
        return parseDocument(filePath, fileType, false);
    }

    /**
     * 解析文档文件（带配置）
     *
     * @param filePath 文件路径
     * @param fileType 文件扩展名
     * @param richText 是否启用富文本解析
     * @return 解析后的文本内容
     */
    private String parseDocument(String filePath, String fileType, boolean richText) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                log.error("File not found: {}", filePath);
                return "";
            }

            return switch (fileType.toLowerCase()) {
                case "txt", "md", "markdown" -> Files.readString(path);
                case "pdf" -> richText ? parsePdfRich(path) : parsePdf(path);
                case "docx" -> richText ? parseDocxRich(path) : parseDocx(path);
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

    /**
     * 富文本解析 PDF - 保留标题层级和段落结构
     */
    private String parsePdfRich(Path path) {
        try (var document = org.apache.pdfbox.Loader.loadPDF(path.toFile())) {
            var stripper = new org.apache.pdfbox.text.PDFTextStripper();
            var sb = new StringBuilder();

            // 按页码遍历
            int pageCount = document.getNumberOfPages();
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document).trim();

                if (!pageText.isEmpty()) {
                    // 检测标题（简单启发式：大字号或全大写行）
                    String[] lines = pageText.split("\n");
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) continue;

                        // 简单标题检测：短行+全大写 或 包含特定关键词
                        if (isLikelyHeading(trimmed)) {
                            sb.append("\n## ").append(trimmed).append("\n");
                        } else {
                            sb.append(trimmed).append("\n");
                        }
                    }
                    sb.append("\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("PDF rich parsing failed: {}", path, e);
            // 回退到普通解析
            return parsePdf(path);
        }
    }

    /**
     * 判断是否可能是标题
     */
    private boolean isLikelyHeading(String line) {
        // 全大写的短行可能是标题
        if (line.length() <= 100 && line.equals(line.toUpperCase()) && line.length() > 3) {
            return true;
        }
        // 常见标题关键词
        String[] headingKeywords = {"第", "章", "节", "概述", "摘要", "目录", "前言", "结论", "参考文献", "附录"};
        for (String kw : headingKeywords) {
            if (line.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 富文本解析 DOCX - 保留标题样式和段落结构
     */
    private String parseDocxRich(Path path) {
        try (var is = Files.newInputStream(path);
             var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(is)) {
            var sb = new StringBuilder();

            // 获取文档中的所有样式
            var styles = doc.getStyles();

            // 遍历所有段落
            for (var para : doc.getParagraphs()) {
                String text = para.getText().trim();
                if (text.isEmpty()) {
                    sb.append("\n");
                    continue;
                }

                // 获取段落的样式名称
                String styleName = para.getStyleID();

                // 检测是否是标题（通过样式名称）
                boolean isHeading = false;
                if (styleName != null && styleName.toLowerCase().contains("heading")) {
                    isHeading = true;
                }

                // 根据文本特征判断标题
                if (isHeading || isLikelyHeading(text)) {
                    sb.append("\n## ").append(text).append("\n");
                } else {
                    sb.append(text).append("\n");
                }
            }

            // 处理表格
            for (var table : doc.getTables()) {
                sb.append("\n[表格内容]\n");
                for (var row : table.getRows()) {
                    var cells = row.getTableCells();
                    StringBuilder rowText = new StringBuilder();
                    for (var cell : cells) {
                        String cellText = cell.getText().trim().replace("\n", " ");
                        if (!cellText.isEmpty()) {
                            rowText.append(cellText).append(" | ");
                        }
                    }
                    if (rowText.length() > 0) {
                        sb.append("| ").append(rowText).append("\n");
                    }
                }
            }

            return sb.toString().trim();
        } catch (Exception e) {
            log.error("DOCX rich parsing failed: {}", path, e);
            // 回退到普通解析
            return parseDocx(path);
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
    private String parseImage(String filePath, Map<String, String> config) throws Exception {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new Exception("图片文件不存在: " + filePath);
        }

        byte[] imageData = Files.readAllBytes(path);
        log.info("Image file loaded: {}, size: {} bytes", filePath, imageData.length);

        String mimeType = getMimeType(path.getFileName().toString());
        String base64Image = Base64.getEncoder().encodeToString(imageData);
        String imageDataUri = "data:" + mimeType + ";base64," + base64Image;

        // 打印调试信息
        log.info("=== VLM Debug Info ===");
        log.info("File: {}", path.getFileName());
        log.info("MimeType: {}", mimeType);
        log.info("Base64 length: {}", base64Image.length());
        log.info("DataURI prefix: data:{};base64,", mimeType);
        log.info("First 100 chars of base64: {}", base64Image.substring(0, Math.min(100, base64Image.length())));

        // 优先使用传入的配置中的模型，否则使用全局默认配置
        String vlmModel = null;
        if (config != null && config.containsKey("ocr_model") && config.get("ocr_model") != null && !config.get("ocr_model").isEmpty()) {
            vlmModel = config.get("ocr_model");
            log.info("Using VLM model from knowledge base config: {}", vlmModel);
        } else {
            var globalConfig = modelConfigService.getConfig();
            vlmModel = globalConfig.getVlmModel();
            log.info("Using VLM model from global config: {}", vlmModel);
        }

        // 检查 VLM 模型是否配置
        if (vlmModel == null || vlmModel.isEmpty()) {
            throw new Exception("VLM 模型未配置，请在系统设置中配置视觉大模型，或在创建知识库时选择 OCR 模型");
        }

        // 使用 VLM 进行 OCR 识别 - 使用默认 prompt
        log.info("Calling VLM for OCR on image: {}, model: {}, will process {} bytes of base64 data",
                filePath, vlmModel, base64Image.length());
        String result = visualAnalysisService.analyzeImage(imageDataUri, vlmModel, null);
        log.info("VLM returned result for {}: {} chars", filePath, result != null ? result.length() : 0);

        // 检查返回结果是否包含失败标记
        if (result == null || result.isEmpty() || result.contains("解析失败") || result.contains("Analysis Failed") || result.contains("API failed")) {
            throw new Exception("VLM OCR 识别失败，返回结果: " + result);
        }

        return result;
    }

    /**
     * 根据文件扩展名获取 MIME 类型
     */
    private String getMimeType(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        String ext = lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "";
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    /**
     * 解析音频文件 - 使用语音识别
     */
    private String parseAudio(String filePath, Map<String, String> config) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "[音频文件不存在]";
            }

            byte[] audioData = Files.readAllBytes(path);
            String filename = path.getFileName().toString();

            // 使用 SiliconFlow ASR
            var globalConfig = modelConfigService.getConfig();
            String apiKey = globalConfig != null ? globalConfig.getSiliconFlowApiKey() : null;
            String endpoint = globalConfig != null ? globalConfig.getSiliconFlowEndpoint() : null;

            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("SiliconFlow API key not configured, audio transcription skipped");
                return "[音频转写失败: 未配置 SiliconFlow API Key]";
            }

            // 优先使用传入配置中的模型，否则使用全局默认
            String model = "paraformer-zh";
            if (config != null && config.containsKey("asr_model") && config.get("asr_model") != null) {
                model = config.get("asr_model");
                log.info("Using ASR model from knowledge base config: {}", model);
            }

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
    private String parseVideo(String filePath, Map<String, String> config) {
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
                    String result = parseAudio(audioPath, config);
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
