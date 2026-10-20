package com.stepvolt.app.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class AvatarTextTest {

    @Test
    public void initialsUseFirstAndLastName() {
        assertEquals("AS", AvatarText.initials("Ana Souza"));
        assertEquals("AL", AvatarText.initials("Ana Maria de Lima"));
        assertEquals("A", AvatarText.initials("ana"));
        assertEquals("\u00c9S", AvatarText.initials("  \u00e9rica   silva "));
    }

    @Test
    public void emptyOrSymbolsGiveNoInitials() {
        assertEquals("", AvatarText.initials(null));
        assertEquals("", AvatarText.initials("   "));
        assertEquals("", AvatarText.initials("!!!"));
    }

    @Test
    public void colorIsStablePerName() {
        int a = AvatarText.colorIndex("Ana Souza", 6);
        assertEquals(a, AvatarText.colorIndex("ana souza", 6));
        assertTrue(a >= 0 && a < 6);
        assertEquals(0, AvatarText.colorIndex(null, 6));
        assertEquals(0, AvatarText.colorIndex("x", 0));
    }

    @Test
    public void resolveUrlJoinsRelativePaths() {
        assertEquals("https://api.x.com/api/users/7/avatar?v=1",
                AvatarText.resolveUrl("https://api.x.com/", "/api/users/7/avatar?v=1"));
        assertEquals("https://api.x.com/api/users/7/avatar?v=1",
                AvatarText.resolveUrl("https://api.x.com", "/api/users/7/avatar?v=1"));
        assertEquals("https://cdn.x.com/a.jpg", AvatarText.resolveUrl("https://api.x.com/", "https://cdn.x.com/a.jpg"));
    }

    @Test
    public void resolveUrlHandlesMissingPieces() {
        assertNull(AvatarText.resolveUrl("https://api.x.com/", null));
        assertNull(AvatarText.resolveUrl("https://api.x.com/", "  "));
        assertNull(AvatarText.resolveUrl(null, "/api/users/7/avatar"));
        assertNull(AvatarText.resolveUrl("", "/api/users/7/avatar"));
    }
}
