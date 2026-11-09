package com.savewise.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokensTest {
    @Test
    void sha256MatchesTheKnownTestVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Tokens.sha256("abc"));
    }

    @Test
    void tokensAreLongUniqueAndUrlSafe() {
        String a = Tokens.newToken(), b = Tokens.newToken();
        assertNotEquals(a, b);
        assertTrue(a.length() >= 43);
        assertTrue(a.matches("[A-Za-z0-9_-]+"));
    }
}
