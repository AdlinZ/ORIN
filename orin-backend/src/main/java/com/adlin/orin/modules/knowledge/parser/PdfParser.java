package com.adlin.orin.modules.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * PDF 解析器
 * 使用 Apache PDFBox 提取 PDF 文本
 */
@Slf4j
@Component
public class PdfParser implements DocumentParser {

    private static final Set<String> SUPPORTED_TYPES = Set.of("pdf");

    @Override
    public Set<String> supportedMediaTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public ParsingResult parse(String inputPath, String outputPath, Map<String, String> config) {
        long startTime = System.currentTimeMillis();
        PDDocument document = null;

        try {
            Path input = Path.of(inputPath);
            Path output = Path.of(outputPath);

            if (!Files.exists(input)) {
                return ParsingResult.failure("Input file not found: " + inputPath);
            }

            // 加载 PDF 文档
            document = Loader.loadPDF(input.toFile());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            // 提取所有文本
            String text = stripper.getText(document);

            if (text == null || text.trim().isEmpty()) {
                return ParsingResult.failure("No text content found in PDF", System.currentTimeMillis() - startTime);
            }

            // 写入解析结果
            Files.createDirectories(output.getParent());
            Files.writeString(output, text);

            int pageCount = document.getNumberOfPages();
            long processingTime = System.currentTimeMillis() - startTime;

            log.info("PDF parsed successfully: {} -> {}, pages: {}, chars: {}",
                    inputPath, outputPath, pageCount, text.length());

            return ParsingResult.builder()
                    .success(true)
                    .text(text)
                    .processingTimeMs(processingTime)
                    .metadata(Map.of(
                            "pageCount", pageCount,
                            "charCount", text.length()
                    ))
                    .build();

        } catch (IOException e) {
            log.error("Failed to parse PDF: {}", inputPath, e);
            return ParsingResult.failure("Failed to parse PDF: " + e.getMessage(),
                    System.currentTimeMillis() - startTime);
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    log.warn("Failed to close PDF document", e);
                }
            }
        }
    }

    @Override
    public boolean supports(String mediaType) {
        if (mediaType == null) return false;
        String type = mediaType.toLowerCase();
        return SUPPORTED_TYPES.contains(type) ||
               type.equals("application/pdf");
    }
}
