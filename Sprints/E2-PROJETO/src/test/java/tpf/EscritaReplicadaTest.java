package tpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class EscritaReplicadaTest {
    private static final InetSocketAddress LOCAL = new InetSocketAddress("127.0.0.1", 0);
    private static final long QUOTA = 8_000_000;
    private static final String TEXTO = "0123456789abcdef".repeat(24);

    @TempDir
    Path raiz;

    @Test
    void rajadaCifradaDurabilidadeSubreplicacaoEReinicio() throws Exception {
        Path dados = raiz.resolve("lider");
        ScheduledExecutorService selagem = Executors.newSingleThreadScheduledExecutor();
        String id;
        try (var lider = new EscritaReplicada(dados, LOCAL)) {
            lider.iniciar();
            var arquivo = new ArquivoLocal(dados, selagem, lider::selado);
            URI url = url(lider.porto());
            try (var r1 = novaReplica(url, "r1", QUOTA, lider.gerarToken());
                 var r2 = novaReplica(url, "r2", QUOTA, lider.gerarToken());
                 var r3 = novaReplica(url, "r3", QUOTA, lider.gerarToken())) {
                aguardar(() -> estado(lider, "r1", "VIVA") && estado(lider, "r2", "VIVA")
                        && estado(lider, "r3", "VIVA"), 5_000, "três réplicas vivas");
                for (int i = 1; i <= 50; i++) {
                    assertEquals(i, arquivo.registar(1, 1, "rajada " + i + "/50 " + TEXTO));
                }
                aguardar(() -> totais(lider, 0, 1, 0), Wire.SELAGEM_MS + 5000, "rajada com três cópias");
                var primeira = new Catalog(dados).entradas().get(0);
                id = primeira.chunkId();
                assertEquals(1, primeira.lsnDe());
                assertEquals(50, primeira.lsnAte());
                assertEquals(Set.of("r1", "r2", "r3"), primeira.replicas());
                byte[] cifrado = Files.readAllBytes(dados.resolve("selados").resolve(id + ".bin"));
                for (String replica : primeira.replicas()) {
                    Path pasta = raiz.resolve("dados-" + replica);
                    Set<String> ficheiros;
                    try (var caminhos = Files.list(pasta)) {
                        ficheiros = new HashSet<>(caminhos.map(p -> p.getFileName().toString()).toList());
                    }
                    assertEquals(Set.of(".replica.lock", "chaves", id + ".hdr", id + ".bin"), ficheiros);
                    byte[] copia = Files.readAllBytes(pasta.resolve(id + ".bin"));
                    assertArrayEquals(cifrado, copia);
                    assertArrayEquals(Files.readAllBytes(dados.resolve("selados").resolve(id + ".hdr")),
                            Files.readAllBytes(pasta.resolve(id + ".hdr")));
                    assertEquals(id, Wire.hash(copia));
                    assertFalse(new String(copia, StandardCharsets.ISO_8859_1).contains(TEXTO));
                    assertPermissoes(pasta.resolve("chaves/privada.pk8"));
                    assertPermissoes(pasta.resolve("chaves/publica.x509"));
                }

                r3.close();
                aguardar(() -> estado(lider, "r3", "SUSPEITA"), Wire.SUSPEITA_MS + 2500, "r3 SUSPEITA");
                aguardar(() -> estado(lider, "r3", "MORTA"), Wire.MORTA_MS + 1500, "r3 MORTA");
                assertEquals(51, arquivo.registar(1, 1, "duas réplicas"));
                aguardar(() -> totais(lider, 0, 2, 1), Wire.SELAGEM_MS + 5000, "2/3 DURAVEL");
                assertEquals(Set.of("r1", "r2"), new Catalog(dados).entradas().get(1).replicas());

                assertTrue(lider.remover("r2").get(3, TimeUnit.SECONDS));
                assertFalse(lider.remover("r2").get(3, TimeUnit.SECONDS));
                assertEquals(52, arquivo.registar(1, 1, "uma réplica"));
                aguardar(() -> totais(lider, 1, 2, 2)
                                && lider.estado().join().stream()
                                .anyMatch(l -> l.startsWith("r1 · ") && l.endsWith(" · 3 chunks")),
                        Wire.SELAGEM_MS + 5000, "1/3 SELADO");
                var terceira = new Catalog(dados).entradas().get(2);
                assertEquals(Set.of("r1"), terceira.replicas());
                assertTrue(evento(dados, "SUBREPLICADO " + terceira.chunkId() + " 1/3"));
                aguardar(() -> evento(dados, "RONDA " + terceira.chunkId() + " " + Wire.MAX_RONDAS),
                        Wire.ACK_TIMEOUT_MS * (long) Wire.MAX_RONDAS + 2000, "limite de rondas");

                try (var retomada = novaReplica(url, "r3", QUOTA, null)) {
                    aguardar(() -> estado(lider, "r3", "VIVA")
                            && evento(dados, "INVENTARIO r3 1"), 6000, "r3 regressa com inventário");
                }
            }
        } finally {
            selagem.shutdownNow();
        }

        try (var reiniciado = new EscritaReplicada(dados, LOCAL)) {
            reiniciado.iniciar();
            var novasSelagens = Executors.newSingleThreadScheduledExecutor();
            try {
                var arquivo = new ArquivoLocal(dados, novasSelagens, reiniciado::selado);
                assertTrue(totais(reiniciado, 1, 2, 2));
                assertEquals(id, new Catalog(dados).entradas().get(0).chunkId());
                assertEquals(53, arquivo.registar(1, 1, "reinício"));
            } finally {
                novasSelagens.shutdownNow();
            }
        }
    }

    @Test
    void retomaUmChunkDoE202AindaSemCatalogo() throws Exception {
        Path dados = raiz.resolve("lider");
        var selagem = Executors.newSingleThreadScheduledExecutor();
        try {
            var original = new ArquivoLocal(dados, selagem);
            original.registar(1, 1, "x".repeat(Wire.CHUNK_MAX_BYTES));
            assertFalse(Files.exists(dados.resolve("catalogo.txt")));

            try (var lider = new EscritaReplicada(dados, LOCAL)) {
                lider.iniciar();
                URI url = url(lider.porto());
                try (var r1 = novaReplica(url, "r1", QUOTA, lider.gerarToken());
                     var r2 = novaReplica(url, "r2", QUOTA, lider.gerarToken());
                     var r3 = novaReplica(url, "r3", QUOTA, lider.gerarToken())) {
                    aguardar(() -> estado(lider, "r1", "VIVA") && estado(lider, "r2", "VIVA")
                            && estado(lider, "r3", "VIVA"), 5000, "réplicas disponíveis");
                    new ArquivoLocal(dados, selagem, lider::selado);
                    aguardar(() -> totais(lider, 0, 1, 0), 5000, "chunk do E2-02 replicado");
                    var entrada = new Catalog(dados).entradas().get(0);
                    assertEquals(1, entrada.lsnDe());
                    assertEquals(1, entrada.lsnAte());
                    assertEquals(Set.of("r1", "r2", "r3"), entrada.replicas());
                }
            }
        } finally {
            selagem.shutdownNow();
        }
    }

    @Test
    void ackComHashErradoIdentidadeErradaOuSemStoreENovaRonda() throws Exception {
        Path dados = raiz.resolve("lider");
        var selagem = Executors.newSingleThreadScheduledExecutor();
        try (var lider = new EscritaReplicada(dados, LOCAL)) {
            lider.iniciar();
            URI url = url(lider.porto());
            try (var r1 = new ClienteTeste(url); var r2 = new ClienteTeste(url);
                 var r3 = new ClienteTeste(url); var r4 = new ClienteTeste(url)) {
                List<ClienteTeste> clientes = List.of(r1, r2, r3, r4);
                for (int i = 0; i < clientes.size(); i++) {
                    String id = "r" + (i + 1);
                    clientes.get(i).autenticar(id, ClienteTeste.chaves(), lider.gerarToken());
                    clientes.get(i).enviar("PUB;tpf-lider;HB;" + id + ";0;" + QUOTA);
                }
                aguardar(() -> estado(lider, "r1", "VIVA") && estado(lider, "r2", "VIVA")
                        && estado(lider, "r3", "VIVA") && estado(lider, "r4", "VIVA"), 5000, "quatro réplicas vivas");
                var arquivo = new ArquivoLocal(dados, selagem, lider::selado);
                arquivo.registar(1, 1, "Injeção controlada de ACK incorreto");
                Pattern store = Pattern.compile("STORE ([0-9a-f]{64}) -> (r[1-4])");
                aguardar(() -> eventos(dados, store).size() >= 3, Wire.SELAGEM_MS + 5000, "primeira ronda");
                List<String> escolhidas = eventos(dados, store).stream().map(linha -> {
                    var match = store.matcher(linha);
                    assertTrue(match.matches());
                    return match.group(2);
                }).toList();
                assertEquals(3, escolhidas.size());
                String chunk = new Catalog(dados).entradas().get(0).chunkId();
                String falha = escolhidas.get(0);
                String boa1 = escolhidas.get(1);
                String boa2 = escolhidas.get(2);
                String extra = List.of("r1", "r2", "r3", "r4").stream()
                        .filter(r -> !escolhidas.contains(r)).findFirst().orElseThrow();
                String storeFalha = cliente(clientes, falha).receber();
                assertTrue(storeFalha.startsWith("STORE;" + chunk + ";"));
                cliente(clientes, falha).enviar("PUB;tpf-lider;ACK;" + falha + ";" + chunk + ";" + "0".repeat(64));
                cliente(clientes, falha).enviar("PUB;tpf-lider;ACK;" + boa1 + ";" + chunk + ";" + chunk);
                cliente(clientes, extra).enviar("PUB;tpf-lider;ACK;" + extra + ";" + chunk + ";" + chunk);
                confirmarStoreDeTeste(clientes, boa1, chunk);
                confirmarStoreDeTeste(clientes, boa2, chunk);
                aguardar(() -> totais(lider, 0, 1, 1), 3000, "duas confirmações válidas");
                assertEquals(Set.of(boa1, boa2), new Catalog(dados).copias(chunk));
                confirmarStoreDeTeste(clientes, extra, chunk);
                aguardar(() -> totais(lider, 0, 1, 0), 5000, "nova ronda completa três cópias");
                assertEquals(Set.of(boa1, boa2, extra), new Catalog(dados).copias(chunk));
                assertTrue(evento(dados, "ACK-REJEITADO " + chunk + " " + falha + " hash"));
                assertTrue(evento(dados, "ACK-REJEITADO " + chunk + " " + falha + " identidade"));
                assertTrue(evento(dados, "ACK-REJEITADO " + chunk + " " + extra + " STORE sem confirmação pendente"));
                assertTrue(evento(dados, "RONDA " + chunk + " 2"));
            }
        } finally {
            selagem.shutdownNow();
        }
    }

    @Test
    void storeValidaQuotaHashFicheirosEAckIdempotente() throws Exception {
        Path dados = raiz.resolve("lider");
        Path pastaReplica = raiz.resolve("dados-r1");
        byte[] bin = {4, 5, 6, 7};
        String chunk = Wire.hash(bin);
        byte[] cabecalho = {1, 2, 3};
        var publicacoes = new LinkedBlockingQueue<String>();
        var hub = new PubSubHub(LOCAL, dados, pub -> {
            if (pub.mensagem().startsWith("ACK;")) {
                boolean gravado = Files.exists(pastaReplica.resolve(chunk + ".hdr"))
                        && Files.exists(pastaReplica.resolve(chunk + ".bin"));
                publicacoes.add(pub.mensagem() + ";" + gravado);
            }
        }, msg -> { });
        try {
            hub.iniciar();
            try (var r1 = novaReplica(url(hub.getPort()), "r1", bin.length, hub.gerarToken())) {
                aguardar(() -> logReplica("r1").contains("LIGADO"), 5000, "registo da réplica");
                byte[] grande = new byte[5];
                String grandeId = Wire.hash(grande);
                enviarStore(hub, grandeId, cabecalho, grande);
                aguardar(() -> logReplica("r1").contains("QUOTA " + grandeId), 5000, "quota insuficiente");
                assertFalse(Files.exists(pastaReplica.resolve(grandeId + ".bin")));
                enviarStore(hub, chunk, cabecalho, new byte[]{1, 1, 1, 1});
                aguardar(() -> logReplica("r1").contains("ERRO STORE"), 5000, "hash errado");
                assertFalse(Files.exists(pastaReplica.resolve(chunk + ".bin")));
                assertTrue(publicacoes.isEmpty());

                enviarStore(hub, chunk, cabecalho, bin);
                String esperado = "ACK;r1;" + chunk + ";" + chunk + ";true";
                assertEquals(esperado, publicacoes.poll(5, TimeUnit.SECONDS));
                assertArrayEquals(bin, Files.readAllBytes(pastaReplica.resolve(chunk + ".bin")));
                assertArrayEquals(cabecalho, Files.readAllBytes(pastaReplica.resolve(chunk + ".hdr")));
                for (String ext : List.of(".bin", ".hdr")) {
                    Files.setLastModifiedTime(pastaReplica.resolve(chunk + ext), FileTime.fromMillis(1_000_000_000_000L));
                }
                enviarStore(hub, chunk, cabecalho, bin);
                assertEquals(esperado, publicacoes.poll(5, TimeUnit.SECONDS));
                for (String ext : List.of(".bin", ".hdr")) {
                    assertEquals(1_000_000_000_000L, Files.getLastModifiedTime(pastaReplica.resolve(chunk + ext)).toMillis());
                }
            }
        } finally {
            hub.stop(1000);
        }
    }

    @Test
    void backoffExponencialERepostoAposReligacao() throws Exception {
        Path dados = raiz.resolve("lider");
        var hub = new PubSubHub(LOCAL, dados, msg -> { }, msg -> { });
        hub.iniciar();
        int porto = hub.getPort();
        var endereco = new InetSocketAddress("127.0.0.1", porto);
        int minimo = Wire.BACKOFF_MIN_MS;
        long dobro = Math.min(2L * minimo, Wire.BACKOFF_MAX_MS);
        long quadruplo = Math.min(4L * minimo, Wire.BACKOFF_MAX_MS);
        try (var r1 = novaReplica(url(porto), "r1", QUOTA, hub.gerarToken())) {
            aguardar(() -> vezesLigado("r1") == 1, 5000, "ligação inicial");
            hub.stop(1000);
            aguardar(() -> logReplica("r1").contains("RELIGAR " + quadruplo + "\n"),
                    minimo + dobro + 5000, "backoff exponencial");
            var reiniciado = new PubSubHub(endereco, dados, msg -> { }, msg -> { });
            try {
                reiniciado.iniciar();
                aguardar(() -> vezesLigado("r1") == 2, quadruplo + 5000, "religação ao líder");
            } finally {
                reiniciado.stop(1000);
            }
            aguardar(() -> logReplica("r1").substring(logReplica("r1").lastIndexOf(";LIGADO"))
                            .contains("RELIGAR " + minimo + "\n"), 3000, "backoff volta ao mínimo");
            List<Long> esperas = logReplica("r1").lines().filter(l -> l.contains(";RELIGAR "))
                    .map(l -> Long.parseLong(l.split("RELIGAR ")[1])).toList();
            assertEquals(List.of((long) minimo, dobro, quadruplo, (long) minimo), esperas.subList(0, 4));
            System.out.println("BACKOFF ms=" + esperas);
        } finally {
            hub.stop(1000);
        }
    }

    private static void enviarStore(PubSubHub hub, String id, byte[] hdr, byte[] bin) {
        var b64 = Base64.getEncoder();
        assertEquals(1, hub.publish("tpf-r-r1", "STORE;" + id + ";" + b64.encodeToString(hdr)
                + ";" + b64.encodeToString(bin)));
    }

    private void confirmarStoreDeTeste(List<ClienteTeste> clientes, String id, String chunk) throws Exception {
        ClienteTeste cliente = cliente(clientes, id);
        String[] campos = cliente.receber().split(";", -1);
        assertEquals("STORE", campos[0]);
        assertEquals(chunk, campos[1]);
        var b64 = Base64.getDecoder();
        byte[] bin = b64.decode(campos[3]);
        assertEquals(chunk, Wire.hash(bin));
        Path pasta = Files.createDirectories(raiz.resolve("simulada-" + id));
        ArquivosAtomicos.publicar(pasta.resolve(chunk + ".hdr"), b64.decode(campos[2]));
        ArquivosAtomicos.publicar(pasta.resolve(chunk + ".bin"), bin);
        cliente.enviar("PUB;tpf-lider;ACK;" + id + ";" + chunk + ";" + chunk);
    }

    private static ClienteTeste cliente(List<ClienteTeste> clientes, String id) {
        return clientes.get(Integer.parseInt(id.substring(1)) - 1);
    }

    private static void assertPermissoes(Path path) throws IOException {
        var store = Files.getFileStore(path);
        if (store.supportsFileAttributeView("posix")) {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(path));
        } else {
            var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            assertEquals(Set.of(acl.getOwner()), new HashSet<>(acl.getAcl().stream()
                    .map(e -> e.principal()).toList()));
        }
    }

    private static List<String> eventos(Path dados, Pattern padrao) {
        try {
            Path ficheiro = dados.resolve("eventos-lider.txt");
            if (Files.notExists(ficheiro)) {
                return List.of();
            }
            return Files.readAllLines(ficheiro).stream().map(l -> l.substring(l.indexOf(';') + 1))
                    .filter(l -> padrao.matcher(l).matches()).toList();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static boolean evento(Path dados, String padrao) {
        return !eventos(dados, Pattern.compile(padrao)).isEmpty();
    }

    private String logReplica(String id) {
        try {
            Path ficheiro = raiz.resolve("eventos-" + id + ".txt");
            return Files.exists(ficheiro) ? Files.readString(ficheiro) : "";
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private long vezesLigado(String id) {
        return logReplica(id).lines().filter(l -> l.endsWith(";LIGADO")).count();
    }

    private static boolean estado(EscritaReplicada lider, String id, String estado) {
        return lider.estado().join().stream().anyMatch(l -> l.startsWith(id + " · " + estado + " ·"));
    }

    private static boolean totais(EscritaReplicada lider, int selados, int duraveis, int sub) {
        return lider.estado().join().get(0).equals("SELADO " + selados + " · DURAVEL " + duraveis
                + " · SUBREPLICADO " + sub);
    }

    private static URI url(int porto) {
        return URI.create("ws://127.0.0.1:" + porto);
    }

    private static void aguardar(BooleanSupplier condicao, long ms, String descricao) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms);
        while (!condicao.getAsBoolean()) {
            assertTrue(System.nanoTime() < limite, descricao);
            Thread.sleep(20);
        }
    }

    private ReplicaEmTeste novaReplica(URI url, String id, long quota, String token) {
        return new ReplicaEmTeste(new ReplicaService(id, url, quota, token, raiz));
    }

    private static final class ReplicaEmTeste implements AutoCloseable {
        private final ReplicaService replica;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Future<?> tarefa;

        ReplicaEmTeste(ReplicaService replica) {
            this.replica = replica;
            tarefa = executor.submit(() -> {
                replica.executar();
                return null;
            });
        }

        @Override
        public void close() throws Exception {
            replica.close();
            try {
                tarefa.get(12, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
