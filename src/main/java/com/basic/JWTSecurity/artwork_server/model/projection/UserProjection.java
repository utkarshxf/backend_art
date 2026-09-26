package com.basic.JWTSecurity.artwork_server.model.projection;

import java.time.LocalDate;

public interface UserProjection {


    String getId();

    String getName();

    default String getProfilePicture() {
        return null;
    }

    LocalDate getDob();

    String getGender();

    String getLanguage();

    String getCountryIso2();

}
