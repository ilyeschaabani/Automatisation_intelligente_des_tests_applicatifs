package com.pfe.platform.authenticationmicroservice.Service.Github;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitHubTokenManagerTests {

    @Test
    void encryptDecrypt_roundTrip() {
        // 32 bytes (0..31) base64
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;
        String base64Key = java.util.Base64.getEncoder().encodeToString(key);

        GitHubTokenManager mgr = new GitHubTokenManager(base64Key);

        String raw = "gho_testtoken_123";
        String enc = mgr.encrypt(raw);
        assertNotNull(enc);
        assertNotEquals(raw, enc);

        String dec = mgr.decrypt(enc);
        assertEquals(raw, dec);
    }

    @Test
    void decrypt_rejectsBadFormat() {
        byte[] key = new byte[32];
        String base64Key = java.util.Base64.getEncoder().encodeToString(key);
        GitHubTokenManager mgr = new GitHubTokenManager(base64Key);

        assertThrows(IllegalStateException.class, () -> mgr.decrypt("not-a-valid-token"));
    }
}

