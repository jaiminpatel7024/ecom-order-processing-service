package com.jp.orderprocessingservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.concurrent.ExecutionException;

@Service
public class PaymentService {

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    @Qualifier("payment-service-web-client")
    WebClient paymentWebClient;

    @Autowired
    OrderRepository orderRepo;

    @Autowired
    Producer eventProducer;

    private static final Logger log = LoggerFactory.getLogger(MainRestControllerAsync.class);


    public void processPayment(String token, String orderId, Order newOrder, String responseKey){

        //If sufficient than initiate a transaction with payment service
        Mono<String> paymentResponse = paymentWebClient.post()
                .uri(uriBuilder ->  uriBuilder.path("/payment/create/"+orderId).build())
                .header("Authorization", token)
                .retrieve()
                .onStatus(status -> status.value() == 401, responseTemp1 ->
                        Mono.error(new WebClientResponseException("Unauthorized", 401, "Unauthorized", null, null, null)))
                .bodyToMono(String.class);

        newOrder.setStatus("PAYMENT_PROCESSING");
        newOrder.setPaymentStatus("PROCESSING");
        orderRepo.save(newOrder);

        try {
            eventProducer.publishOrderData(orderId,newOrder.getUsername(), newOrder.getStatus(),newOrder.getPaymentStatus(),"UPDATE","Payment Processing In Progress.");
        } catch (JsonProcessingException | ExecutionException | InterruptedException e) {
            log.error("Error updating the events in Kafka Producer", e);
        }

        paymentResponse.subscribe((response) -> {
                log.info(response+" from the payment service in async call");
                if(response.startsWith("PaymentID")){
                    {
                        String paymentId = response.split(":")[1];
                        log.info("Payment processing completed, marking the order successful. Payment ID : "+paymentId);
                        newOrder.setPaymentStatus("PAID");
                        newOrder.setStatus("SHIPPED");
                        orderRepo.save(newOrder);
                        try {
                            eventProducer.publishOrderData(orderId,newOrder.getUsername(), newOrder.getStatus(),newOrder.getPaymentStatus(),"COMPLETE","Payment Successful. Order Complete.");
                        } catch (JsonProcessingException | ExecutionException | InterruptedException e) {
                            log.error("Error updating the events in Kafka Producer", e);
                        }
                        redisTemplate.opsForValue().set(responseKey,"Order-Service-Stage:PaymentStage:PaymentSuccessful:"+orderId);
                    }
                } else {
                    log.info("Payment processing failed for order id : {}.",orderId);
                    redisTemplate.opsForValue().set(responseKey,"Order-Service-Stage:PaymentStage:PaymentFailed:"+orderId);
                    newOrder.setStatus("FAILED");
                    newOrder.setPaymentStatus("PAYMENT_FAILED");
                    orderRepo.save(newOrder);
                    try {
                        eventProducer.publishOrderData(orderId,newOrder.getUsername(), newOrder.getStatus(),newOrder.getPaymentStatus(),"COMPLETE","Payment Failed. Order Complete.");
                    } catch (JsonProcessingException | ExecutionException | InterruptedException e) {
                        log.error("Error updating the events in Kafka Producer", e);
                    }
                }
            },
            error ->
            {
                log.info("error processing the response "+error.getMessage());
                redisTemplate.opsForValue().set(responseKey,"Order-Service-Stage:PaymentStage:PaymentError:"+orderId);
                newOrder.setStatus("FAILED");
                newOrder.setPaymentStatus("PAYMENT_FAILED");
                orderRepo.save(newOrder);
                try {
                    eventProducer.publishOrderData(orderId,newOrder.getUsername(), newOrder.getStatus(),newOrder.getPaymentStatus(),"ERROR","Error processing payment.");
                } catch (JsonProcessingException | ExecutionException | InterruptedException e) {
                    log.error("Error updating the events in Kafka Producer", e);
                }
            });
    }

}
