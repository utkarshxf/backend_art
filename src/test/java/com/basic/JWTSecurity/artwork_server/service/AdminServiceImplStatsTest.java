package com.basic.JWTSecurity.artwork_server.service;

import com.basic.JWTSecurity.artwork_server.dto.AdminUserDetailsRecord;
import com.basic.JWTSecurity.artwork_server.dto.AdminUserSummaryRecord;
import com.basic.JWTSecurity.artwork_server.dto.CatalogueStatsRecord;
import com.basic.JWTSecurity.artwork_server.model.projection.UserProjection;
import com.basic.JWTSecurity.artwork_server.repository.AdminGraphQueries;
import com.basic.JWTSecurity.artwork_server.repository.ArtistRepository;
import com.basic.JWTSecurity.artwork_server.repository.ArtworkRepository;
import com.basic.JWTSecurity.artwork_server.repository.GenreRepository;
import com.basic.JWTSecurity.artwork_server.repository.UserRepository;
import com.basic.JWTSecurity.auth.model.Profile;
import com.basic.JWTSecurity.auth.repository.ProfileRepository;
import com.basic.JWTSecurity.auth.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Merging/robustness of the admin overview service against Neo4jClient-shaped row maps.
// The graph itself is not exercised here (no container available); AdminGraphQueries owns the Cypher.
class AdminServiceImplStatsTest {

    private ArtworkRepository artworkRepository;
    private ArtistRepository artistRepository;
    private GenreRepository genreRepository;
    private ProfileRepository profileRepository;
    private ProfileService profileService;
    private UserRepository userRepository;
    private AdminGraphQueries graph;
    private AdminServiceImpl service;

    @BeforeEach
    void setUp() {
        artworkRepository = mock(ArtworkRepository.class);
        artistRepository = mock(ArtistRepository.class);
        genreRepository = mock(GenreRepository.class);
        profileRepository = mock(ProfileRepository.class);
        profileService = mock(ProfileService.class);
        userRepository = mock(UserRepository.class);
        graph = mock(AdminGraphQueries.class);
        service = new AdminServiceImpl(artistRepository, artworkRepository, genreRepository,
                mock(ArtistService.class), mock(ArtworkService.class), mock(YearService.class),
                profileRepository, profileService, userRepository, graph);
    }

    private static Profile profile(String id, String username) {
        Profile profile = new Profile();
        profile.setId(id);
        profile.setUsername(username);
        return profile;
    }

    @Test
    void catalogueStatsMergeRowMapsAndTolerateNulls() {
        when(artworkRepository.countCatalogueArtworks()).thenReturn(3201L);
        when(artworkRepository.countDeletedArtworks()).thenReturn(4L);
        when(artistRepository.count()).thenReturn(3458L);
        when(genreRepository.count()).thenReturn(158L);
        when(graph.countArtworksPerArtist()).thenReturn(List.of(
                Map.of("id", "a1", "name", "Vincent van Gogh", "artworkCount", 12L),
                Map.of("id", "a2", "artworkCount", 0L), // missing name
                Map.of("id", "a3", "name", "Claude Monet"))); // missing count

        CatalogueStatsRecord stats = service.getCatalogueStats();

        assertEquals(3201L, stats.totalArtworks());
        assertEquals(3458L, stats.totalArtists());
        assertEquals(158L, stats.totalGenres());
        assertEquals(4L, stats.deletedArtworks());
        assertEquals(3, stats.artists().size());
        assertEquals(12L, stats.artists().get(0).artworkCount());
        assertNull(stats.artists().get(1).name());
        assertEquals(0L, stats.artists().get(2).artworkCount());
    }

    @Test
    void catalogueStatsDegradeWhenTheGraphFails() {
        when(artworkRepository.countCatalogueArtworks()).thenThrow(new RuntimeException("graph down"));
        when(artworkRepository.countDeletedArtworks()).thenReturn(0L);
        when(artistRepository.count()).thenReturn(1L);
        when(genreRepository.count()).thenReturn(1L);
        when(graph.countArtworksPerArtist()).thenThrow(new RuntimeException("graph down"));

        CatalogueStatsRecord stats = service.getCatalogueStats();

        assertEquals(0L, stats.totalArtworks());
        assertEquals(1L, stats.totalArtists());
        assertTrue(stats.artists().isEmpty());
    }

    @Test
    void usersListMergesMongoAccountsWithGraphCounts() {
        when(profileRepository.findAll()).thenReturn(List.of(
                profile("665f0a1b2c3d4e5f6a7b8c9d", "meera_art"),
                profile("not-an-objectid", "ravi96")));
        when(profileService.isAdmin(anyString())).thenAnswer(inv -> "meera_art".equals(inv.getArgument(0)));
        when(graph.userArtists()).thenReturn(List.of(
                Map.of("id", "meera_art", "artistId", "b8f1c2d3", "artistName", "Meera K")));
        when(graph.userArtworkCounts()).thenReturn(List.of(Map.of("id", "meera_art", "count", 7L)));
        when(graph.userLikeCounts()).thenReturn(List.of(Map.of("id", "meera_art", "count", 51L)));
        when(graph.userFollowerCounts()).thenReturn(List.of(Map.of("id", "meera_art", "count", 19L)));
        when(graph.userCommentCounts()).thenReturn(List.of());

        List<AdminUserSummaryRecord> users = service.listUsers(null, "username", "asc", null);

        assertEquals(2, users.size());
        AdminUserSummaryRecord meera = users.get(0);
        assertEquals("meera_art", meera.username());
        assertNotNull(meera.joinedAt()); // ObjectId timestamp recovered
        assertTrue(meera.admin());
        assertTrue(meera.artist());
        assertEquals(7L, meera.artworkCount());
        assertEquals(51L, meera.likeCount());
        assertEquals(19L, meera.followerCount());
        assertEquals(0L, meera.commentCount());
        AdminUserSummaryRecord ravi = users.get(1);
        assertNull(ravi.joinedAt()); // non-ObjectId id: no recoverable join date
        assertTrue(!ravi.artist());
    }

    @Test
    void userDetailsCombineProfileGraphAndActivity() {
        when(profileRepository.findByUsername("meera_art"))
                .thenReturn(Optional.of(profile("665f0a1b2c3d4e5f6a7b8c9d", "meera_art")));
        when(userRepository.findByIdProjection("meera_art")).thenReturn(Optional.of(new UserProjection() {
            public String getId() { return "meera_art"; }
            public String getName() { return "Meera Krishnan"; }
            public String getProfilePicture() { return "pic"; }
            public java.time.LocalDate getDob() { return java.time.LocalDate.of(1998, 4, 17); }
            public String getGender() { return "female"; }
            public String getLanguage() { return "en"; }
            public String getCountryIso2() { return "in"; }
        }));
        when(graph.userArtist("meera_art")).thenReturn(List.of(
                Map.of("id", "b8f1c2d3", "name", "Meera K", "image_url", "img")));
        when(graph.userArtworks(anyString(), anyInt())).thenReturn(List.of(
                Map.of("id", "w101", "title", "Monsoon Study", "status", "APPROVED",
                        "releasedDate", "2025-06-01", "likeCount", 34L)));
        when(graph.userRecentLikes(anyString(), anyInt())).thenReturn(List.of(
                Map.of("id", "w201", "title", "Water Lilies", "artistId", "21d8e8ab",
                        "likedAt", "2026-11-01T10:00")));
        when(userRepository.countArtworksForUser("meera_art")).thenReturn(7L);
        when(userRepository.countLikesForUser("meera_art")).thenReturn(51L);
        when(userRepository.countFollowersForUser("meera_art")).thenReturn(19L);
        when(userRepository.countCommentsForUser("meera_art")).thenReturn(12L);

        AdminUserDetailsRecord details = service.getUserDetails("meera_art");

        assertEquals("Meera Krishnan", details.name());
        assertTrue(details.artist());
        assertEquals("Meera K", details.artistName());
        assertEquals(7L, details.artworkCount());
        assertEquals(1, details.artworks().size());
        assertEquals(34L, details.artworks().get(0).likeCount());
        assertNull(details.artworks().get(0).imageUrlCompressed());
        assertEquals(1, details.recentLikes().size());
    }
}
