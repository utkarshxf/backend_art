package com.basic.JWTSecurity.artwork_server.model;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Year")
@Getter
@Setter
@Builder
public class Year {
    @Id
    private String id;
    private Integer year;
}
