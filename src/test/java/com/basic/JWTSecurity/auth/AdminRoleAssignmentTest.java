package com.basic.JWTSecurity.auth;

import com.basic.JWTSecurity.auth.model.Profile;
import com.basic.JWTSecurity.auth.repository.ProfileRepository;
import com.basic.JWTSecurity.auth.service.ProfileServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// ROLE_ADMIN comes only from the app.admin.usernames list: an empty list makes nobody an admin, and only the
// exact listed username gets the role.
class AdminRoleAssignmentTest {

    private ProfileServiceImpl serviceWithAdmins(String adminUsernames, String existingUsername) {
        ProfileRepository repository = mock(ProfileRepository.class);
        Profile profile = new Profile();
        profile.setUsername(existingUsername);
        profile.setPassword("x");
        when(repository.findByUsername(existingUsername)).thenReturn(Optional.of(profile));

        ProfileServiceImpl service = new ProfileServiceImpl();
        ReflectionTestUtils.setField(service, "profileRepository", repository);
        ReflectionTestUtils.setField(service, "adminUsernames", adminUsernames);
        return service;
    }

    private static List<String> authorities(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .collect(Collectors.toList());
    }

    @Test
    void emptyAdminListGrantsOnlyUser() {
        UserDetails details = serviceWithAdmins("", "utkarshxf").loadUserByUsername("utkarshxf");
        assertEquals(List.of("ROLE_USER"), authorities(details));
    }

    @Test
    void listedUsernameGetsAdminRole() {
        UserDetails details = serviceWithAdmins("someoneelse, utkarshxf ", "utkarshxf").loadUserByUsername("utkarshxf");
        assertEquals(List.of("ROLE_ADMIN", "ROLE_USER"), authorities(details));
    }

    @Test
    void unlistedUsernameStaysPlainUser() {
        UserDetails details = serviceWithAdmins("utkarshxf", "alice").loadUserByUsername("alice");
        assertEquals(List.of("ROLE_USER"), authorities(details));
    }
}
