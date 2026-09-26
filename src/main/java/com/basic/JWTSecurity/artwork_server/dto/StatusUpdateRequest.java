package com.basic.JWTSecurity.artwork_server.dto;

import com.basic.JWTSecurity.artwork_server.model.Status;
import lombok.Data;

@Data
public class StatusUpdateRequest {
    private Status status;
}
