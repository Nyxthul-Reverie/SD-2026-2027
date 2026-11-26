package tpf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class ClienteTeste implements WebSocket.Listener, AutoCloseable {
    private static final HttpClient CLIENTE = HttpClient.newHttpClient();
    private final BlockingQueue<String> recebidas = new LinkedBlockingQueue<>();
    private final BlockingQueue<Integer> pongs = new LinkedBlockingQueue<>();
    private final StringBuilder texto = new StringBuilder();
    private final CompletableFuture<Integer> codigoFecho = new CompletableFuture<>();
    private final WebSocket socket;

    ClienteTeste(URI url) throws Exception {
        socket = CLIENTE.newWebSocketBuilder().buildAsync(url, this).get(5, TimeUnit.SECONDS);
    }

    static KeyPair chaves() throws GeneralSecurityException {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    void enviar(String mensagem) throws Exception {
        socket.sendText(mensagem, true).get(5, TimeUnit.SECONDS);
    }

    String receber() throws InterruptedException {
        String mensagem = recebidas.poll(5, TimeUnit.SECONDS);
        assertNotNull(mensagem, "Mensagem WebSocket em falta");
        return mensagem;
    }

    String ola(String id, KeyPair par, String token) throws Exception {
        String mensagem = "OLA;" + id;
        if (token != null) {
            mensagem += ";" + Base64.getEncoder().encodeToString(par.getPublic().getEncoded()) + ";" + token;
        }
        enviar(mensagem);
        String desafio = receber();
        assertTrue(desafio.startsWith("DESAFIO;"));
        String nonce = desafio.substring("DESAFIO;".length());
        assertEquals(32, Base64.getDecoder().decode(nonce).length);
        return nonce;
    }

    static String prova(String idEnviado, String idAssinado, String nonce, KeyPair par)
            throws GeneralSecurityException {
        Signature assinatura = Signature.getInstance("Ed25519");
        assinatura.initSign(par.getPrivate());
        assinatura.update(Wire.dadosDaProva(idAssinado, nonce));
        return "PROVA;" + idEnviado + ";" + Base64.getEncoder().encodeToString(assinatura.sign());
    }

    void autenticar(String id, KeyPair par, String token) throws Exception {
        String nonce = ola(id, par, token);
        enviar(prova(id, id, nonce, par));
        enviar("SUB;" + Wire.topicoReplica(id));
        enviar("SUB;" + Wire.TOPICO_CONTROLO);
        barreira();
    }

    void barreira() throws Exception {
        socket.sendPing(ByteBuffer.wrap(new byte[]{37})).get(5, TimeUnit.SECONDS);
        assertEquals(37, pongs.poll(5, TimeUnit.SECONDS));
    }

    void esperarFecho() throws Exception {
        assertEquals(1008, codigoFecho.get(5, TimeUnit.SECONDS));
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence dados, boolean ultimo) {
        texto.append(dados);
        if (ultimo) {
            recebidas.add(texto.toString());
            texto.setLength(0);
        }
        ws.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onPong(WebSocket ws, ByteBuffer mensagem) {
        if (mensagem.remaining() == 1) {
            pongs.add((int) mensagem.get());
        }
        ws.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int codigo, String motivo) {
        codigoFecho.complete(codigo);
        return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable erro) {
        codigoFecho.completeExceptionally(erro);
    }

    @Override
    public void close() {
        socket.abort();
    }
}
