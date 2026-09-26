package com.basic.JWTSecurity.artwork_server.model.projection;

import java.time.LocalDate;

public interface BucketCountProjection {
    LocalDate getPeriod();
    Long getCount();
}
