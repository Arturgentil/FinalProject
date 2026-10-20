package com.stepvolt.app.protocol;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class PacketCodecTest {

    @Test
    public void sealedPacketDecodesAndChecksumMatches() {
        String json = "{\"v\":2,\"seq\":1,\"bpm\":82}";
        PacketCodec.Decoded d = PacketCodec.decode(PacketCodec.seal(json));
        assertTrue(d.valid);
        assertTrue(d.checksummed);
        assertEquals(json, d.json);
    }

    @Test
    public void corruptedPacketIsRejected() {
        String sealed = PacketCodec.seal("{\"v\":2,\"seq\":1,\"bpm\":82}");
        String corrupted = sealed.replace("82", "83");
        PacketCodec.Decoded d = PacketCodec.decode(corrupted);
        assertFalse(d.valid);
    }

    @Test
    public void v2WithoutChecksumIsRejected() {
        assertFalse(PacketCodec.decode("{\"v\":2,\"bpm\":82}").valid);
    }

    @Test
    public void v1WithoutChecksumIsAccepted() {
        PacketCodec.Decoded d = PacketCodec.decode("{\"bpm\":82,\"spo2\":97}");
        assertTrue(d.valid);
        assertFalse(d.checksummed);
    }

    @Test
    public void garbageIsRejected() {
        assertFalse(PacketCodec.decode("hello").valid);
        assertFalse(PacketCodec.decode("").valid);
        assertFalse(PacketCodec.decode(null).valid);
    }

    @Test
    public void malformedChecksumIsRejected() {
        assertFalse(PacketCodec.decode("{\"v\":2}*ZZ").valid);
        assertFalse(PacketCodec.decode("{\"v\":2}*1").valid);
    }

    @Test
    public void commandIsSealedWithIdAndArgument() {
        String cmd = PacketCodec.encodeCommand("calibrate", null, 7);
        assertTrue(cmd.startsWith("CMD:CALIBRATE:7*"));
        String withArg = PacketCodec.encodeCommand("sos_test", "x", 3);
        assertTrue(withArg.startsWith("CMD:SOS_TEST:x:3*"));
        int star = cmd.lastIndexOf('*');
        assertEquals(PacketCodec.hex2(PacketCodec.checksum(cmd.substring(0, star))), cmd.substring(star + 1));
    }

    @Test
    public void assemblerJoinsSplitV2Packet() {
        String sealed = PacketCodec.seal("{\"v\":2,\"seq\":5,\"bpm\":70}") + "\n";
        StreamAssembler a = new StreamAssembler();
        assertTrue(a.feed(sealed.substring(0, 10)).isEmpty());
        assertTrue(a.feed(sealed.substring(10, sealed.length() - 3)).isEmpty());
        List<String> out = a.feed(sealed.substring(sealed.length() - 3));
        assertEquals(1, out.size());
        assertTrue(PacketCodec.decode(out.get(0)).valid);
    }

    @Test
    public void assemblerSplitsTwoPacketsInOneChunk() {
        String one = PacketCodec.seal("{\"v\":2,\"seq\":1,\"bpm\":70}") + "\n";
        String two = PacketCodec.seal("{\"v\":2,\"seq\":2,\"bpm\":71}") + "\n";
        List<String> out = new StreamAssembler().feed(one + two);
        assertEquals(2, out.size());
    }

    @Test
    public void assemblerHandlesV1WithoutChecksum() {
        List<String> out = new StreamAssembler().feed("{\"bpm\":82}{\"bpm\":83}");
        assertEquals(2, out.size());
    }

    @Test
    public void assemblerIgnoresBracesInsideStrings() {
        String payload = "{\"fw\":\"a}b\",\"bpm\":60}";
        List<String> out = new StreamAssembler().feed(payload);
        assertEquals(1, out.size());
        assertEquals(payload, out.get(0));
    }

    @Test
    public void assemblerDropsLeadingNoise() {
        List<String> out = new StreamAssembler().feed("xx\r\n{\"bpm\":1}");
        assertEquals(1, out.size());
    }
}
