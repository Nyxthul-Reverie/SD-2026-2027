package tpf;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class ChunkCrypto {
    public static final int MASTER_KEY_BYTES = 32;
    private static final int VERSION = 1;
    private static final int SALT_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] INFO = "tpf-chunk-v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AAD_PREFIXO = "tpf-v1".getBytes(StandardCharsets.US_ASCII);

    private final byte[] masterKey;
    private final SecureRandom random = new SecureRandom();

    public ChunkCrypto(byte[] masterKey) {
        if (masterKey.length != MASTER_KEY_BYTES) {
            throw new IllegalArgumentException("master.key deve ter 32 bytes.");
        }
        this.masterKey = masterKey.clone();
    }

    public Sealed seal(byte[] dados) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(salt);
        random.nextBytes(nonce);
        byte[] cifrado = cifra(Cipher.ENCRYPT_MODE, VERSION, salt, nonce).doFinal(dados);
        return new Sealed(hash(cifrado), VERSION, salt, nonce, cifrado);
    }

    public byte[] open(Sealed sealed) throws GeneralSecurityException {
        byte[] cifrado = sealed.cifrado();
        if (!hash(cifrado).equals(sealed.id())) {
            throw new GeneralSecurityException("O SHA-256 do .bin não coincide com o identificador.");
        }
        if (sealed.keyVersion() != VERSION) {
            throw new GeneralSecurityException("Versão de chave não suportada.");
        }
        byte[] salt = sealed.salt();
        byte[] nonce = sealed.nonce();
        if (salt.length != SALT_BYTES || nonce.length != NONCE_BYTES) {
            throw new GeneralSecurityException("Salt ou nonce com tamanho inválido.");
        }
        return cifra(Cipher.DECRYPT_MODE, sealed.keyVersion(), salt, nonce).doFinal(cifrado);
    }

    static String hash(byte[] bytes) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private byte[] derivarDek(byte[] salt) throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = hmac.doFinal(masterKey);
        hmac.init(new SecretKeySpec(prk, "HmacSHA256"));
        hmac.update(INFO);
        return hmac.doFinal(new byte[] { 1 });
    }

    private Cipher cifra(int modo, int versao, byte[] salt, byte[] nonce)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(modo, new SecretKeySpec(derivarDek(salt), "AES"),
                new GCMParameterSpec(TAG_BITS, nonce));
        byte[] aad = ByteBuffer.allocate(AAD_PREFIXO.length + Integer.BYTES + salt.length + nonce.length)
                .put(AAD_PREFIXO).putInt(versao).put(salt).put(nonce).array();
        cipher.updateAAD(aad);
        return cipher;
    }
}
