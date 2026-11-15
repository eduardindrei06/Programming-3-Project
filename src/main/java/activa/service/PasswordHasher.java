package activa.service;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    private static final int ITERATIONS = 210000;
    private PasswordHasher() { }
    public static String hash(char[] password) {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    public static boolean matches(char[] password, String encoded) {
        String[] parts = encoded.split(":", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Invalid stored password hash.");
        int iterations = Integer.parseInt(parts[0]);
        if (iterations < 100000 || iterations > 1000000) throw new IllegalArgumentException("Invalid hash iterations.");
        byte[] salt = Base64.getDecoder().decode(parts[1]), expected = Base64.getDecoder().decode(parts[2]);
        if (salt.length != 16 || expected.length != 32) throw new IllegalArgumentException("Invalid password hash size.");
        return MessageDigest.isEqual(expected, derive(password, salt, iterations));
    }
    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("PBKDF2 is unavailable.", e); }
        finally { spec.clearPassword(); }
    }
}
