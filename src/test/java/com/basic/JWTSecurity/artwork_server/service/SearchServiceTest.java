package com.basic.JWTSecurity.artwork_server.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchServiceTest {

    @Test
    void termsAreLowercaseAccentFreeWordsWithoutLuceneSyntax() {
        assertEquals(List.of("durer", "self", "portrait"), SearchService.terms("  Dürer: Self-Portrait "));
        assertEquals(List.of("a", "or", "b"), SearchService.terms("a* OR (b)~ \"a\""));
        assertEquals(List.of(), SearchService.terms("  +-!() "));
        assertEquals(List.of(), SearchService.terms(null));
        assertEquals(8, SearchService.terms("a b c d e f g h i j").size());
    }

    @Test
    void luceneQueryMatchesEachWordExactlyAsPrefixAndFuzzyWhenLong() {
        assertEquals("(starry^3 OR starry* OR starry~1) AND (ni^3 OR ni*)",
                SearchService.luceneQuery(List.of("starry", "ni")));
    }

    @Test
    void snippetOnlyWhenTheMatchComesFromTheDescription() {
        String description = "Painted in 1889 while Van Gogh stayed at the asylum of Saint-Remy, the swirling sky "
                + "shows the view from his east-facing window just before sunrise, with the addition of an "
                + "imaginary village.";
        // explained by the title/artist -> no snippet
        assertNull(SearchService.snippet(description, "The Starry Night Vincent van Gogh", List.of("starry", "gogh")));
        // "asylum" is only in the description -> excerpt around it
        String snippet = SearchService.snippet(description, "The Starry Night Vincent van Gogh", List.of("asylum"));
        assertTrue(snippet.contains("asylum"), snippet);
        assertTrue(snippet.endsWith("…"), snippet);
        assertTrue(snippet.length() <= 145, snippet);
        assertNull(SearchService.snippet(null, "x", List.of("y")));
    }

    @Test
    void yearFromReleasedDate() {
        assertEquals("1632", SearchService.year("1632-01-01T00:00"));
        assertNull(SearchService.year(null));
        assertNull(SearchService.year("n/a"));
    }
}
