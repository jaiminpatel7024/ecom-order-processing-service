package com.jp.orderprocessingservice;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
@Entity
@Table (name = "orders")
public class Order {

    @Id
    private String orderId;
    private Long productId;
    private Integer quantities;
    private Long orderTotal;
    private String username;
    private LocalDateTime orderTime;
    private String status;
    private String paymentStatus;

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", productId='" + productId + '\'' +
                ", quantities=" + quantities +
                ", orderTotal=" + orderTotal +
                ", username='" + username + '\'' +
                ", orderTime=" + orderTime +
                ", status='" + status + '\'' +
                ", paymentStatus='" + paymentStatus + '\'' +
                '}';
    }
}
