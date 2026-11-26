package tpf;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class PubSubHub extends WebSocketServer {
    private enum Estado { NOVA, DESAFIADA, AUTENTICADA }

    private static final class Sessao {
        Estado estado = Estado.NOVA;
        String id;
        String nonce;
        String token;
        PublicKey chave;
    }

    record Publicacao(String id, String mensagem, PublicKey chave) {
    }

    private final Map<WebSocket, Sessao> sessoes = new HashMap<>();
    private final Map<String, WebSocket> autenticadas = new HashMap<>();
    private final Map<String, Set<WebSocket>> subscricoes = new HashMap<>();
    private final Map<String, Long> tokens = new HashMap<>();
    private final Map<String, PublicKey> chaves = new TreeMap<>();
    private final SecureRandom aleatorio = new SecureRandom();
    private final CompletableFuture<Void> iniciado = new CompletableFuture<>();
    private final Path ficheiro;
    private final Consumer<Publicacao> receber;
    private final Consumer<String> eventos;
    private final LongSupplier agora;

    PubSubHub(InetSocketAddress endereco, Path pasta, Consumer<Publicacao> receber, Consumer<String> eventos)
            throws IOException, GeneralSecurityException {
        this(endereco, pasta, receber, eventos, () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    PubSubHub(InetSocketAddress endereco, Path pasta, Consumer<Publicacao> receber, Consumer<String> eventos,
              LongSupplier agora) throws IOException, GeneralSecurityException {
        super(endereco, 2);
        this.receber = receber;
        this.eventos = eventos;
        this.agora = agora;
        Files.createDirectories(pasta);
        ficheiro = pasta.resolve("replicas.txt");
        if (Files.exists(ficheiro)) {
            for (String linha : Files.readAllLines(ficheiro, StandardCharsets.UTF_8)) {
                String[] campos = linha.split(";", -1);
                if (campos.length != 2 || !Wire.idValido(campos[0])
                        || chaves.putIfAbsent(campos[0], chave(campos[1])) != null) {
                    throw new IOException("replicas.txt inválido.");
                }
            }
        }
        setReuseAddr(true);
    }

    void iniciar() throws IOException, InterruptedException {
        start();
        try {
            iniciado.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Não foi possível arrancar o hub WebSocket.", e);
        }
    }

    public synchronized String gerarToken() {
        long instante = agora.getAsLong();
        tokens.values().removeIf(emitido -> instante - emitido >= Wire.TOKEN_VALIDADE_MS);
        String token;
        do {
            token = bytesAleatorios(32);
        } while (tokens.containsKey(token));
        tokens.put(token, instante);
        return token;
    }

    synchronized Set<String> idsRegistados() {
        return Set.copyOf(chaves.keySet());
    }

    synchronized boolean ativa(Publicacao publicacao) {
        // Um ACK recebido antes de uma quebra de rede continua válido. A
        // revogação, ao contrário da quebra, invalida os trabalhos em fila.
        return chaves.get(publicacao.id()) == publicacao.chave();
    }

    boolean remover(String id) throws IOException {
        var fechar = new ArrayList<WebSocket>();
        synchronized (this) {
            if (!chaves.containsKey(id)) {
                return false;
            }
            var propostas = new TreeMap<>(chaves);
            propostas.remove(id);
            gravarChaves(propostas);
            chaves.remove(id);
            // Uma prova em curso também é invalidada pela revogação.
            for (var e : Map.copyOf(sessoes).entrySet()) {
                if (id.equals(e.getValue().id)) {
                    limpar(e.getKey());
                    fechar.add(e.getKey());
                }
            }
        }
        fechar.forEach(ligacao -> ligacao.close(1008, "revogada"));
        return true;
    }

    public synchronized int publish(String topico, String mensagem) {
        int envios = 0;
        for (WebSocket ligacao : Set.copyOf(subscricoes.getOrDefault(topico, Set.of()))) {
            if (ligacao.isOpen()) {
                try {
                    ligacao.send(mensagem);
                    envios++;
                } catch (WebsocketNotConnectedException e) {
                    limpar(ligacao);
                }
            }
        }
        return envios;
    }

    @Override
    public synchronized void onOpen(WebSocket ligacao, ClientHandshake handshake) {
        sessoes.put(ligacao, new Sessao());
    }

    @Override
    public void onMessage(WebSocket ligacao, String mensagem) {
        WebSocket fechar = processar(ligacao, mensagem);
        if (fechar != null) {
            // WebSocketImpl pode invocar onClose com o seu próprio bloqueio.
            fechar.close(1008, fechar == ligacao ? "protocolo" : "substituída");
        }
    }

    private synchronized WebSocket processar(WebSocket ligacao, String mensagem) {
        Sessao sessao = sessoes.get(ligacao);
        if (sessao == null) {
            return null;
        }
        try {
            switch (sessao.estado) {
                case NOVA -> desafio(ligacao, sessao, mensagem);
                case DESAFIADA -> {
                    return verificarProva(ligacao, sessao, mensagem);
                }
                case AUTENTICADA -> encaminhar(sessao, ligacao, mensagem);
            }
        } catch (IllegalArgumentException e) {
            // Nunca registar os argumentos do OLA, sobretudo o token.
            recusar(ligacao, sessao, "protocolo ou autorização");
            return ligacao;
        } catch (GeneralSecurityException e) {
            recusar(ligacao, sessao, "chave ou prova inválida");
            return ligacao;
        } catch (IOException e) {
            recusar(ligacao, sessao, "falha ao gravar replicas.txt");
            return ligacao;
        } catch (WebsocketNotConnectedException e) {
            limpar(ligacao);
        }
        return null;
    }

    @Override
    public void onMessage(WebSocket ligacao, ByteBuffer mensagem) {
        synchronized (this) {
            Sessao sessao = sessoes.get(ligacao);
            if (sessao == null) {
                return;
            }
            recusar(ligacao, sessao, "esperava mensagem de texto");
        }
        ligacao.close(1008, "protocolo");
    }

    private void desafio(WebSocket ligacao, Sessao sessao, String mensagem) throws GeneralSecurityException {
        String[] campos = mensagem.split(";", -1);
        if ((campos.length != 2 && campos.length != 4) || !campos[0].equals("OLA")
                || !Wire.idValido(campos[1])) {
            throw new IllegalArgumentException();
        }
        sessao.id = campos[1];
        PublicKey conhecida = chaves.get(sessao.id);
        if (conhecida == null) {
            if (campos.length != 4 || !tokenValido(campos[3])) {
                throw new IllegalArgumentException();
            }
            sessao.chave = chave(campos[2]);
            sessao.token = campos[3];
        } else {
            if (campos.length != 2) {
                throw new IllegalArgumentException();
            }
            sessao.chave = conhecida;
        }
        sessao.nonce = bytesAleatorios(32);
        sessao.estado = Estado.DESAFIADA;
        ligacao.send("DESAFIO;" + sessao.nonce);
    }

    private WebSocket verificarProva(WebSocket ligacao, Sessao sessao, String mensagem)
            throws GeneralSecurityException, IOException {
        String nonce = sessao.nonce;
        sessao.nonce = null;
        String[] campos = mensagem.split(";", -1);
        if (campos.length != 3 || !campos[0].equals("PROVA") || !campos[1].equals(sessao.id)) {
            throw new IllegalArgumentException();
        }
        Signature assinatura = Signature.getInstance("Ed25519");
        assinatura.initVerify(sessao.chave);
        assinatura.update(Wire.dadosDaProva(sessao.id, nonce));
        if (!assinatura.verify(Base64.getDecoder().decode(campos[2]))) {
            throw new GeneralSecurityException("Prova inválida.");
        }
        if (sessao.token != null) {
            // Revalidar sob o mesmo bloqueio cobre expiração e dois OLA com o mesmo token.
            if (chaves.containsKey(sessao.id) || !tokenValido(sessao.token)) {
                throw new IllegalArgumentException();
            }
            var propostas = new TreeMap<>(chaves);
            propostas.put(sessao.id, sessao.chave);
            gravarChaves(propostas);
            chaves.put(sessao.id, sessao.chave);
            tokens.remove(sessao.token);
            sessao.token = null;
        }
        WebSocket anterior = autenticadas.put(sessao.id, ligacao);
        if (anterior != null) {
            limpar(anterior);
        }
        sessao.estado = Estado.AUTENTICADA;
        return anterior;
    }

    private void encaminhar(Sessao sessao, WebSocket ligacao, String mensagem) {
        String[] campos = mensagem.split(";", 3);
        if (campos.length == 2 && campos[0].equals("SUB")
                && (campos[1].equals(Wire.TOPICO_CONTROLO) || campos[1].equals(Wire.topicoReplica(sessao.id)))) {
            subscricoes.computeIfAbsent(campos[1], t -> new HashSet<>()).add(ligacao);
        } else if (campos.length == 3 && campos[0].equals("PUB") && campos[1].equals(Wire.TOPICO_LIDER)) {
            receber.accept(new Publicacao(sessao.id, campos[2], sessao.chave));
        } else {
            throw new IllegalArgumentException();
        }
    }

    private boolean tokenValido(String token) {
        Long emitido = tokens.get(token);
        return emitido != null && agora.getAsLong() - emitido < Wire.TOKEN_VALIDADE_MS;
    }

    private String bytesAleatorios(int tamanho) {
        byte[] bytes = new byte[tamanho];
        aleatorio.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static PublicKey chave(String b64) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
    }

    private void gravarChaves(Map<String, PublicKey> propostas) throws IOException {
        var texto = new StringBuilder();
        propostas.forEach((id, chave) -> texto.append(id).append(';')
                .append(Base64.getEncoder().encodeToString(chave.getEncoded())).append('\n'));
        ArquivosAtomicos.publicar(ficheiro, texto.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void recusar(WebSocket ligacao, Sessao sessao, String motivo) {
        eventos.accept("ERRO protocolo " + (sessao.id == null ? "-" : sessao.id) + " " + motivo);
        limpar(ligacao);
    }

    private void limpar(WebSocket ligacao) {
        Sessao sessao = sessoes.remove(ligacao);
        if (sessao != null && sessao.id != null) {
            autenticadas.remove(sessao.id, ligacao);
        }
        subscricoes.values().removeIf(ligacoes -> {
            ligacoes.remove(ligacao);
            return ligacoes.isEmpty();
        });
    }

    @Override
    public synchronized void onClose(WebSocket ligacao, int codigo, String motivo, boolean remoto) {
        limpar(ligacao);
    }

    @Override
    public void onError(WebSocket ligacao, Exception erro) {
        if (ligacao == null) {
            iniciado.completeExceptionally(erro);
        }
        eventos.accept("ERRO WebSocket " + erro.getClass().getSimpleName());
    }

    @Override
    public void onStart() {
        iniciado.complete(null);
    }
}
