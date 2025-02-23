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
@Document(collection = "PaymentMethod")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentMethod {
    private String type;
    private String lastFourDigits;
    private String expiryDate;
}
