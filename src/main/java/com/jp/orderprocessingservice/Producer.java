package com.jp.orderprocessingservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Service
public class Producer
{
    private static final Logger logger = LoggerFactory.getLogger(Producer.class);
    private static final String TOPIC = "order-events";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate ;


    public void publishOrderData(String orderId, String username, String orderStatus, String paymentStatus, String type, String description) throws JsonProcessingException, ExecutionException, InterruptedException {

       OrderData orderData = new OrderData();
       orderData.setOrderId(orderId);
       orderData.setUsername(username);
       orderData.setOrderStatus(orderStatus);
       orderData.setPaymentStatus(paymentStatus);
       orderData.setEventType(type);
       orderData.setDescription(description);

       System.out.println("Trying to produce the kafka data.");
        logger.info("created the order message object: {}",orderData);
        // convert to JSON
        ObjectMapper objectMapper = new ObjectMapper();
        String datum =  objectMapper.writeValueAsString(orderData);
        logger.info("converted the order message object to JSON: "+datum);

        logger.info(String.format("#### -> Producing message -> %s", datum));
        this.kafkaTemplate.send(TOPIC, datum).get();
    }

}
