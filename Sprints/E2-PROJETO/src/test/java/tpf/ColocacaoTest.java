package tpf;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ColocacaoTest {
    @Test
    void medePrimeiraEscolhaSemAlterarVagas() {
        int[] quotas = {8, 2, 4, 6};
        var candidatas = new ArrayList<Placement.Candidato>();
        for (int i = 0; i < quotas.length; i++) {
            candidatas.add(new Placement.Candidato("r" + i, quotas[i] * 1_000_000L));
        }
        for (int alfa = 0; alfa <= 2; alfa++) {
            int[] escolhas = new int[quotas.length];
            for (int i = 0; i < 10_000; i++) {
                String chunk = Wire.hash(("chunk-" + i).getBytes(StandardCharsets.UTF_8));
                String id = Placement.choose(chunk, 4096, 1, candidatas, Set.of(), alfa).get(0);
                escolhas[Integer.parseInt(id.substring(1))]++;
            }
            int a = alfa;
            double soma = Arrays.stream(quotas).mapToDouble(q -> Math.pow(q, a)).sum();
            for (int j = 0; j < quotas.length; j++) {
                assertEquals(Math.pow(quotas[j], alfa) / soma, escolhas[j] / 10_000.0, 0.02);
            }
            System.out.printf("PLACEMENT alfa=%d N=10000 quotas=%s primeiras=%s%n",
                    alfa, Arrays.toString(quotas), Arrays.toString(escolhas));
        }
        var ordem = Placement.choose("chunk", 4096, 3, candidatas, Set.of());
        var inversa = new ArrayList<>(candidatas);
        java.util.Collections.reverse(inversa);
        assertEquals(ordem, Placement.choose("chunk", 4096, 3, inversa, Set.of()));
        assertFalse(Placement.choose("chunk", 4096, 4, candidatas, Set.of("r0"), 0).contains("r0"));
        assertTrue(Placement.choose("chunk", 4096, 1,
                List.of(new Placement.Candidato("pequena", 4096 + Wire.MARGEM_BYTES - 1)), Set.of(), 0)
                .isEmpty());
    }

    @Test
    void escolhaEReservaSaoAtomicasAntesEDepoisDoAck() throws Exception {
        var registry = new ReplicaRegistry(evento -> { });
        long tamanho = Wire.MARGEM_BYTES + 8192;
        registry.onHeartbeat("r1", 0, Wire.MARGEM_BYTES + tamanho, 0);
        var prontas = new CountDownLatch(2);
        var partir = new CountDownLatch(1);
        var threads = Executors.newFixedThreadPool(2);
        try {
            Callable<List<String>> reservar = () -> {
                prontas.countDown();
                assertTrue(partir.await(3, TimeUnit.SECONDS));
                return registry.reserveAndChoose(Thread.currentThread().getName(), tamanho, 1, Set.of());
            };
            var a = threads.submit(reservar);
            var b = threads.submit(reservar);
            assertTrue(prontas.await(3, TimeUnit.SECONDS));
            partir.countDown();
            assertEquals(1, a.get(3, TimeUnit.SECONDS).size() + b.get(3, TimeUnit.SECONDS).size());
            assertEquals(tamanho, registry.estados().get(0).reservados());
            registry.confirmarReserva("r1", tamanho);
            assertEquals(tamanho, registry.estados().get(0).usados());
            assertEquals(0, registry.estados().get(0).reservados());
            assertTrue(registry.reserveAndChoose("terceiro", 1, 1, Set.of()).isEmpty());
            registry.onHeartbeat("r1", tamanho, Wire.MARGEM_BYTES + 2 * tamanho, 1);
            assertEquals(List.of("r1"), registry.reserveAndChoose("terceiro", tamanho, 1, Set.of()));
            registry.libertar("r1", tamanho);
            assertEquals(0, registry.estados().get(0).reservados());
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void estadosExclusaoERegresso() {
        var eventos = new ArrayList<String>();
        var registry = new ReplicaRegistry(eventos::add);
        long quota = Wire.MARGEM_BYTES + 4096;
        registry.onHeartbeat("r1", 0, quota, 0);
        registry.atualizarEstados(Wire.SUSPEITA_MS - 1L);
        assertEquals(HeartbeatMonitor.Estado.VIVA, registry.estados().get(0).estado());
        registry.atualizarEstados(Wire.SUSPEITA_MS);
        assertEquals(HeartbeatMonitor.Estado.SUSPEITA, registry.estados().get(0).estado());
        assertEquals(List.of("r1"), registry.reserveAndChoose("chunk", 4096, 1, Set.of()));
        registry.libertar("r1", 4096);
        registry.atualizarEstados(Wire.MORTA_MS);
        assertEquals(HeartbeatMonitor.Estado.MORTA, registry.estados().get(0).estado());
        assertTrue(registry.reserveAndChoose("chunk", 4096, 1, Set.of()).isEmpty());
        registry.onHeartbeat("r1", 0, quota, Wire.MORTA_MS + 1L);
        assertEquals(HeartbeatMonitor.Estado.VIVA, registry.estados().get(0).estado());
        assertEquals(List.of("ESTADO r1 MORTA -> VIVA", "ESTADO r1 VIVA -> SUSPEITA",
                "ESTADO r1 SUSPEITA -> MORTA", "ESTADO r1 MORTA -> VIVA"), eventos);
    }
}
