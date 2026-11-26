package tpf;

import java.util.Base64;

// Este objeto representa um chunk deppois de selado
public record Sealed(String id, int keyVersion, byte[] salt, byte[] nonce, byte[] cifrado) {
    public Sealed { // clone dos arrays para se forem alterados, a copia continua intacta
        salt = salt.clone();
        nonce = nonce.clone();
        cifrado = cifrado.clone();
    }

    @Override
    public byte[] salt() {
        return salt.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }

    @Override
    public byte[] cifrado() {
        return cifrado.clone();
    }

    public String cabecalho() {
        Base64.Encoder codificador = Base64.getEncoder();
        return keyVersion + ";" + codificador.encodeToString(salt)
                + ";" + codificador.encodeToString(nonce);
    }

    public static Sealed ler(String id, String cabecalho, byte[] cifrado) {
        String[] campos = cabecalho.strip().split(";", -1);
        if (campos.length != 3) {
            throw new IllegalArgumentException("Cabeçalho .hdr inválido.");
        }
        Base64.Decoder descodificador = Base64.getDecoder();
        return new Sealed(id, Integer.parseInt(campos[0]),
                descodificador.decode(campos[1]), descodificador.decode(campos[2]), cifrado);
    }

}
