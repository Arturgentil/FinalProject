package com.stepvolt.app.util;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.*;

public class ProfileRulesTest {

    private static Calendar day(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d);
        return c;
    }

    @Test
    public void nameAndUsername() {
        assertNull(ProfileRules.validateName("Ana"));
        assertNotNull(ProfileRules.validateName("A"));
        assertNotNull(ProfileRules.validateName("   "));
        assertNotNull(ProfileRules.validateName(null));
        assertNotNull(ProfileRules.validateName(new String(new char[81]).replace('\0', 'a')));
        assertNull(ProfileRules.validateUsername("ana_s.22"));
        assertNotNull(ProfileRules.validateUsername("a b"));
        assertNotNull(ProfileRules.validateUsername("ab"));
        assertNotNull(ProfileRules.validateUsername("n\u00e3o"));
    }

    @Test
    public void birthDateRules() {
        Calendar today = day(2026, 10, 5);
        assertNull(ProfileRules.validateBirthDate("1990-05-17", today));
        assertNotNull(ProfileRules.validateBirthDate("2027-01-01", today));
        assertNotNull(ProfileRules.validateBirthDate("1899-12-31", today));
        assertNotNull(ProfileRules.validateBirthDate("1990-02-31", today));
        assertNotNull(ProfileRules.validateBirthDate("17/05/1990", today));
        assertNotNull(ProfileRules.validateBirthDate("1990-5-7", today));
        assertNull(ProfileRules.validateBirthDate("2000-02-29", today));      // bissexto
        assertNotNull(ProfileRules.validateBirthDate("2001-02-29", today));
    }

    @Test
    public void weightAndHeightAcceptCommaAndEmpty() {
        assertNull(ProfileRules.validateWeight(""));
        assertNull(ProfileRules.validateWeight("70,5"));
        assertNotNull(ProfileRules.validateWeight("abc"));
        assertNotNull(ProfileRules.validateWeight("5"));
        assertNotNull(ProfileRules.validateWeight("900"));
        assertNull(ProfileRules.validateHeight("175"));
        assertNotNull(ProfileRules.validateHeight("30"));
        assertEquals(70.5, ProfileRules.parseNumber("70,5"), 1e-9);
        assertNull(ProfileRules.parseNumber("NaN"));
        assertNull(ProfileRules.parseNumber(""));
    }

    @Test
    public void dateFormatAndParseRoundTrip() {
        assertEquals("1990-05-07", ProfileRules.formatDate(1990, 5, 7));
        int[] d = ProfileRules.parseDate("1990-05-07");
        assertEquals(1990, d[0]); assertEquals(5, d[1]); assertEquals(7, d[2]);
        assertNull(ProfileRules.parseDate("1990-5-7"));
        assertNull(ProfileRules.parseDate("hoje"));
        assertNull(ProfileRules.parseDate(null));
    }
}
