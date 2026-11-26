package tpf;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class EscritaReplicada implements AutoCloseable {
    private static final class Escrita {
        final String chunkId;
        final long tamanho;
        final String store;
        final Set<String> tentadas = new HashSet<>();
        final Set<String> pendentes = new HashSet<>();
        int ronda;
        ScheduledFuture<?> timeout;

        Escrita(ArquivoLocal.ChunkSelado chunk, String store) {
            chunkId = chunk.id();
            tamanho = chunk.tamanho();
            this.store = store;
        }
    }

    private final ScheduledExecutorService fila = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, Escrita> escritas = new HashMap<>();
    private final Catalog catalogo;
    private final ReplicaRegistry registry;
    private final PubSubHub hub;
    private final Path selados;
    private final Path eventos;

    EscritaReplicada(Path pasta, InetSocketAddress endereco) throws IOException, GeneralSecurityException {
        selados = pasta.resolve("selados");
        eventos = pasta.resolve("eventos-lider.txt");
        catalogo = new Catalog(pasta);
        registry = new ReplicaRegistry(this::evento);
        hub = new PubSubHub(endereco, pasta, pub -> fila.execute(() -> receber(pub)), this::evento);
        hub.idsRegistados().forEach(registry::conhecer);
    }

    void iniciar() throws IOException, InterruptedException {
        hub.iniciar();
        long intervalo = Math.max(1, Math.min(Wire.PERIODO_HB_MS, Wire.SUSPEITA_MS) / 4);
        fila.scheduleWithFixedDelay(() -> registry.atualizarEstados(agora()), intervalo,
                intervalo, TimeUnit.MILLISECONDS);
    }

    int porto() {
        return hub.getPort();
    }

    String gerarToken() {
        return hub.gerarToken();
    }

    CompletableFuture<Boolean> remover(String id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!hub.remover(id)) {
                    return false;
                }
                escritas.values().forEach(e -> e.pendentes.remove(id));
                registry.remover(id);
                return true;
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, fila);
    }

    CompletableFuture<List<String>> estado() {
        return CompletableFuture.supplyAsync(() -> {
            hub.idsRegistados().forEach(registry::conhecer);
            List<EntradaCatalogo> entradas = catalogo.entradas();
            long duraveis = entradas.stream().filter(e -> e.estado() == EntradaCatalogo.Estado.DURAVEL).count();
            long subreplicados = entradas.stream().filter(EntradaCatalogo::subreplicado).count();
            Map<String, Integer> porReplica = new HashMap<>();
            entradas.forEach(e -> e.replicas().forEach(id -> porReplica.merge(id, 1, Integer::sum)));
            var linhas = new ArrayList<String>();
            linhas.add("SELADO " + (entradas.size() - duraveis) + " · DURAVEL " + duraveis
                    + " · SUBREPLICADO " + subreplicados);
            for (var r : registry.estados()) {
                linhas.add(r.id() + " · " + r.estado() + " · " + r.usados() + "/" + r.quota()
                        + " bytes · " + porReplica.getOrDefault(r.id(), 0) + " chunks");
            }
            return linhas;
        }, fila);
    }

    // O callback da selagem só enfileira trabalho. Disco e rede da replicação
    // não retêm o monitor de ArquivoLocal durante a ronda ou a espera pelo ACK.
    void selado(ArquivoLocal.ChunkSelado chunk) {
        fila.execute(() -> iniciarEscrita(chunk, 1));
    }

    private void iniciarEscrita(ArquivoLocal.ChunkSelado chunk, int tentativa) {
        try {
            if (catalogo.contem(chunk.id())) {
                catalogo.adicionar(chunk.id(), chunk.lsnDe(), chunk.lsnAte(), chunk.tamanho());
                return;
            }
            var b64 = Base64.getEncoder();
            byte[] hdr = Files.readAllBytes(selados.resolve(chunk.id() + ".hdr"));
            byte[] bin = Files.readAllBytes(selados.resolve(chunk.id() + ".bin"));
            if (bin.length != chunk.tamanho() || !Wire.hash(bin).equals(chunk.id())) {
                throw new IOException("Chunk local alterado.");
            }
            catalogo.adicionar(chunk.id(), chunk.lsnDe(), chunk.lsnAte(), chunk.tamanho());
            var escrita = new Escrita(chunk, "STORE;" + chunk.id() + ";" + b64.encodeToString(hdr)
                    + ";" + b64.encodeToString(bin));
            escritas.put(chunk.id(), escrita);
            ronda(escrita);
        } catch (IOException e) {
            evento("ERRO replicação " + chunk.id() + " " + e.getMessage());
            if (tentativa < Wire.MAX_RONDAS) {
                fila.schedule(() -> iniciarEscrita(chunk, tentativa + 1), Wire.ACK_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS);
            }
        }
    }

    private void ronda(Escrita escrita) {
        int copias = catalogo.copias(escrita.chunkId).size();
        int objetivo = Wire.R - copias;
        escrita.ronda++;
        evento("RONDA " + escrita.chunkId + " " + escrita.ronda);
        List<String> escolhidas = registry.reserveAndChoose(escrita.chunkId, escrita.tamanho,
                objetivo, escrita.tentadas);
        int envios = 0;
        for (String id : escolhidas) {
            escrita.tentadas.add(id);
            if (hub.publish(Wire.topicoReplica(id), escrita.store) > 0) {
                escrita.pendentes.add(id);
                envios++;
                evento("STORE " + escrita.chunkId + " -> " + id);
            } else {
                registry.libertar(id, escrita.tamanho);
            }
        }
        if (envios < objetivo) {
            subreplicado(escrita, copias);
        }
        escrita.timeout = fila.schedule(() -> expirar(escrita), Wire.ACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void expirar(Escrita escrita) {
        escrita.pendentes.forEach(id -> registry.libertar(id, escrita.tamanho));
        escrita.pendentes.clear();
        int copias = catalogo.copias(escrita.chunkId).size();
        if (escrita.ronda < Wire.MAX_RONDAS) {
            ronda(escrita);
        } else {
            subreplicado(escrita, copias);
            escritas.remove(escrita.chunkId);
        }
    }

    private void receber(PubSubHub.Publicacao pub) {
        if (!hub.ativa(pub)) {
            return;
        }
        String id = pub.id();
        String[] campos = pub.mensagem().split(";", -1);
        try {
            switch (campos[0]) {
                case "HB" -> {
                    exigirOrigem(campos, 4, id);
                    registry.onHeartbeat(id, Long.parseLong(campos[2]), Long.parseLong(campos[3]), agora());
                }
                case "INVENTARIO" -> {
                    exigirOrigem(campos, 3, id);
                    int quantidade = 0;
                    if (!campos[2].isEmpty()) {
                        var chunks = new HashSet<String>();
                        for (String chunk : campos[2].split(",", -1)) {
                            if (!Wire.chunkIdValido(chunk) || !chunks.add(chunk)) {
                                throw new IllegalArgumentException("Inventário inválido.");
                            }
                        }
                        quantidade = chunks.size();
                    }
                    evento("INVENTARIO " + id + " " + quantidade);
                }
                case "ACK" -> confirmar(id, campos);
                default -> evento("ERRO mensagem desconhecida de " + id);
            }
        } catch (IllegalArgumentException e) {
            evento("ERRO mensagem inválida de " + id);
        }
    }

    private static void exigirOrigem(String[] campos, int tamanho, String id) {
        if (campos.length != tamanho || !campos[1].equals(id)) {
            throw new IllegalArgumentException("Identidade ou formato inválido.");
        }
    }

    private void confirmar(String id, String[] campos) {
        String chunkId = campos.length >= 3 && Wire.chunkIdValido(campos[2]) ? campos[2] : "-";
        if (campos.length != 4 || chunkId.equals("-")) {
            rejeitar(chunkId, id, "formato");
            return;
        }
        if (!campos[1].equals(id)) {
            rejeitar(chunkId, id, "identidade");
            return;
        }
        if (!campos[3].equals(chunkId)) {
            rejeitar(chunkId, id, "hash");
            return;
        }
        Escrita escrita = escritas.get(chunkId);
        if (escrita == null || !escrita.pendentes.contains(id)) {
            rejeitar(chunkId, id, "STORE sem confirmação pendente");
            return;
        }
        try {
            int anterior = catalogo.copias(chunkId).size();
            EntradaCatalogo entrada = catalogo.confirmar(chunkId, id);
            escrita.pendentes.remove(id);
            registry.confirmarReserva(id, escrita.tamanho);
            evento("ACK " + chunkId + " " + id);
            if (anterior < Wire.acksNecessarios() && entrada.estado() == EntradaCatalogo.Estado.DURAVEL) {
                evento("DURAVEL " + chunkId);
            }
            if (entrada.subreplicado()) {
                subreplicado(escrita, entrada.replicas().size());
            } else {
                escrita.timeout.cancel(false);
                escritas.remove(chunkId);
            }
        } catch (IOException e) {
            rejeitar(chunkId, id, "falha ao gravar catálogo");
        }
    }

    private void rejeitar(String chunkId, String id, String motivo) {
        evento("ACK-REJEITADO " + chunkId + " " + id + " " + motivo);
    }

    private void subreplicado(Escrita escrita, int copias) {
        evento("SUBREPLICADO " + escrita.chunkId + " " + copias + "/" + Wire.R);
    }

    private static long agora() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private void evento(String mensagem) {
        try {
            ArquivosAtomicos.evento(eventos, mensagem);
        } catch (IOException e) {
            System.err.println("ERRO a gravar eventos do líder: " + mensagem);
        }
    }

    @Override
    public void close() {
        try {
            hub.stop(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            fila.shutdownNow();
        }
    }
}
