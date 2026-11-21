package com.hungerbox.wings.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * Thin client for the Admin Chatbot used by "Summarize this page".
 * The request/response contract below is a PLACEHOLDER until the chatbot team publishes theirs:
 * POST {url}  {"role": "...", "pageContext": "<json>"}  ->  {"summary": "..."}.
 * When wings.chatbot.url is empty the call is skipped and the caller returns the page context only.
 */
@Component
public class AdminChatbotClient {
    private final String url;

    public AdminChatbotClient(@Value("${wings.chatbot.url:}") String url) { this.url = url; }

    @SuppressWarnings("unchecked")
    public Optional<String> summarize(String role, String pageContextJson) {
        if (url == null || url.isBlank()) return Optional.empty();
        Map<String, Object> res = RestClient.create(url).post()
                .body(Map.of("role", role, "pageContext", pageContextJson))
                .retrieve().body(Map.class);
        return res == null ? Optional.empty() : Optional.ofNullable((String) res.get("summary"));
    }
}
