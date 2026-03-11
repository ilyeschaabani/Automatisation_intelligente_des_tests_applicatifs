package com.pfe.platform.authenticationmicroservice.Service.Github;

import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Encrypts/decrypts tokens for storage (AES-256-GCM).
 *
 * Stored format: v1:{base64(iv)}:{base64(ciphertext)}
 */
@Component
public class GitHubTokenManager {

    private static final String VERSION_PREFIX = "v1";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public GitHubTokenManager(@Value("${app.crypto.github-token-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.secretKey = null;
            return;
        }
        byte[] keyBytes = Base64.decodeBase64(base64Key);
        if (keyBytes == null || keyBytes.length != 32) {
            throw new IllegalArgumentException("app.crypto.github-token-key must be base64 for 32 bytes (AES-256 key)");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        if (secretKey == null) {
            throw new IllegalStateException("GitHub token encryption key is not configured (app.crypto.github-token-key)");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));

            return VERSION_PREFIX + ":" + Base64.encodeBase64String(iv) + ":" + Base64.encodeBase64String(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt GitHub token", e);
        }
    }

    /**
     * @return true if the token matches the encrypted storage format.
     */
    public boolean isEncryptedFormat(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) return false;
        return tokenValue.startsWith(VERSION_PREFIX + ":")
                || tokenValue.startsWith("enc:" + VERSION_PREFIX + ":")
                || tokenValue.startsWith("gcm:" + VERSION_PREFIX + ":");
    }

    public String decrypt(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) return null;

        // Backward compatibility: if it's not in our encrypted format, treat it as plaintext.
        if (!isEncryptedFormat(tokenValue)) {
            return tokenValue;
        }

        if (secretKey == null) {
            throw new IllegalStateException("GitHub token encryption key is not configured (app.crypto.github-token-key)");
        }

        try {
            String normalized = tokenValue;
            if (normalized.startsWith("enc:")) normalized = normalized.substring("enc:".length());
            if (normalized.startsWith("gcm:")) normalized = normalized.substring("gcm:".length());

            String[] parts = normalized.split(":");
            if (parts.length != 3 || !VERSION_PREFIX.equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported encrypted token format");
            }
            byte[] iv = Base64.decodeBase64(parts[1]);
            byte[] cipherText = Base64.decodeBase64(parts[2]);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] decrypted = cipher.doFinal(cipherText);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt GitHub token", e);
        }
    }
}
