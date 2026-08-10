package com.focusos.config;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEventListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 6-A: 自定义 HttpClientBuilder — 使用 JDK HttpURLConnection
 * <p>
 * 解决 SpringRestClientBuilder + JettyClientHttpRequestFactory 的 readTimeout 不生效问题
 * （Jetty HttpClient 默认 30s idle timeout 导致 LLM 长文本生成超时）
 * <p>
 * HttpURLConnection.setReadTimeout 直接生效，无第三方依赖。
 */
public class SimpleHttpClientBuilder implements HttpClientBuilder {

    private Duration connectTimeout = Duration.ofSeconds(15);
    private Duration readTimeout = Duration.ofSeconds(120);

    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    public HttpClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public HttpClientBuilder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    @Override
    public dev.langchain4j.http.client.HttpClient build() {
        return new SimpleHttpClient(connectTimeout, readTimeout);
    }

    /**
     * 基于 JDK HttpURLConnection 的 HttpClient 实现
     */
    private static class SimpleHttpClient implements dev.langchain4j.http.client.HttpClient {

        private final int connectTimeoutMs;
        private final int readTimeoutMs;

        SimpleHttpClient(Duration connectTimeout, Duration readTimeout) {
            this.connectTimeoutMs = (int) connectTimeout.toMillis();
            this.readTimeoutMs = (int) readTimeout.toMillis();
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) URI.create(request.url()).toURL().openConnection();
                conn.setRequestMethod(request.method().name());
                conn.setConnectTimeout(connectTimeoutMs);
                conn.setReadTimeout(readTimeoutMs);
                conn.setDoInput(true);

                // 设置请求头
                if (request.headers() != null) {
                    for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
                        String headerName = entry.getKey();
                        for (String v : entry.getValue()) {
                            conn.setRequestProperty(headerName, v);
                        }
                    }
                }

                // 写请求体
                String body = request.body();
                if (body != null && !body.isEmpty()) {
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        os.flush();
                    }
                }

                // 触发请求
                int statusCode = conn.getResponseCode();
                if (statusCode < 0) {
                    throw new IOException("Invalid HTTP response code: " + statusCode);
                }

                // 读取响应（错误流或正常流）
                InputStream stream;
                if (statusCode >= 400) {
                    stream = conn.getErrorStream();
                    String errorBody = readAll(stream != null ? stream : InputStream.nullInputStream());
                    throw new RuntimeException("HTTP " + statusCode + ": " + errorBody);
                } else {
                    stream = conn.getInputStream();
                }

                String responseBody = readAll(stream);

                // 构造响应头
                Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
                conn.getHeaderFields().forEach((k, values) -> {
                    if (k != null && values != null && !values.isEmpty()) {
                        responseHeaders.put(k, new ArrayList<>(values));
                    }
                });

                return SuccessfulHttpResponse.builder()
                        .statusCode(statusCode)
                        .headers(responseHeaders)
                        .body(responseBody)
                        .build();
            } catch (IOException e) {
                throw new RuntimeException("HTTP request failed: " + request.url(), e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            // SSE 流式请求 — 回退到普通请求
            try {
                SuccessfulHttpResponse response = execute(request);
                if (listener != null && response != null) {
                    listener.onOpen(response);
                    // 将整个响应体作为单个 SSE 事件发送
                    listener.onEvent(new ServerSentEvent(null, response.body()));
                    listener.onClose();
                }
            } catch (Exception e) {
                if (listener != null) {
                    listener.onError(e);
                } else {
                    throw e instanceof RuntimeException re ? re : new RuntimeException(e);
                }
            }
        }

        private static String readAll(InputStream is) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
            byte[] buffer = new byte[8192];
            int n;
            while ((n = is.read(buffer)) != -1) {
                baos.write(buffer, 0, n);
            }
            return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
