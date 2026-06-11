package com.adlin.orin.modules.multimodal.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 锁定 OcrService 三个未实现的云方法契约：直接抛 UnsupportedOperationException，
 * 调用方可明确区分"未实现"与"运行时错误"，避免历史返回的 "[OCR Error] ..." 魔法字符串被误当成成功结果。
 *
 * 与 AsrService 三个云方法同构（commit 5a3656fa）。
 */
class OcrServiceTest {

    private final OcrService ocrService = new OcrService();

    @Test
    @DisplayName("OCR 未实现契约: 阿里云 OCR 调用抛 UnsupportedOperationException")
    void ocrWithAliCloud_throwsUnsupportedOperationException() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> ocrService.ocrWithAliCloud("http://example.com/img.png", "ak", "sk")
        );
        assertEquals("AliCloud OCR not implemented", ex.getMessage());
    }

    @Test
    @DisplayName("OCR 未实现契约: 腾讯云 OCR 调用抛 UnsupportedOperationException")
    void ocrWithTencentCloud_throwsUnsupportedOperationException() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> ocrService.ocrWithTencentCloud("http://example.com/img.png", "sid", "skey")
        );
        assertEquals("TencentCloud OCR not implemented", ex.getMessage());
    }

    @Test
    @DisplayName("OCR 未实现契约: 百度 OCR 调用抛 UnsupportedOperationException")
    void ocrWithBaidu_throwsUnsupportedOperationException() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> ocrService.ocrWithBaidu("http://example.com/img.png", "apikey", "skey")
        );
        assertEquals("Baidu OCR not implemented", ex.getMessage());
    }
}
