package com.basic.JWTSecurity.shopping_server.collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "OrderItem")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderItem {
    private String id;
    private String title;
    private String artist;
    private Double price;
    private Integer quantity;
    private String imageUrl;
}