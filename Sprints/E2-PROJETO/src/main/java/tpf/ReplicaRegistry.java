package tpf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class ReplicaRegistry {
    public record EstadoReplica(String id, HeartbeatMonitor.Estado estado, long quota,
                                long usados, long reservados, long ultimoHeartbeat) {
    }

    private static final class Replica {
        final HeartbeatMonitor monitor = new HeartbeatMonitor();
        long quota;
        long usados;
        long reservados;

        long livre() {
            return Math.max(Long.MIN_VALUE + reservados, quota - usados) - reservados;
        }
    }

    private final Map<String, Replica> replicas = new HashMap<>();
    private final Consumer<String> eventos;

    public ReplicaRegistry(Consumer<String> eventos) {
        this.eventos = eventos;
    }

    synchronized void conhecer(String id) {
        replicas.computeIfAbsent(id, chave -> new Replica());
    }

    public synchronized void onHeartbeat(String id, long usados, long quota, long agora) {
        if (usados < 0 || quota < 0) {
            throw new IllegalArgumentException("Ocupação ou quota negativa.");
        }
        Replica replica = replicas.computeIfAbsent(id, chave -> new Replica());
        var anterior = replica.monitor.estado();
        replica.usados = usados;
        replica.quota = quota;
        replica.monitor.onHeartbeat(agora);
        registarTransicao(id, anterior, replica.monitor.estado());
    }

    public synchronized void atualizarEstados(long agora) {
        replicas.forEach((id, replica) -> {
            var anterior = replica.monitor.estado();
            replica.monitor.atualizarEstado(agora);
            registarTransicao(id, anterior, replica.monitor.estado());
        });
    }

    // O mesmo monitor protege a leitura do espaço livre e o débito das reservas.
    public synchronized List<String> reserveAndChoose(String chunkId, long tamanho, int count,
                                                       Set<String> exclude) {
        if (tamanho <= 0 || count < 0) {
            throw new IllegalArgumentException("Tamanho ou número de réplicas inválido.");
        }
        List<Placement.Candidato> candidatos = replicas.entrySet().stream()
                .filter(e -> e.getValue().monitor.estado() != HeartbeatMonitor.Estado.MORTA)
                .map(e -> new Placement.Candidato(e.getKey(), e.getValue().livre()))
                .toList();
        List<String> escolhidas = Placement.choose(chunkId, tamanho, count, candidatos, exclude);
        escolhidas.forEach(id -> replicas.get(id).reservados += tamanho);
        return escolhidas;
    }

    public synchronized void confirmarReserva(String id, long tamanho) {
        Replica replica = replicas.get(id);
        if (replica != null && replica.reservados >= tamanho) {
            replica.reservados -= tamanho;
            replica.usados += Math.min(tamanho, Long.MAX_VALUE - replica.usados);
        }
    }

    public synchronized void libertar(String id, long tamanho) {
        Replica replica = replicas.get(id);
        if (replica != null && replica.reservados >= tamanho) {
            replica.reservados -= tamanho;
        }
    }

    synchronized void remover(String id) {
        replicas.remove(id);
    }

    public synchronized List<EstadoReplica> estados() {
        return replicas.keySet().stream().sorted().map(id -> {
            Replica r = replicas.get(id);
            return new EstadoReplica(id, r.monitor.estado(), r.quota, r.usados,
                    r.reservados, r.monitor.ultimoHeartbeat());
        }).toList();
    }

    private void registarTransicao(String id, HeartbeatMonitor.Estado anterior, HeartbeatMonitor.Estado atual) {
        if (anterior != atual) {
            eventos.accept("ESTADO " + id + " " + anterior + " -> " + atual);
        }
    }
}
