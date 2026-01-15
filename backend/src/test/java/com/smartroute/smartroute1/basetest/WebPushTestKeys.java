package com.smartroute.smartroute1.basetest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.SecureRandom;
import java.util.Base64;

@TestConfiguration
public class WebPushTestKeys {

    public static String p256dh() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        ECPublicKey pub = (ECPublicKey) kp.getPublic();
        BigInteger x = pub.getW().getAffineX();
        BigInteger y = pub.getW().getAffineY();

        byte[] xb = toFixedLength(x, 32);
        byte[] yb = toFixedLength(y, 32);

        byte[] uncompressed = new byte[65];
        uncompressed[0] = 0x04;
        System.arraycopy(xb, 0, uncompressed, 1, 32);
        System.arraycopy(yb, 0, uncompressed, 33, 32);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressed);
    }

    public static String auth() {
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(auth);
    }

    private static byte[] toFixedLength(BigInteger v, int size) {
        byte[] raw = v.toByteArray(); // may be 33 bytes with leading 0x00
        byte[] out = new byte[size];

        int srcPos = Math.max(0, raw.length - size);
        int length = Math.min(raw.length, size);
        System.arraycopy(raw, srcPos, out, size - length, length);

        return out;
    }
}
