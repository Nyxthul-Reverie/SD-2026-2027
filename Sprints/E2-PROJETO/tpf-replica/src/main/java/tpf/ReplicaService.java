package tpf;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ReplicaService implements AutoCloseable {
    private static final Duration TIMEOUT_LIGACAO = Duration.ofSeconds(10);
    private static final int MARCA_PING = 0x545046;
    private final String id;
    private final URI url;
    private final long quota;
    private final Path pasta;
    private final Path eventos;
    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(TIMEOUT_LIGACAO).build();
    private final ScheduledExecutorService temporizador = Executors.newSingleThreadScheduledExecutor();
    private final CountDownLatch parar = new CountDownLatch(1);
    private volatile String token;
    private volatile Ligacao atual;
    private KeyPair chaves;
    private long usados;

    ReplicaService(String id, URI url, long quota, String token, Path raiz) {
        if (!Wire.idValido(id) || quota < 0
                || !("ws".equalsIgnoreCase(url.getScheme()) || "wss".equalsIgnoreCase(url.getScheme()))
                || url.getHost() == null) {
            throw new IllegalArgumentException("Identificador, URL ou quota inválidos.");
        }
        this.id = id;
        this.url = url;
        this.quota = quota;
        this.token = token;
        pasta = raiz.resolve("dados-" + id);
        eventos = raiz.resolve("eventos-" + id + ".txt");
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException, InterruptedException {
        if (args.length != 3 && args.length != 4) {
            throw new IllegalArgumentException("Uso: ReplicaService <id> <url> <quotaBytes> [token]");
        }
        try (var replica = new ReplicaService(args[0], URI.create(args[1]), Long.parseLong(args[2]),
                args.length == 4 ? args[3] : null, Path.of("."))) {
            Runtime.getRuntime().addShutdownHook(new Thread(replica::close));
            replica.executar();
        }
    }

    public void executar() throws IOException, GeneralSecurityException, InterruptedException {
        Files.createDirectories(pasta);
        try (FileChannel canal = FileChannel.open(pasta.resolve(".replica.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = canal.tryLock()) {
            if (lock == null) {
                throw new IOException("Já existe um processo a usar os dados de " + id);
            }
            chaves = carregarChaves();
            try (var ficheiros = Files.list(pasta)) {
                for (Path bin : ficheiros.filter(ReplicaService::eBin).toList()) {
                    usados = Math.addExact(usados, Files.size(bin));
                }
            }
            cicloDeLigacoes();
        }
    }

    private void cicloDeLigacoes() throws InterruptedException {
        long espera = Wire.BACKOFF_MIN_MS;
        while (parar.getCount() > 0) {
            var ligacao = new Ligacao();
            atual = ligacao;
            if (parar.getCount() == 0) {
                atual = null;
                break;
            }
            try {
                cliente.newWebSocketBuilder().connectTimeout(TIMEOUT_LIGACAO)
                        .buildAsync(url, ligacao).get();
                if (ligacao.fim.get()) {
                    espera = Wire.BACKOFF_MIN_MS;
                }
            } catch (ExecutionException e) {
                ligacao.falhar("ligação " + e.getCause().getClass().getSimpleName());
            } finally {
                ligacao.encerrar();
                atual = null;
            }
            if (parar.getCount() == 0) {
                return;
            }
            evento("RELIGAR " + espera);
            parar.await(espera, TimeUnit.MILLISECONDS);
            espera = Math.min(espera * 2, Wire.BACKOFF_MAX_MS);
        }
    }

    private KeyPair carregarChaves() throws IOException, GeneralSecurityException {
        Path destino = pasta.resolve("chaves");
        if (Files.notExists(destino)) {
            Path tmp = Files.createTempDirectory(pasta, ".chaves-");
            try {
                KeyPair par = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                var permissoes = ArquivosAtomicos.permissoesDaChave(tmp);
                ArquivosAtomicos.publicar(tmp.resolve("privada.pk8"), par.getPrivate().getEncoded(), permissoes);
                ArquivosAtomicos.publicar(tmp.resolve("publica.x509"), par.getPublic().getEncoded(), permissoes);
                // A pasta é publicada inteira para nunca arrancar com meio par de chaves.
                Files.move(tmp, destino, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                if (Files.exists(tmp)) {
                    Files.deleteIfExists(tmp.resolve("privada.pk8"));
                    Files.deleteIfExists(tmp.resolve("publica.x509"));
                    Files.deleteIfExists(tmp);
                }
            }
        }
        KeyFactory fabrica = KeyFactory.getInstance("Ed25519");
        var privada = new PKCS8EncodedKeySpec(Files.readAllBytes(destino.resolve("privada.pk8")));
        var publica = new X509EncodedKeySpec(Files.readAllBytes(destino.resolve("publica.x509")));
        return new KeyPair(fabrica.generatePublic(publica), fabrica.generatePrivate(privada));
    }

    private static boolean eBin(Path p) {
        return Files.isRegularFile(p) && p.getFileName().toString().endsWith(".bin");
    }

    private List<String> inventario() throws IOException {
        try (var ficheiros = Files.list(pasta)) {
            return ficheiros.filter(ReplicaService::eBin)
                    .map(p -> p.getFileName().toString().replaceFirst("\\.bin$", ""))
                    .filter(Wire::chunkIdValido)
                    .filter(chunk -> Files.isRegularFile(pasta.resolve(chunk + ".hdr")))
                    .sorted().toList();
        }
    }

    private void evento(String mensagem) {
        try {
            ArquivosAtomicos.evento(eventos, mensagem);
        } catch (IOException e) {
            System.err.println("ERRO a gravar eventos da réplica " + id);
        }
    }

    @Override
    public void close() {
        parar.countDown();
        Ligacao ligacao = atual;
        if (ligacao != null) {
            ligacao.encerrar();
        }
        temporizador.shutdownNow();
    }

    private final class Ligacao implements WebSocket.Listener {
        private final CompletableFuture<Boolean> fim = new CompletableFuture<>();
        private final StringBuilder mensagem = new StringBuilder();
        private WebSocket socket;
        private ScheduledFuture<?> limite;
        private ScheduledFuture<?> heartbeats;
        private boolean provaEnviada;
        private boolean ligada;

        @Override
        public synchronized void onOpen(WebSocket ws) {
            socket = ws;
            if (fim.isDone() || parar.getCount() == 0) {
                ws.abort();
                return;
            }
            try {
                limite = temporizador.schedule(() -> falhar("tempo limite de autenticação"),
                        TIMEOUT_LIGACAO.toMillis(), TimeUnit.MILLISECONDS);
                String ola = "OLA;" + id;
                String registo = token;
                if (registo != null) {
                    ola += ";" + Base64.getEncoder().encodeToString(chaves.getPublic().getEncoded())
                            + ";" + registo;
                }
                enviar(ola);
                ws.request(1);
            } catch (RuntimeException e) {
                falhar("envio de OLA");
            }
        }

        @Override
        public synchronized CompletableFuture<?> onText(WebSocket ws, CharSequence dados, boolean ultimo) {
            if (fim.isDone()) {
                return null;
            }
            mensagem.append(dados);
            if (ultimo) {
                String texto = mensagem.toString();
                mensagem.setLength(0);
                try {
                    if (!provaEnviada) {
                        responderDesafio(texto);
                    } else {
                        store(texto);
                    }
                } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
                    if (!provaEnviada) {
                        falhar("desafio ou estabelecimento inválido");
                    } else {
                        evento("ERRO STORE " + e.getClass().getSimpleName());
                    }
                } catch (RuntimeException e) {
                    falhar("WebSocket " + e.getClass().getSimpleName());
                }
            }
            ws.request(1);
            return null;
        }

        private void responderDesafio(String texto) throws GeneralSecurityException, IOException {
            String[] campos = texto.split(";", -1);
            if (campos.length != 2 || !campos[0].equals("DESAFIO")
                    || Base64.getDecoder().decode(campos[1]).length != 32) {
                throw new IllegalArgumentException("Desafio inválido.");
            }
            String chunks = String.join(",", inventario());
            Signature assinatura = Signature.getInstance("Ed25519");
            assinatura.initSign(chaves.getPrivate());
            assinatura.update(Wire.dadosDaProva(id, campos[1]));
            enviar("PROVA;" + id + ";" + Base64.getEncoder().encodeToString(assinatura.sign()));
            enviar("SUB;" + Wire.topicoReplica(id));
            enviar("SUB;" + Wire.TOPICO_CONTROLO);
            publicar("INVENTARIO;" + id + ";" + chunks);
            heartbeat();
            // O pong marca que o hub processou as mensagens anteriores, sem um comando extra.
            aguardar(socket.sendPing(ByteBuffer.allocate(Integer.BYTES).putInt(MARCA_PING).flip()));
            provaEnviada = true;
        }

        @Override
        public synchronized CompletableFuture<?> onPong(WebSocket ws, ByteBuffer dados) {
            if (!fim.isDone() && provaEnviada && !ligada
                    && dados.remaining() == Integer.BYTES && dados.getInt() == MARCA_PING) {
                ligada = true;
                token = null;
                limite.cancel(false);
                evento("LIGADO");
                heartbeats = temporizador.scheduleWithFixedDelay(this::enviarHeartbeat,
                        Wire.PERIODO_HB_MS, Wire.PERIODO_HB_MS, TimeUnit.MILLISECONDS);
            }
            ws.request(1);
            return null;
        }

        private synchronized void enviarHeartbeat() {
            if (fim.isDone()) {
                return;
            }
            try {
                heartbeat();
            } catch (RuntimeException e) {
                falhar("envio de HB");
            }
        }

        private void heartbeat() {
            publicar("HB;" + id + ";" + usados + ";" + quota);
        }

        private void store(String texto) throws IOException {
            String[] campos = texto.split(";", -1);
            if (campos.length != 4 || !campos[0].equals("STORE") || !Wire.chunkIdValido(campos[1])) {
                throw new IllegalArgumentException("STORE inválido.");
            }
            String chunk = campos[1];
            Path bin = pasta.resolve(chunk + ".bin");
            Path hdr = pasta.resolve(chunk + ".hdr");
            boolean guardado = Files.isRegularFile(bin);
            if (guardado && Files.isRegularFile(hdr)) {
                if (!Wire.hash(Files.readAllBytes(bin)).equals(chunk)) {
                    throw new IOException("O chunk local está corrompido.");
                }
                publicar("ACK;" + id + ";" + chunk + ";" + chunk);
                return;
            }
            byte[] bytes = Base64.getDecoder().decode(campos[3]);
            if (!guardado && bytes.length > quota - usados) {
                evento("QUOTA " + chunk);
                return;
            }
            if (!Wire.hash(bytes).equals(chunk) || (guardado && !Wire.hash(Files.readAllBytes(bin)).equals(chunk))) {
                throw new IllegalArgumentException("SHA-256 incorreto.");
            }
            // O .bin é publicado por último e só depois se confirma a escrita.
            ArquivosAtomicos.publicar(hdr, Base64.getDecoder().decode(campos[2]));
            if (!guardado) {
                ArquivosAtomicos.publicar(bin, bytes);
                usados += bytes.length;
            }
            evento("STORE " + chunk);
            publicar("ACK;" + id + ";" + chunk + ";" + chunk);
        }

        private void publicar(String texto) {
            enviar("PUB;" + Wire.TOPICO_LIDER + ";" + texto);
        }

        private void enviar(String texto) {
            aguardar(socket.sendText(texto, true));
        }

        private void aguardar(CompletableFuture<WebSocket> envio) {
            try {
                envio.get(TIMEOUT_LIGACAO.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public synchronized CompletableFuture<?> onClose(WebSocket ws, int codigo, String motivo) {
            if (!ligada && !fim.isDone()) {
                evento("ERRO ligação recusada");
            }
            terminar();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable erro) {
            falhar("WebSocket " + erro.getClass().getSimpleName());
        }

        synchronized void falhar(String motivo) {
            if (!fim.isDone()) {
                evento("ERRO " + motivo);
                encerrar();
            }
        }

        synchronized void encerrar() {
            terminar();
            if (socket != null) {
                socket.abort();
            }
        }

        private void terminar() {
            if (fim.isDone()) {
                return;
            }
            if (limite != null) {
                limite.cancel(false);
            }
            if (heartbeats != null) {
                heartbeats.cancel(false);
            }
            evento("DESLIGADO");
            fim.complete(ligada);
        }
    }
}
