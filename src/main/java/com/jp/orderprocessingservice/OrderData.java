package com.jp.orderprocessingservice;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderData {

    private String orderId;
    private String username;
    private String orderStatus;
    private String paymentStatus;
    private String eventType;
    private String description;

    @Override
    public String toString() {
        return "OrderData{" +
                "orderId='" + orderId + '\'' +
                ", username='" + username + '\'' +
                ", orderStatus='" + orderStatus + '\'' +
                ", paymentStatus='" + paymentStatus + '\'' +
                ", eventType='" + eventType + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
