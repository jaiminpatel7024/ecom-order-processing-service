package com.jp.orderprocessingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
public class OrderProcessingServiceApplication {

    public static void main(String[] args) {

        SpringApplication.run(OrderProcessingServiceApplication.class, args);

    }

}
