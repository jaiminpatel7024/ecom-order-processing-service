package com.jp.orderprocessingservice;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table (name="inventory")
public class Inventory {

    @Id
    private Long productId;
    private Integer quantities;

    @Override
    public String toString() {
        return "Inventory{" +
                "productId=" + productId +
                ", quantities=" + quantities +
                '}';
    }
}
