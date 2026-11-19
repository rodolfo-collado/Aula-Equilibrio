package com.practice.aulaequilibrio.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.Base64;

public final class PasswordHasher {
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private PasswordHasher() {}
    public static void validate(char[] password) {
        if (password == null || password.length < 12 || password.length > 128)
            throw new ValidationException("La contraseña debe tener entre 12 y 128 caracteres.");
    }
    public static String hash(char[] password) {
        validate(password);
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    public static boolean verify(char[] password, String encoded) {
        if (password == null || password.length > 128 || encoded == null) return false;
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
            int rounds = Integer.parseInt(parts[1]);
            if (rounds < ITERATIONS || rounds > 2_000_000) return false;
            return MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]),
                    derive(password, Base64.getDecoder().decode(parts[2]), rounds));
        } catch (IllegalArgumentException error) { return false; }
    }
    private static byte[] derive(char[] password, byte[] salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException error) { throw new IllegalStateException("No se pudo procesar la contraseña.", error); }
        finally { spec.clearPassword(); }
    }
    public static String newToken() {
        byte[] token = new byte[32]; RANDOM.nextBytes(token); return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
    public static String tokenHash(String token) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
