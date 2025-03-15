package com.basic.JWTSecurity.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.basic.JWTSecurity.shopping_server.collection.*;

import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "user")
public class Profile {
    @Id
    private String id;
    private String username;
    private String phone;
    private String password;
    private List<Address> userAddress = null;
    private List<CartItem> cardItems = null;
    private List<OrderItem> orderItems = null;
}