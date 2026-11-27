package tpf;

import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;
import java.security.GeneralSecurityException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ChunkCryptoTest {
    private final byte[] chave = new byte[32];
    private final ChunkCrypto crypto = new ChunkCrypto(chave);

    @Test
    void abreEReconstroiOsRegistos() throws Exception {
        Registo a = new Registo(1, 1000, 20, 30, "Olá; mundo\nsegunda linha");
        Registo b = new Registo(2, 1001, 21, 30, "outro registo");
        Chunk chunk = new Chunk();
        chunk.adicionar(a);
        chunk.adicionar(b);

        byte[] dados = chunk.selar();
        Sealed sealed = crypto.seal(dados);
        Sealed lido = Sealed.ler(sealed.id(), sealed.cabecalho(), sealed.cifrado());

        assertEquals(0, dados.length % Wire.PAD_BYTES);
        assertEquals(dados.length + 16, sealed.cifrado().length);
        assertEquals(List.of(a, b), Chunk.ler(crypto.open(lido)));
        assertEquals(a, Registo.lerLinha(a.linhaDoLog()));
        assertThrows(IllegalStateException.class, () -> chunk.adicionar(a));
    }

    @Test
    void byteAlteradoFalhaNoHash() throws Exception {
        Sealed s = crypto.seal(new byte[4096]);
        byte[] bytes = alterarPrimeiroByte(s.cifrado());
        Sealed adulterado = new Sealed(s.id(), s.keyVersion(), s.salt(), s.nonce(), bytes);
        GeneralSecurityException e = assertThrows(GeneralSecurityException.class,
                () -> crypto.open(adulterado));
        assertEquals("O SHA-256 do .bin não coincide com o identificador.", e.getMessage());
    }

    @Test
    void versaoAlteradaERecusada() throws Exception {
        Sealed s = crypto.seal(new byte[4096]);
        Sealed outraVersao = new Sealed(s.id(), 2, s.salt(), s.nonce(), s.cifrado());
        GeneralSecurityException e = assertThrows(GeneralSecurityException.class,
                () -> crypto.open(outraVersao));
        assertEquals("Versão de chave não suportada.", e.getMessage());
    }

    @Test
    void saltNonceEChaveErradosFalhamNaAutenticacao() throws Exception {
        Sealed s = crypto.seal(new byte[4096]);
        Sealed saltErrado = new Sealed(s.id(), s.keyVersion(), alterarPrimeiroByte(s.salt()),
                s.nonce(), s.cifrado());
        Sealed nonceErrado = new Sealed(s.id(), s.keyVersion(), s.salt(),
                alterarPrimeiroByte(s.nonce()), s.cifrado());
        ChunkCrypto outraChave = new ChunkCrypto(alterarPrimeiroByte(chave));

        assertThrows(AEADBadTagException.class, () -> crypto.open(saltErrado));
        assertThrows(AEADBadTagException.class, () -> crypto.open(nonceErrado));
        assertThrows(AEADBadTagException.class, () -> outraChave.open(s));
    }

    private static byte[] alterarPrimeiroByte(byte[] original) {
        byte[] alterado = original.clone();
        alterado[0] ^= 1;
        return alterado;
    }
}
