package com.archana.jobs.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@Configuration
@EnableAsync
public class AnthropicConfig {

    @Value("${anthropic.api-key:}")
    private String apiKey;

    @Bean
    public AnthropicClient anthropicClient() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ANTHROPIC_API_KEY not set — Claude-backed features (resume parsing, job scoring) will fail until configured.");
            return AnthropicOkHttpClient.builder().apiKey("").build();
        }
        return AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }
}
