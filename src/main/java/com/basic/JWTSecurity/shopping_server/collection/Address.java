package com.basic.JWTSecurity.shopping_server.collection;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.neo4j.core.schema.Id;

import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "address")
public class Address {
    @Id
    private String id;
    private String name;
    private String streetAddress;
    private String apartment;
    private String city;
    private String state;
    private String zipCode;
    private String phone;
    private Boolean isDefault;
}