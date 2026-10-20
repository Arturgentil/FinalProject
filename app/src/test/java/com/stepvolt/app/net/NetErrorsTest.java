package com.stepvolt.app.net;

import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLHandshakeException;

import static org.junit.Assert.*;

public class NetErrorsTest {

    private static final String BASE = "https://stepvolt-backend.onrender.com/";

    @Test
    public void offlineWinsOverEverything() {
        String m = NetErrors.describe(new SocketTimeoutException(), false, BASE);
        assertTrue(m.startsWith("Sem internet"));
    }

    @Test
    public void timeoutExplainsTheServerIsWaking() {
        String m = NetErrors.describe(new SocketTimeoutException("timeout"), true, BASE);
        assertTrue(m, m.contains("acordar"));
        assertFalse(m.contains("Ajustes > Servidor"));        // não manda mexer no endereço à toa
    }

    @Test
    public void timeoutIsFoundInsideWrappedExceptions() {
        String m = NetErrors.describe(new IOException("falhou", new SocketTimeoutException()), true, BASE);
        assertTrue(m.contains("acordar"));
    }

    @Test
    public void unknownHostMentionsAddress() {
        String m = NetErrors.describe(new UnknownHostException("x"), true, BASE);
        assertTrue(m.contains("stepvolt-backend.onrender.com"));
        assertTrue(m.contains("Ajustes > Servidor"));
    }

    @Test
    public void connectAndSslHaveOwnMessages() {
        assertTrue(NetErrors.describe(new ConnectException(), true, BASE).contains("fora do ar"));
        assertTrue(NetErrors.describe(new SSLHandshakeException("x"), true, BASE).contains("https://"));
    }

    @Test
    public void transientDetection() {
        assertTrue(NetErrors.isTransient(new SocketTimeoutException()));
        assertTrue(NetErrors.isTransient(new IOException("a", new ConnectException())));
        assertFalse(NetErrors.isTransient(new UnknownHostException()));
    }

    @Test
    public void proxyStatusCodes() {
        assertTrue(NetErrors.describeHttp(503).contains("iniciando"));
        assertTrue(NetErrors.describeHttp(502).contains("iniciando"));
        assertTrue(NetErrors.describeHttp(429).contains("Muitas tentativas"));
        assertTrue(NetErrors.describeHttp(418).contains("418"));
    }

    @Test
    public void nullBaseUrlDoesNotCrash() {
        assertNotNull(NetErrors.describe(new UnknownHostException(), true, null));
    }
}
