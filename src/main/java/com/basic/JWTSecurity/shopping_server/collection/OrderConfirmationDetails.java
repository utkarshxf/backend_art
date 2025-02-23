package com.basic.JWTSecurity.shopping_server.collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "OrderConfirmationDetails")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderConfirmationDetails {
    private String orderId;
    private Double totalAmount;
    private String estimatedDelivery;
    private Address shippingAddress;
}