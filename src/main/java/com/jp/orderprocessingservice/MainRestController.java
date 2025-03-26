package com.jp.orderprocessingservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@RestController
@RequestMapping("/api/v1")
public class MainRestController {

    @Autowired
    CustomerService customerService;

    @Autowired
    OrderRepository orderRepo;

    @Autowired
    @Qualifier("inventory-service-web-client")
    WebClient inventoryWebClient;

    @Autowired
    @Qualifier("payment-service-web-client")
    WebClient paymentWebClient;

    private static final Logger log = LoggerFactory.getLogger(MainRestController.class);

    @GetMapping("/test")
    public ResponseEntity<?> testOrderService() {
        return ResponseEntity.ok("Order Processing Service running fine");
    }


    @PostMapping("/orders/new")
    public ResponseEntity<?> placeNewOrder(@RequestHeader("Authorization") String token, @RequestBody Order orderRequest){

        log.info("Received request to place new order : {}", orderRequest);

        if(customerService.validateToken(token)){

            //Fit in SYNC logic to place the order end to end first
            Order newOrder = new Order();
            newOrder.setOrderId(String.valueOf(new Random().nextInt(100000)));
            newOrder.setOrderTime(LocalDateTime.now());
            newOrder.setOrderTotal(orderRequest.getOrderTotal());
            newOrder.setPaymentStatus("PENDING");
            newOrder.setProductId(orderRequest.getProductId());
            newOrder.setQuantities(orderRequest.getQuantities());
            newOrder.setStatus("PROCESSING");
            newOrder.setUsername(orderRequest.getUsername());

            orderRepo.save(newOrder);
            String orderId = newOrder.getOrderId();
            log.info("New order is placed and is pending with order id : "+orderId);


            //If token is valid, check with Inventory service for available stock
            log.info("Validating stock availibility using inventory service.");

            //Create temp Inventory Object
            Inventory inventory = new Inventory();
            inventory.setProductId(newOrder.getProductId());
            inventory.setQuantities(newOrder.getQuantities());

            try {
                String inventoryResponse = inventoryWebClient.get()
                        .uri(uriBuilder ->  uriBuilder.path("/inventory/block")
                                .build(inventory))
                        .header("Authorization", token)
                        .retrieve()
                        .onStatus(status -> status.value() == 401, responseTemp ->
                                Mono.error(new WebClientResponseException("Unauthorized", 401, "Unauthorized", null, null, null)))
                        .bodyToMono(String.class)
                        .block(); // Blocking call, ensure you use it only in non-reactive flows

                log.info("Response from inventory service: {}", inventoryResponse);

                if(inventoryResponse.startsWith("Insufficient")){
                    //Inventory have insufficient quantities failed the order.
                    log.info("Order can not be processed as having insufficient quantities for order id : "+newOrder.getOrderId());
                    newOrder.setStatus("FAILED");
                    orderRepo.save(newOrder);
                    return ResponseEntity.ok("Failed to process order "+newOrder.getOrderId()+" because of insufficient quantity.");
                }else{
                    //If sufficient than initiate a transaction with payment service
                    String paymentResponse = paymentWebClient.get()
                            .uri(uriBuilder ->  uriBuilder.path("/payment/create/"+orderId).build())
                            .header("Authorization", token)
                            .retrieve()
                            .onStatus(status -> status.value() == 401, responseTemp1 ->
                                    Mono.error(new WebClientResponseException("Unauthorized", 401, "Unauthorized", null, null, null)))
                            .bodyToMono(String.class)
                            .block(); // Blocking call, ensure you use it only in non-reactive flows

                    log.info("Response from payment service: {}", paymentResponse);


                    if(paymentResponse.startsWith("PaymentID")){
                        log.info("Payment processing completed, marking the order successful.");
                        newOrder.setPaymentStatus("PAID");
                        newOrder.setStatus("SHIPPED");
                        orderRepo.save(newOrder);
                        return ResponseEntity.ok("Payment processed successfully for order : "+orderId+". Order ready to be Shipped.");
                    }else{
                        log.info("Payment processsing failed.");
                        newOrder.setStatus("FAILED");
                        orderRepo.save(newOrder);
                        return ResponseEntity.ok("Failed to process order "+newOrder.getOrderId()+" because of payment failure.");
                    }
                }
            } catch (WebClientResponseException e) {
                if (e.getStatusCode().value() == 401) {
                    log.info("Unauthorized request.");
                    log.info("Order processing failed for order id : "+newOrder.getOrderId());
                    newOrder.setStatus("FAILED");
                    orderRepo.save(newOrder);
                    return ResponseEntity.status(401).build();
                }
                throw e;
            }
        } else {
            return ResponseEntity.status(401).build();
        }

    }

    @GetMapping("/orders/view/{orderId}")
    public ResponseEntity<?> getOrderDetails(@RequestHeader("Authorization") String token, @PathVariable("orderId") String orderId){

        log.info("Received request to view order with order id : {} ",orderId);

        if(customerService.validateToken(token)){
            Optional<Order> orderObj = orderRepo.findById(orderId);

            if(orderObj.isPresent()){
                return ResponseEntity.ok(orderObj);
            }else{
                return  ResponseEntity.ok("No order found with given id : "+orderId);
            }
        } else {
            return ResponseEntity.status(401).body("Unauthorized");
        }
    }

    @GetMapping("/orders/viewAll")
    public ResponseEntity<?> getAllOrders(@RequestHeader("Authorization") String token){

        log.info("Received request to view all orders");

        if(customerService.validateToken(token)){
            return ResponseEntity.ok(orderRepo.findAll());
        } else {
            return ResponseEntity.status(401).body("Unauthorized");
        }
    }

}
