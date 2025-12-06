package com.smartroute.smartroute1.basetest;

import okhttp3.mockwebserver.MockWebServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.URI;

@TestConfiguration
public class ApiMockConfig {

    @Bean(destroyMethod = "shutdown")
    public MockWebServer mockApiServer() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();
        return server;
    }

    @Bean
    public MockWebServerProvider mockWebServerProvider() throws IOException {
        return new MockWebServerProvider(mockApiServer());
    }

    @Bean
    @Primary
    public WebClient stravaWebClient(MockWebServerProvider provider) {
        return WebClient.builder()
                .baseUrl(provider.get().url("/").toString())
                .filter(rewriteToMock(provider))
                .build();
    }

    private ExchangeFilterFunction rewriteToMock(MockWebServerProvider provider) {
        return (request, next) -> {
            URI original = request.url();
            String path = original.getRawPath();
            String query = original.getRawQuery();
            String target = path + (query != null && !query.isEmpty() ? "?" + query : "");
            MockWebServer server = provider.get();
            URI rewritten = server.url(target).uri();

            ClientRequest newRequest = ClientRequest.from(request)
                    .url(rewritten)
                    .build();

            return next.exchange(newRequest);
        };
    }

    public static class MockWebServerProvider {
        private volatile MockWebServer server;

        public MockWebServerProvider(MockWebServer server) {
            this.server = server;
        }

        public synchronized MockWebServer resetAndGet() throws IOException {
            try {
                server.shutdown();
            } catch (Exception ignored) {
            }
            server = new MockWebServer();
            server.start();
            return server;
        }

        public MockWebServer get() {
            return server;
        }
    }
}
