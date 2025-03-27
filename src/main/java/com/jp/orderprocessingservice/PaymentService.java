package com.jp.orderprocessingservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
public class PaymentService {

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    @Qualifier("payment-service-web-client")
    WebClient paymentWebClient;

    @Autowired
    OrderRepository orderRepo;

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

        paymentResponse.subscribe((response) -> {
                log.info(response+" from the payment service in async call");
                if(response.startsWith("PaymentID")){
                    {
                        String paymentId = response.split(":")[1];
                        log.info("Payment processing completed, marking the order successful. Payment ID : "+paymentId);
                        newOrder.setPaymentStatus("PAID");
                        newOrder.setStatus("SHIPPED");
                        orderRepo.save(newOrder);
                        redisTemplate.opsForValue().set(responseKey,"Order-Service-Stage:PaymentStage:PaymentSuccessful:"+orderId);
                        //return ResponseEntity.ok("Payment processed successfully for order : "+orderId+". Order ready to be Shipped.");
                    }
                } else {
                    log.info("Payment processing failed for order id : {}.",orderId);
                    redisTemplate.opsForValue().set(responseKey,"Order-Service-Stage:PaymentStage:PaymentFailed:"+orderId);
                    newOrder.setStatus("FAILED");
                    orderRepo.save(newOrder);
                    //return ResponseEntity.ok("Failed to process order "+newOrder.getOrderId()+" because of payment failure.");
                }
                // MENU CREATION LOGIC TO BE IMPLEMENTED HERE
                // AND PUT THE RESPONSE IN REDIS
                        /*try {
                            producer.publishSubDatum(subscription.getSubid(), "subscription created payment created", "UPDATE", "UNPAID");
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }*/

                // redisTemplate.opsForValue().set(responseKey,"payresponse "+response);
            },
            error ->
            {
                log.info("error processing the response "+error.getMessage());
                redisTemplate.opsForValue().set(responseKey,"Order-Service-Stage:PaymentStage:PaymentError:"+orderId);
                newOrder.setStatus("FAILED");
                orderRepo.save(newOrder);
                //redisTemplate.opsForValue().set(responseKey,"error "+error.getMessage());
            });
    }

}
