package com.basic.JWTSecurity.auth;

import com.basic.JWTSecurity.JwtSecurityApplication;
import com.basic.JWTSecurity.artwork_server.service.AdminService;
import com.basic.JWTSecurity.artwork_server.service.ArtistService;
import com.basic.JWTSecurity.artwork_server.service.ArtworkService;
import com.basic.JWTSecurity.artwork_server.service.CommentService;
import com.basic.JWTSecurity.artwork_server.service.FavoritesService;
import com.basic.JWTSecurity.artwork_server.service.GalleryService;
import com.basic.JWTSecurity.artwork_server.service.GenreService;
import com.basic.JWTSecurity.artwork_server.service.UserService;
import com.basic.JWTSecurity.auth.config.SecurityConfig;
import com.basic.JWTSecurity.auth.security.AccessDeniedHandlerJwt;
import com.basic.JWTSecurity.auth.security.AuthEntryPointJwt;
import com.basic.JWTSecurity.auth.security.AuthTokenFilter;
import com.basic.JWTSecurity.auth.security.FirebaseTokenVerifier;
import com.basic.JWTSecurity.auth.security.JwtUtils;
import com.basic.JWTSecurity.auth.security.Ownership;
import com.basic.JWTSecurity.auth.service.ProfileServiceImpl;
import com.basic.JWTSecurity.chat.service.ChatService;
import com.basic.JWTSecurity.shopping_server.service.PersonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The /admin/** catalogue surface: nobody without a JWT, nobody with a plain USER JWT, only ROLE_ADMIN.
// Services are mocked, so no database is needed.
@WebMvcTest
@Import({SecurityConfig.class, JwtUtils.class, AuthEntryPointJwt.class, AccessDeniedHandlerJwt.class})
@TestPropertySource(properties = {
        "spring.app.jwtSecret=" + AdminSecurityTest.SECRET,
        "spring.app.jwtExpirationMs=2592000000"
})
class AdminSecurityTest {

    @SpringBootConfiguration
    @ComponentScan(basePackages = "com.basic.JWTSecurity", excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtSecurityApplication.class)})
    static class SliceConfiguration {
    }

    static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtMTIzNDU2Nzg=";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;

    @MockBean private ProfileServiceImpl profileService;
    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockBean private UserService userService;
    @MockBean private ArtworkService artworkService;
    @MockBean private ArtistService artistService;
    @MockBean private AdminService adminService;
    @MockBean private CommentService commentService;
    @MockBean private FavoritesService favoritesService;
    @MockBean private GalleryService galleryService;
    @MockBean private GenreService genreService;
    @MockBean private PersonService personService;
    @MockBean(name = "owner") private Ownership owner;
    @MockBean private ChatService chatService;
    @MockBean private com.basic.JWTSecurity.artwork_server.service.SearchService searchService;
    @MockBean private com.basic.JWTSecurity.account.AccountDeletionService accountDeletionService;
    @MockBean private com.basic.JWTSecurity.call.service.CallService callService;
    @MockBean private com.basic.JWTSecurity.artwork_server.service.DatabaseKeepAliveService keepAliveService;

    private String alice;   // plain USER
    private String curator; // USER + ADMIN

    @BeforeEach
    void setUp() {
        when(profileService.loadUserByUsername("alice")).thenReturn(user("alice", "USER"));
        when(profileService.loadUserByUsername("curator")).thenReturn(user("curator", "USER", "ADMIN"));
        alice = jwtUtils.generateTokenFromUsername(user("alice", "USER"));
        curator = jwtUtils.generateTokenFromUsername(user("curator", "USER", "ADMIN"));
    }

    private static UserDetails user(String name, String... roles) {
        return User.withUsername(name).password("x").roles(roles).build();
    }

    private MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void adminEndpointsRejectAnonymousCallers() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[]{
                json(post("/admin/artists"), "{\"name\":\"Hokusai\"}"),
                json(put("/admin/artists/a1"), "{\"name\":\"Hokusai\"}"),
                json(post("/admin/artists/a1/artworks"), "{\"title\":\"t\",\"genreId\":\"g\"}"),
                json(put("/admin/artworks/w1"), "{\"title\":\"t\"}"),
                get("/admin/artworks/w1"),
                json(put("/admin/artworks/w1/status"), "{\"status\":\"BLOCKED\"}"),
                delete("/admin/artworks/w1"),
                json(post("/admin/genres"), "{\"name\":\"Ukiyo-e\"}"),
        }) {
            mvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }
    }

    @Test
    void adminEndpointsRejectPlainUserTokens() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[]{
                json(post("/admin/artists"), "{\"name\":\"Hokusai\"}"),
                json(put("/admin/artists/a1"), "{\"name\":\"Hokusai\"}"),
                json(post("/admin/artists/a1/artworks"), "{\"title\":\"t\",\"genreId\":\"g\"}"),
                json(put("/admin/artworks/w1"), "{\"title\":\"t\"}"),
                get("/admin/artworks/w1"),
                json(put("/admin/artworks/w1/status"), "{\"status\":\"BLOCKED\"}"),
                delete("/admin/artworks/w1"),
                json(post("/admin/genres"), "{\"name\":\"Ukiyo-e\"}"),
        }) {
            mvc.perform(as(alice, request))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Forbidden"));
        }
    }

    @Test
    void adminEndpointsAcceptAdminTokens() throws Exception {
        when(adminService.createArtist(org.mockito.ArgumentMatchers.any())).thenReturn("a1");
        when(adminService.createArtwork(org.mockito.ArgumentMatchers.eq("a1"), org.mockito.ArgumentMatchers.any())).thenReturn("w1");
        when(adminService.createGenre("Ukiyo-e")).thenReturn(java.util.Map.of("id", "g1", "key", "g1", "name", "Ukiyo-e"));
        when(adminService.getArtworkForAudit("w1")).thenReturn(java.util.Map.of("id", "w1", "title", "Wave"));

        mvc.perform(as(curator, json(post("/admin/artists"), "{\"name\":\"Hokusai\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("a1"));
        mvc.perform(as(curator, json(put("/admin/artists/a1"), "{\"name\":\"Hokusai\"}")))
                .andExpect(status().isOk());
        mvc.perform(as(curator, json(post("/admin/artists/a1/artworks"), "{\"title\":\"t\",\"genreId\":\"g\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("w1"));
        mvc.perform(as(curator, json(put("/admin/artworks/w1"), "{\"title\":\"t\"}")))
                .andExpect(status().isOk());
        mvc.perform(as(curator, get("/admin/artworks/w1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("w1"));
        mvc.perform(as(curator, json(put("/admin/artworks/w1/status"), "{\"status\":\"BLOCKED\"}")))
                .andExpect(status().isOk());
        mvc.perform(as(curator, delete("/admin/artworks/w1")))
                .andExpect(status().isOk());
        mvc.perform(as(curator, json(post("/admin/genres"), "{\"name\":\"Ukiyo-e\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("g1"));
    }

    // A USER token still cannot mint itself an admin through the public self-service API either:
    // POST /artwork/artist/{otherArtist} stays ownership-guarded
    @Test
    void plainUserStillCannotWriteUnderAnotherArtist() throws Exception {
        mvc.perform(as(alice, json(post("/artwork/artist/bob"), "{\"title\":\"t\",\"genreId\":\"g\"}")))
                .andExpect(status().isForbidden());
    }
}
