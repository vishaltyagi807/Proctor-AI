package dev.varshit.proctor.notification.crypto;

public interface SecretCipher {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
