package com.adlin.orin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Configuration
public class DifyConfig {

    @Value("${dify.api.timeout:60000}") // 增加默认超时时间到60秒
    private int difyApiTimeout;

    @Bean
    public RestTemplate difyRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(difyApiTimeout);
        factory.setReadTimeout(difyApiTimeout);

        RestTemplate restTemplate = new RestTemplate(factory);

        // 添加JSON消息转换器
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        restTemplate.getMessageConverters().add(jsonConverter);

        // 注册重试拦截器（429/5xx 指数退避）
        restTemplate.setInterceptors(java.util.List.of(
                (org.springframework.http.client.ClientHttpRequestInterceptor) (request, body, execution) -> {
                    int attempt = 0;
                    IOException lastEx = null;
                    while (attempt <= 3) {
                        try {
                            var response = execution.execute(request, body);
                            int status = response.getStatusCode().value();
                            if (status == 429 || status >= 500) {
                                if (response.getBody() != null) response.getBody().close();
                                if (attempt < 3) {
                                    long delay = (long) (2000 * Math.pow(2.0, attempt));
                                    java.util.logging.Logger.getLogger("DifyRetry")
                                            .info("Dify API " + status + " at attempt " + (attempt + 1) + ", retry in " + delay + "ms");
                                    Thread.sleep(delay);
                                    attempt++;
                                    continue;
                                }
                            }
                            return response;
                        } catch (IOException e) {
                            lastEx = e;
                            attempt++;
                            if (attempt > 3) throw e;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Retry interrupted", e);
                        }
                    }
                    throw lastEx != null ? lastEx : new IOException("Max retries exceeded");
                }
        ));

        return restTemplate;
    }
}