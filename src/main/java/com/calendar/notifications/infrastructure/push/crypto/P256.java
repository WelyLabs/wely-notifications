package com.calendar.notifications.infrastructure.push.crypto;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

/** P-256 keys in the raw forms Web Push uses: a 65-byte uncompressed point, a 32-byte scalar. */
public final class P256 {

    static final int COORDINATE_LENGTH = 32;
    static final int POINT_LENGTH = 1 + 2 * COORDINATE_LENGTH;
    private static final byte UNCOMPRESSED = 0x04;
    private static final String CURVE = "secp256r1";
    private static final ECParameterSpec SPEC = curve();

    private P256() {
    }

    public static ECPublicKey publicKey(byte[] point) {
        if (point.length != POINT_LENGTH || point[0] != UNCOMPRESSED) {
            throw new IllegalArgumentException("Not an uncompressed P-256 point");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(point, 1, 1 + COORDINATE_LENGTH));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(point, 1 + COORDINATE_LENGTH, POINT_LENGTH));
        if (!onCurve(x, y)) {
            throw new IllegalArgumentException("Not a point of P-256");
        }
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), SPEC));
        } catch (GeneralSecurityException invalid) {
            throw new IllegalArgumentException("Not a P-256 public key", invalid);
        }
    }

    public static ECPrivateKey privateKey(byte[] scalar) {
        if (scalar.length != COORDINATE_LENGTH) {
            throw new IllegalArgumentException("Not a 32-byte P-256 private key");
        }
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), SPEC));
        } catch (GeneralSecurityException invalid) {
            throw new IllegalArgumentException("Not a P-256 private key", invalid);
        }
    }

    /** The key as 0x04 || X || Y, each coordinate left-padded to 32 bytes. */
    public static byte[] encode(ECPublicKey key) {
        byte[] point = new byte[POINT_LENGTH];
        point[0] = UNCOMPRESSED;
        copyPadded(key.getW().getAffineX(), point, 1);
        copyPadded(key.getW().getAffineY(), point, 1 + COORDINATE_LENGTH);
        return point;
    }

    public static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("P-256 is not available in this JDK", unavailable);
        }
    }

    // The JDK does not check this when building the key: y^2 = x^3 + ax + b (mod p).
    private static boolean onCurve(BigInteger x, BigInteger y) {
        BigInteger p = ((ECFieldFp) SPEC.getCurve().getField()).getP();
        BigInteger left = y.modPow(BigInteger.TWO, p);
        BigInteger right = x.pow(3).add(SPEC.getCurve().getA().multiply(x)).add(SPEC.getCurve().getB()).mod(p);
        return x.compareTo(p) < 0 && y.compareTo(p) < 0 && left.equals(right);
    }

    // BigInteger drops leading zeros and may add a sign byte: both are undone here.
    private static void copyPadded(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int length = Math.min(bytes.length, COORDINATE_LENGTH);
        System.arraycopy(bytes, bytes.length - length, target, offset + COORDINATE_LENGTH - length, length);
    }

    private static ECParameterSpec curve() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec(CURVE));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("P-256 is not available in this JDK", unavailable);
        }
    }
}
