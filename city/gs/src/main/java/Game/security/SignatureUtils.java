package Game.security;

import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;


public class SignatureUtils {
    public static final String ALGORITHM = "EC";
    public static final String SIGN_ALGORITHM = "SHA256withECDSA";

    // Initialize the key pair
    public static KeyPair initKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            ECGenParameterSpec ecGenParameterSpec = new ECGenParameterSpec("secp256k1");
            generator.initialize(ecGenParameterSpec, new SecureRandom());
            generator.initialize(256);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Get public key
    public static byte[] getPublicKey(KeyPair keyPair) {
        byte[] bytes = keyPair.getPublic().getEncoded();
        return bytes;
    }

    // Get public key
    public static String getPublicKeyStr(KeyPair keyPair) {
        byte[] bytes = keyPair.getPublic().getEncoded();
        return encodeHex(bytes);
    }

    // Get private key
    public static byte[] getPrivateKey(KeyPair keyPair) {
        byte[] bytes = keyPair.getPrivate().getEncoded();
        return bytes;
    }

    // Get private key
    public static String getPrivateKeyStr(KeyPair keyPair) {
        byte[] bytes = keyPair.getPrivate().getEncoded();
        return encodeHex(bytes);
    }

    // signature
    public static byte[] sign(byte[] data, byte[] privateKey, String signAlgorithm) {
        try {
            // Restore to use
            PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privateKey);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            PrivateKey priKey = keyFactory.generatePrivate(pkcs8EncodedKeySpec);
            // 1、Instantiate Signature
            Signature signature = Signature.getInstance(signAlgorithm);
            // 2、Initialize Signature
            signature.initSign(priKey);
            // 3、update data
            signature.update(data);
            // 4、signature
            return signature.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // verification
    public static boolean verify(byte[] data, byte[] publicKey, byte[] sign, String signAlgorithm) {
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKey);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            PublicKey pubKey = keyFactory.generatePublic(keySpec);
            // 1、Instantiate Signature
            Signature signature = Signature.getInstance(signAlgorithm);
            // 2、Initialize Signature
            signature.initVerify(pubKey);
            // 3、update data
            signature.update(data);
            // 4、signature
            return signature.verify(sign);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Data quasi-hexadecimal encoding
    public static String encodeHex(final byte[] data) {
        return encodeHex(data, true);
    }

    // Data to hexadecimal encoding
    public static String encodeHex(final byte[] data, final boolean toLowerCase) {
        final char[] DIGITS_LOWER = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
        final char[] DIGITS_UPPER = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
        final char[] toDigits = toLowerCase ? DIGITS_LOWER : DIGITS_UPPER;
        final int l = data.length;
        final char[] out = new char[l << 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = toDigits[(0xF0 & data[i]) >>> 4];
            out[j++] = toDigits[0x0F & data[i]];
        }
        return new String(out);
    }

    public static void testfun() {
        String str = "java小工匠";
        byte[] data = str.getBytes();
        // Initial key
        KeyPair keyPair1 = initKey();
        byte[] publicKey1 = getPublicKey(keyPair1);
        byte[] privateKey1 = getPrivateKey(keyPair1);
        String st1 = privateKey1.toString();
        String st2 = publicKey1.toString();

        KeyPair keyPair2 = initKey();
        byte[] publicKey2 = getPublicKey(keyPair2);
        byte[] privateKey2 = getPrivateKey(keyPair2);

        // signature
        byte[] sign = sign(str.getBytes(), privateKey1, SIGN_ALGORITHM);
        // verification
        boolean b = verify(data, publicKey1, sign, SIGN_ALGORITHM);
        System.out.println("验证:" + b);
    }
}
