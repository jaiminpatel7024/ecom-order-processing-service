package com.jp.orderprocessingservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
public class InventoryService
{
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);


    @Autowired
    @Qualifier("inventory-service-web-client")
    WebClient webClient;

    public boolean checkStockAvailibility(String token, Order orderObj){

        log.info("Validating stock availibility using inventory service.");

        //Create temp Inventory Object
        Inventory inventory = new Inventory();
        inventory.setProductId(orderObj.getProductId());
        inventory.setQuantities(orderObj.getQuantities());

        try {
            String response = webClient.get()
                    .uri("/inventory/block")
                    .header("Authorization", token)
                    .retrieve()
                    .onStatus(status -> status.value() == 401, responseTemp ->
                            Mono.error(new WebClientResponseException("Unauthorized", 401, "Unauthorized", null, null, null)))
                    .bodyToMono(String.class)
                    .block(); // Blocking call, ensure you use it only in non-reactive flows

            log.info("Response from auth service: {}", response);
            return response.equalsIgnoreCase("valid");

        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                log.info("Unauthorized request.");
                return false;
            }
            throw e;
        }
    }
}
