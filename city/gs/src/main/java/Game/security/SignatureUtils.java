package Game.security;

import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;


public class SignatureUtils {
    public static final String ALGORITHM = "EC";
    public static final String SIGN_ALGORITHM = "SHA256withECDSA";

    // 初始化密钥对
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

    // 获取公钥
    public static byte[] getPublicKey(KeyPair keyPair) {
        byte[] bytes = keyPair.getPublic().getEncoded();
        return bytes;
    }

    // 获取公钥
    public static String getPublicKeyStr(KeyPair keyPair) {
        byte[] bytes = keyPair.getPublic().getEncoded();
        return encodeHex(bytes);
    }

    // 获取私钥
    public static byte[] getPrivateKey(KeyPair keyPair) {
        byte[] bytes = keyPair.getPrivate().getEncoded();
        return bytes;
    }

    // 获取私钥
    public static String getPrivateKeyStr(KeyPair keyPair) {
        byte[] bytes = keyPair.getPrivate().getEncoded();
        return encodeHex(bytes);
    }

    // 签名
    public static byte[] sign(byte[] data, byte[] privateKey, String signAlgorithm) {
        try {
            // 还原使用
            PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privateKey);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            PrivateKey priKey = keyFactory.generatePrivate(pkcs8EncodedKeySpec);
            // 1、实例化Signature
            Signature signature = Signature.getInstance(signAlgorithm);
            // 2、初始化Signature
            signature.initSign(priKey);
            // 3、更新数据
            signature.update(data);
            // 4、签名
            return signature.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 验证
    public static boolean verify(byte[] data, byte[] publicKey, byte[] sign, String signAlgorithm) {
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKey);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            PublicKey pubKey = keyFactory.generatePublic(keySpec);
            // 1、实例化Signature
            Signature signature = Signature.getInstance(signAlgorithm);
            // 2、初始化Signature
            signature.initVerify(pubKey);
            // 3、更新数据
            signature.update(data);
            // 4、签名
            return signature.verify(sign);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 数据准16进制编码
    public static String encodeHex(final byte[] data) {
        return encodeHex(data, true);
    }

    // 数据转16进制编码
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
        // 初始化密钥度
        KeyPair keyPair1 = initKey();
        byte[] publicKey1 = getPublicKey(keyPair1);
        byte[] privateKey1 = getPrivateKey(keyPair1);
        String st1 = privateKey1.toString();
        String st2 = publicKey1.toString();

        KeyPair keyPair2 = initKey();
        byte[] publicKey2 = getPublicKey(keyPair2);
        byte[] privateKey2 = getPrivateKey(keyPair2);

        // 签名
        byte[] sign = sign(str.getBytes(), privateKey1, SIGN_ALGORITHM);
        // 验证
        boolean b = verify(data, publicKey1, sign, SIGN_ALGORITHM);
        System.out.println("验证:" + b);
    }
}
