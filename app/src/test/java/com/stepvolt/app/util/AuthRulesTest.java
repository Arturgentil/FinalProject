package com.stepvolt.app.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class AuthRulesTest {

    @Test
    public void emailMatchesServerRule() {
        assertNull(AuthRules.validateEmail("ana@email.com"));
        assertNull(AuthRules.validateEmail("  ana.souza+teste@mail.co.uk  "));
        assertNotNull(AuthRules.validateEmail(""));
        assertNotNull(AuthRules.validateEmail(null));
        assertNotNull(AuthRules.validateEmail("sem-arroba"));
        assertNotNull(AuthRules.validateEmail("a@b"));
        assertNotNull(AuthRules.validateEmail("a b@c.com"));
        assertNotNull(AuthRules.validateEmail("a@c.c"));
    }

    @Test
    public void passwordLengthLimits() {
        assertNotNull(AuthRules.validatePassword(""));
        assertNotNull(AuthRules.validatePassword("1234567"));
        assertNull(AuthRules.validatePassword("12345678"));
        assertNull(AuthRules.validatePassword(new String(new char[128]).replace('\0', 'a')));
        assertNotNull(AuthRules.validatePassword(new String(new char[129]).replace('\0', 'a')));
    }

    @Test
    public void passwordKeepsSpacesAtTheEnds() {
        // a senha NÃO é aparada: espaço faz parte dela
        assertNull(AuthRules.validatePassword("senha   1"));
        assertNull(AuthRules.validateConfirm("abc 12345", "abc 12345"));
        assertNotNull(AuthRules.validateConfirm("abc 12345", "abc 12345 "));
    }

    @Test
    public void confirmMustMatch() {
        assertNull(AuthRules.validateConfirm("abc12345", "abc12345"));
        assertNotNull(AuthRules.validateConfirm("abc12345", "abc12346"));
        assertNotNull(AuthRules.validateConfirm("abc12345", ""));
    }

    @Test
    public void strengthScale() {
        assertEquals(0, AuthRules.strength(""));
        assertEquals(1, AuthRules.strength("abc"));
        assertEquals(1, AuthRules.strength("12345678"));      // comum
        assertEquals(1, AuthRules.strength("aaaaaaaaaa"));    // repetida
        assertEquals(1, AuthRules.strength("password"));       // comum
        assertTrue(AuthRules.strength("corredor90") >= 2);
        assertTrue(AuthRules.strength("Corredor90!Noite") >= 3);
        assertEquals(4, AuthRules.strength("Corredor90!NoiteLonga"));
        for (int i = 0; i <= 4; i++) assertFalse(AuthRules.strengthLabel(i).isEmpty());
    }
}
