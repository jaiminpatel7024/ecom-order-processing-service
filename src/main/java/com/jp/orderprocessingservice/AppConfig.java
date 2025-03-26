package com.jp.orderprocessingservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {


    @Bean(name = "inventory-service-web-client")
    public WebClient webClientInventoryService(WebClient.Builder webClientBuilder)
    {
        return webClientBuilder
                .baseUrl("http://localhost:8103/api/v1")
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

    @Bean(name = "payment-service-web-client")
    public WebClient webClientPaymentService(WebClient.Builder webClientBuilder)
    {
        return webClientBuilder
                .baseUrl("http://localhost:8105/api/v1")
                .filter(new LoggingWebClientFilter())
                .build();
    }



}
