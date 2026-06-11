package com.adlin.orin.modules.multimodal.service;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.service.RouterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * OcrService.recognize 单测。
 * <p>验证：
 * <ul>
 *   <li>RouterService 命中 provider → chatCompletion 结果被提取</li>
 *   <li>RouterService 返回空 → 返回 "[OCR Error] No healthy provider ..."</li>
 *   <li>chatCompletion 抛异常 → 返回 "[OCR Error] ..."</li>
 *   <li>请求构造：多模态 parts 包含 image_url + text 部件</li>
 *   <li>响应含 [NO_TEXT_DETECTED] → 返回空串</li>
 *   <li>参数校验：imageUrl / model 为空</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock
    private RouterService routerService;
    @Mock
    private ProviderAdapter provider;

    private OcrService newService() {
        return new OcrService(routerService);
    }

    @Test
    void recognize_nullImageUrl_returnsError() {
        OcrService svc = newService();
        String result = svc.recognize(null, "Qwen/Qwen2-VL-7B-Instruct");
        assertThat(result).startsWith("[OCR Error]").contains("imageUrl");
    }

    @Test
    void recognize_blankImageUrl_returnsError() {
        OcrService svc = newService();
        String result = svc.recognize("", "Qwen/Qwen2-VL-7B-Instruct");
        assertThat(result).startsWith("[OCR Error]").contains("imageUrl");
    }

    @Test
    void recognize_nullModel_returnsError() {
        OcrService svc = newService();
        String result = svc.recognize("data:image/png;base64,AAAA", null);
        assertThat(result).startsWith("[OCR Error]").contains("model");
    }

    @Test
    void recognize_noHealthyProvider_returnsError() {
        when(routerService.selectProviderByModel(eq("Qwen/Qwen2-VL-7B-Instruct"), any()))
                .thenReturn(Optional.empty());

        OcrService svc = newService();
        String result = svc.recognize("data:image/png;base64,AAAA", "Qwen/Qwen2-VL-7B-Instruct");

        assertThat(result)
                .startsWith("[OCR Error]")
                .contains("No healthy provider")
                .contains("Qwen/Qwen2-VL-7B-Instruct");
    }

    @Test
    void recognize_providerReturnsText_extracted() {
        when(provider.getProviderName()).thenReturn("siliconflow-vlm");
        when(provider.chatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(Mono.just(ChatCompletionResponse.builder()
                        .id("chatcmpl-1")
                        .object("chat.completion")
                        .created(0L)
                        .model("Qwen/Qwen2-VL-7B-Instruct")
                        .provider("siliconflow")
                        .choices(List.of(ChatCompletionResponse.Choice.builder()
                                .index(0)
                                .message(ChatCompletionRequest.Message.builder()
                                        .role("assistant")
                                        .content("  hello world  ")
                                        .build())
                                .finishReason("stop")
                                .build()))
                        .build()));
        when(routerService.selectProviderByModel(eq("Qwen/Qwen2-VL-7B-Instruct"), any()))
                .thenReturn(Optional.of(provider));

        OcrService svc = newService();
        String result = svc.recognize("data:image/png;base64,AAAA", "Qwen/Qwen2-VL-7B-Instruct");

        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void recognize_providerMarksNoText_returnsEmptyString() {
        when(provider.chatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(Mono.just(ChatCompletionResponse.builder()
                        .id("chatcmpl-1")
                        .object("chat.completion")
                        .created(0L)
                        .model("Qwen/Qwen2-VL-7B-Instruct")
                        .provider("siliconflow")
                        .choices(List.of(ChatCompletionResponse.Choice.builder()
                                .index(0)
                                .message(ChatCompletionRequest.Message.builder()
                                        .role("assistant")
                                        .content("[NO_TEXT_DETECTED]")
                                        .build())
                                .finishReason("stop")
                                .build()))
                        .build()));
        when(routerService.selectProviderByModel(eq("Qwen/Qwen2-VL-7B-Instruct"), any()))
                .thenReturn(Optional.of(provider));

        OcrService svc = newService();
        String result = svc.recognize("data:image/png;base64,AAAA", "Qwen/Qwen2-VL-7B-Instruct");

        assertThat(result).isEmpty();
    }

    @Test
    void recognize_providerThrows_returnsError() {
        when(provider.getProviderName()).thenReturn("siliconflow-vlm");
        when(provider.chatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("upstream boom")));
        when(routerService.selectProviderByModel(eq("Qwen/Qwen2-VL-7B-Instruct"), any()))
                .thenReturn(Optional.of(provider));

        OcrService svc = newService();
        String result = svc.recognize("data:image/png;base64,AAAA", "Qwen/Qwen2-VL-7B-Instruct");

        assertThat(result)
                .startsWith("[OCR Error]")
                .contains("upstream boom");
    }

    @Test
    void recognize_buildsMultimodalRequest() {
        ArgumentCaptor<ChatCompletionRequest> captor = ArgumentCaptor.forClass(ChatCompletionRequest.class);
        when(provider.getProviderName()).thenReturn("siliconflow-vlm");
        when(provider.chatCompletion(captor.capture()))
                .thenReturn(Mono.just(ChatCompletionResponse.builder()
                        .id("x").object("chat.completion").created(0L).model("m").provider("p")
                        .choices(List.of(ChatCompletionResponse.Choice.builder()
                                .index(0)
                                .message(ChatCompletionRequest.Message.builder()
                                        .role("assistant").content("ok").build())
                                .finishReason("stop").build()))
                        .build()));
        when(routerService.selectProviderByModel(eq("Qwen/Qwen2-VL-7B-Instruct"), any()))
                .thenReturn(Optional.of(provider));

        OcrService svc = newService();
        svc.recognize("data:image/png;base64,HI", "Qwen/Qwen2-VL-7B-Instruct");

        ChatCompletionRequest req = captor.getValue();
        assertThat(req.getModel()).isEqualTo("Qwen/Qwen2-VL-7B-Instruct");
        assertThat(req.getMessages()).hasSize(2);
        // system 消息走 content 字符串
        assertThat(req.getMessages().get(0).getRole()).isEqualTo("system");
        assertThat(req.getMessages().get(0).getContent()).contains("OCR service");
        // user 消息走 parts 多模态
        assertThat(req.getMessages().get(1).getRole()).isEqualTo("user");
        assertThat(req.getMessages().get(1).getParts()).hasSize(2);
        assertThat(req.getMessages().get(1).getParts().get(0).getType()).isEqualTo("text");
        assertThat(req.getMessages().get(1).getParts().get(1).getType()).isEqualTo("image_url");
        assertThat(req.getMessages().get(1).getParts().get(1).getImageUrl().getUrl())
                .isEqualTo("data:image/png;base64,HI");
        assertThat(req.getMessages().get(1).getParts().get(1).getImageUrl().getDetail()).isEqualTo("auto");
    }
}
