package com.basic.JWTSecurity.shopping_server.collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.neo4j.core.schema.Id;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "CartItem")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartItem {
    @Id
    private String id;
    private String title;
    private String artist;
    private Double price;
    private String imageUrl;
    private Integer quantity;
}