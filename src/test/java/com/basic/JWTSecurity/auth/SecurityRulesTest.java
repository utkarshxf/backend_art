package com.basic.JWTSecurity.auth;

import com.basic.JWTSecurity.JwtSecurityApplication;
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
import com.basic.JWTSecurity.auth.security.FirebaseTokenVerifier;
import com.basic.JWTSecurity.auth.security.JwtUtils;
import com.basic.JWTSecurity.auth.security.Ownership;
import com.basic.JWTSecurity.auth.service.ProfileServiceImpl;
import com.basic.JWTSecurity.shopping_server.service.PersonService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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

import java.util.Date;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Which endpoints need a JWT, and that a JWT only lets its user change their own data. Services are mocked, so no
// database is needed.
@WebMvcTest
@Import({SecurityConfig.class, JwtUtils.class, AuthEntryPointJwt.class, AccessDeniedHandlerJwt.class})
@TestPropertySource(properties = {
        "spring.app.jwtSecret=" + SecurityRulesTest.SECRET,
        "spring.app.jwtExpirationMs=2592000000"
})
class SecurityRulesTest {

    // Stands in for JwtSecurityApplication, whose @EnableNeo4jAuditing needs a database and whose scan of
    // org.springdoc needs auto-configuration this slice does not load
    @SpringBootConfiguration
    @ComponentScan(basePackages = "com.basic.JWTSecurity", excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtSecurityApplication.class)})
    static class SliceConfiguration {
    }

    static final String SECRET ="dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtMTIzNDU2Nzg=";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;

    @MockBean private ProfileServiceImpl profileService;
    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockBean private UserService userService;
    @MockBean private ArtworkService artworkService;
    @MockBean private ArtistService artistService;
    @MockBean private CommentService commentService;
    @MockBean private FavoritesService favoritesService;
    @MockBean private GalleryService galleryService;
    @MockBean private GenreService genreService;
    @MockBean private PersonService personService;
    @MockBean(name = "owner") private Ownership owner;

    private String alice;

    @BeforeEach
    void setUp() {
        when(profileService.loadUserByUsername("alice")).thenReturn(user("alice"));
        when(profileService.validateUsername(anyString())).thenReturn(Map.of("isValid", true));
        when(firebaseTokenVerifier.verify(anyString()))
                .thenReturn(new FirebaseTokenVerifier.VerifiedIdentity("uid", "+910000000000", null));
        when(owner.ofFavorites("alices-favorites", "alice")).thenReturn(true);
        when(owner.ofComment("alices-comment", "alice")).thenReturn(true);
        when(owner.ofArtwork("alices-artwork", "alice")).thenReturn(true);
        alice = jwtUtils.generateTokenFromUsername(user("alice"));
    }

    private static UserDetails user(String name) {
        return User.withUsername(name).password("x").roles("USER").build();
    }

    private MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ---- public: what the app calls before it has a JWT

    @Test
    void loggedOutAppCanStillLogInAndSignUp() throws Exception {
        mvc.perform(get("/isValidUsername").param("username", "newname")).andExpect(status().isOk());
        mvc.perform(get("/users/isValidUsername").param("username", "newname")).andExpect(status().isOk());
        mvc.perform(json(post("/auth/firebase"), "{\"firebaseIdToken\":\"t\"}")).andExpect(status().isOk());
        mvc.perform(json(post("/auth/phone"), "{\"firebaseIdToken\":\"t\"}")).andExpect(status().isOk());
        // controller answers (bad credentials = 404), not the security layer
        mvc.perform(json(post("/login"), "{\"username\":\"nobody\",\"password\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(json(post("/check"), "{\"token\":\"garbage\"}")).andExpect(status().isOk());
    }

    @Test
    void publicEndpointsIgnoreAStaleToken() throws Exception {
        mvc.perform(as(expiredToken("alice"), get("/isValidUsername").param("username", "newname")))
                .andExpect(status().isOk());
    }

    // ---- everything else needs a valid JWT

    @Test
    void noTokenIs401Json() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[]{
                get("/users/getUserByUserId/alice"),
                get("/artwork/recommend").param("userId", "alice").param("skip", "0").param("limit", "4"),
                get("/artwork/today-biggest-hit"),
                get("/artist/search").param("query", "a").param("responseSize", "5"),
                get("/comments/artwork/a1"),
                get("/favorites/user/alice"),
                get("/genres/all"),
                get("/shopping").param("name", "a"),
                get("/admin/backup"),
                put("/artwork/user/like/a1/alice"),
                get("/no/such/endpoint"),
        }) {
            mvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }
    }

    @Test
    void expiredOrForgedTokenIs401() throws Exception {
        mvc.perform(as(expiredToken("alice"), get("/users/getUserByUserId/alice"))).andExpect(status().isUnauthorized());
        mvc.perform(as(alice + "x", get("/users/getUserByUserId/alice"))).andExpect(status().isUnauthorized());
        String otherKey = Jwts.builder().subject("alice").expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode("b3RoZXItc2VjcmV0LW90aGVyLXNlY3JldC1vdGhlci1zZWNyZXQ=")))
                .compact();
        mvc.perform(as(otherKey, get("/users/getUserByUserId/alice"))).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenReadsAnything() throws Exception {
        mvc.perform(as(alice, get("/users/getUserByUserId/bob"))).andExpect(status().isOk());
        mvc.perform(as(alice, get("/artwork/recommend").param("userId", "alice").param("skip", "0").param("limit", "4")))
                .andExpect(status().isOk());
        mvc.perform(as(alice, get("/favorites/user/bob"))).andExpect(status().isOk());
        mvc.perform(as(alice, get("/comments/artwork/a1"))).andExpect(status().isOk());
    }

    @Test
    void tokenIsLongLived() {
        Date expiry = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).build()
                .parseSignedClaims(alice).getPayload().getExpiration();
        long days = (expiry.getTime() - System.currentTimeMillis()) / 86_400_000L;
        org.junit.jupiter.api.Assertions.assertTrue(days >= 29, "token lifetime in days: " + days);
    }

    // ---- writes: only for yourself

    @Test
    void writesForYourselfAreAllowed() throws Exception {
        mvc.perform(as(alice, put("/artwork/user/like/a1/alice"))).andExpect(status().isOk());
        mvc.perform(as(alice, put("/artwork/user/unlike/a1/alice"))).andExpect(status().isOk());
        mvc.perform(as(alice, put("/artwork/user/dislike/a1/alice"))).andExpect(status().isOk());
        mvc.perform(as(alice, post("/artwork/a1/view/alice"))).andExpect(status().isCreated());
        mvc.perform(as(alice, json(post("/comments/artwork/a1/user/alice"), "{\"text\":\"hi\"}"))).andExpect(status().isCreated());
        mvc.perform(as(alice, put("/users/alice/artist/art1/follow"))).andExpect(status().isOk());
        mvc.perform(as(alice, put("/users/alice/artist/art1/unfollow"))).andExpect(status().isOk());
        mvc.perform(as(alice, json(put("/users/alice"), "{\"id\":\"alice\",\"name\":\"Alice\"}"))).andExpect(status().isOk());
        mvc.perform(as(alice, json(post("/artwork/artist/alice"), "{\"title\":\"t\",\"genreId\":\"g\"}"))).andExpect(status().isCreated());
        mvc.perform(as(alice, json(post("/favorites/user/alice"), "{\"title\":\"Saved\"}"))).andExpect(status().isCreated());
        mvc.perform(as(alice, put("/favorites/artwork/add-artwork/alices-favorites/a1"))).andExpect(status().isOk());
        mvc.perform(as(alice, put("/favorites/alices-favorites/artwork/a1/remove-artwork"))).andExpect(status().isOk());
        mvc.perform(as(alice, delete("/comments/alices-comment"))).andExpect(status().isOk());
        mvc.perform(as(alice, json(put("/artwork/alices-artwork/status"), "{\"status\":\"DELETED\"}"))).andExpect(status().isOk());
        mvc.perform(as(alice, json(put("/artist/alice"), "{\"id\":\"alice\",\"name\":\"Alice\"}"))).andExpect(status().isOk());
        // what the app sends when registering as an artist: ?userId= and the same id in the body
        mvc.perform(as(alice, json(post("/artist").param("userId", "alice"), "{\"id\":\"alice\",\"name\":\"Alice\"}")))
                .andExpect(status().isCreated());
        // creating a genre during upload is open to any logged-in user
        mvc.perform(as(alice, json(post("/genres"), "{\"name\":\"Abstract\"}"))).andExpect(status().isCreated());
    }

    @Test
    void writesForSomeoneElseAre403Json() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[]{
                put("/artwork/user/like/a1/bob"),
                put("/artwork/user/unlike/a1/bob"),
                put("/artwork/user/dislike/a1/bob"),
                post("/artwork/a1/view/bob"),
                json(post("/comments/artwork/a1/user/bob"), "{\"text\":\"hi\"}"),
                put("/users/bob/artist/art1/follow"),
                put("/users/bob/artist/art1/unfollow"),
                json(put("/users/bob"), "{\"id\":\"bob\",\"name\":\"Bob\"}"),
                json(post("/artwork/artist/bob"), "{\"title\":\"t\",\"genreId\":\"g\"}"),
                json(post("/favorites/user/bob"), "{\"title\":\"Saved\"}"),
                put("/favorites/artwork/add-artwork/bobs-favorites/a1"),
                put("/favorites/bobs-favorites/artwork/a1/remove-artwork"),
                delete("/comments/bobs-comment"),
                json(put("/artwork/bobs-artwork/status"), "{\"status\":\"DELETED\"}"),
                json(put("/artist/bob"), "{\"id\":\"bob\",\"name\":\"Bob\"}"),
                json(post("/artist").param("userId", "bob"), "{\"name\":\"Bob\"}"),
                json(post("/artist").param("userId", "alice"), "{\"id\":\"bob\",\"name\":\"Bob\"}"),
                // (POST /gallery/artist/{artistId} is not covered: its GalleryProjection body cannot be deserialized)
                put("/gallery/g1/user/bob/like"),
                put("/gallery/g1/user/bob/dislike"),
                put("/genres/g1/artist/bob/assign-artist"),
                put("/genres/g1/artist/bob/un-assign-artist"),
                put("/genres/g1/artwork/bobs-artwork/assign-artwork"),
                put("/genres/g1/artwork/bobs-artwork/un-assign-artwork"),
        }) {
            mvc.perform(as(alice, request))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Forbidden"));
        }
    }

    @Test
    void browserPreflightNeedsNoToken() throws Exception {
        mvc.perform(options("/artwork/user/like/a1/alice")
                        .header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isOk());
    }

    private static String expiredToken(String username) {
        return Jwts.builder().subject(username)
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();
    }
}
