package com.jp.orderprocessingservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

@RestController
@RequestMapping("/api/v2")
public class MainRestController1 {

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

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    PaymentService paymentService;

    private static final Logger log = LoggerFactory.getLogger(MainRestController1.class);

    @GetMapping("/test")
    public ResponseEntity<?> testOrderService() {
        return ResponseEntity.ok("Order Processing Service running fine");
    }


    @PostMapping("/orders/new")
    public ResponseEntity<?> placeNewOrder(@RequestHeader("Authorization") String token, @RequestBody Order orderRequest, HttpServletRequest request,
                                           HttpServletResponse response){

        log.info("Received request to place new order : {}", orderRequest);

        if(customerService.validateToken(token)){

            //First check for cookies
            if(request.getCookies()==null){

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


                Mono<String> inventoryAsyncResponse = inventoryWebClient.post()
                        .uri("/inventory/block")
                        .header("Authorization", token)
                        .bodyValue(inventory)
                        .retrieve()
                        .onStatus(status -> status.value() == 401, responseTemp ->
                                Mono.error(new WebClientResponseException("Unauthorized", 401, "Unauthorized", null, null, null)))
                        .bodyToMono(String.class);

                String responseKey = String.valueOf(new Random().nextInt(100000)); // this is the key that we will return from this method
                log.info("Response Key: {}", responseKey);
                redisTemplate.opsForValue().set(responseKey,"Order-Service-Stage:OrderPlaced:"+orderId);

                inventoryAsyncResponse.subscribe((tempResponse) ->
                        {
                            log.info(tempResponse+" from the inventory service in async call");
                            if(tempResponse.startsWith("Insufficient")){
                                {
                                    //Inventory have insufficient quantities failed the order.
                                    log.info("Order can not be processed as having insufficient quantities for order id : "+newOrder.getOrderId());
                                    newOrder.setStatus("FAILED");
                                    orderRepo.save(newOrder);
                                    redisTemplate.opsForValue().set(responseKey,"Order-Service-Stage:QuantityCheckStage:InsufficientQuantity:"+orderId);
                                    //return ResponseEntity.ok("Failed to process order "+newOrder.getOrderId()+" because of insufficient quantity.");
                                }
                            } else {
                                log.info("Sufficient quantity of products available for order : {}, proceeding further with payment creation.",orderId);
                                redisTemplate.opsForValue().set(responseKey,"Order-Service-Stage:QuantityCheckStage:Available:"+orderId);
                                newOrder.setStatus("QuantityAvailable");
                                orderRepo.save(newOrder);
                                //Move further with payment creation and updates
                                paymentService.processPayment(token,orderId,newOrder, responseKey);
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
                            //redisTemplate.opsForValue().set(responseKey,"error "+error.getMessage());
                        });

                //Get the cookies and update the intrim response.
                log.info("Setting up the Cookie for the Front-end");
                Cookie cookieStage1 = new Cookie("order-service-stage-1", responseKey);
                cookieStage1.setMaxAge(300);
                log.info("Cookie set up successfully");
                response.addCookie(cookieStage1);
                return ResponseEntity.ok("Order processing in progress with orderId : "+orderId);

            }else {

                Optional<Cookie> tempCookie = Arrays.stream(request.getCookies()).filter(cookie -> cookie.getName().equals("order-service-stage-1")).findFirst();

                if(tempCookie.isPresent()){
                    String responseKey = tempCookie.get().getValue();
                    String updatedResponse = redisTemplate.opsForValue().get(responseKey).toString();
                    log.info("Updated response stored is : "+updatedResponse);
                    String orderId = updatedResponse.split(":")[3];

                    if(updatedResponse.startsWith("Order-Service-Stage:QuantityCheckStage:InsufficientQuantity")){
                        return ResponseEntity.ok("Order Id : "+orderId+" : Failed to process order because of insufficient quantity.");
                    } else if(updatedResponse.startsWith("Order-Service-Stage:QuantityCheckStage:Available")){
                        return ResponseEntity.ok("Order Id : "+orderId+" : Order Processing in progress. Quantity Check completed successfully. Proceeding with payment creation.");
                    } else if(updatedResponse.startsWith("Order-Service-Stage:PaymentStage:PaymentSuccessful")){
                        return ResponseEntity.ok("Order Id : "+orderId+" : Order Payment successful. Order ready to ship.");
                    } else if (updatedResponse.startsWith("Order-Service-Stage:PaymentStage:PaymentFailed")){
                        return ResponseEntity.ok("Order Id : "+orderId+" : Order Payment failed.");
                    }else if (updatedResponse.startsWith("Order-Service-Stage:PaymentStage:PaymentError")){
                        return ResponseEntity.ok("Order Id : "+orderId+" : Error processing order payment. Try to place new order");
                    }

                    return ResponseEntity.ok(updatedResponse);

                }else{
                    //False Alarm. Need to update it later to make them work.
                    return ResponseEntity.ok("Cookies not found. Enable cookies in browser");
                }
            }
        } else {
            return ResponseEntity.status(401).body("Unauthorized");
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
