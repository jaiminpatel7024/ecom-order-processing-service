package com.jp.productcatalogservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {


    @Bean(name = "auth-service-validate")
    public WebClient webClientAuthService(WebClient.Builder webClientBuilder)
    {
        return webClientBuilder
                .baseUrl("http://localhost:8085/api/v1/validate")
                .filter(new LoggingWebClientFilter())
                .build();
    }

    @Bean(name = "customer-service-web-client")
    public WebClient webClientCustomerService(WebClient.Builder webClientBuilder)
    {
        return webClientBuilder
                .baseUrl("http://localhost:8101/api/v1")
                .filter(new LoggingWebClientFilter())
                .build();
    }

    @Bean(name = "plain-old-web-client")
    public WebClient webClientSubService(WebClient.Builder webClientBuilder)
    {
        return webClientBuilder
                .baseUrl("http://localhost:8101/api/v1")
                .filter(new LoggingWebClientFilter())
                .build();
    }



}
