package tpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static tpf.ClienteTeste.chaves;
import static tpf.ClienteTeste.prova;

class PubSubHubTest {
    @TempDir
    Path pasta;

    @Test
    void registoUnicoExpiracaoProvaPermissoesEPersistencia() throws Exception {
        var relogio = new AtomicLong();
        var eventos = new LinkedBlockingQueue<String>();
        var publicacoes = new LinkedBlockingQueue<PubSubHub.Publicacao>();
        var endereco = new InetSocketAddress("127.0.0.1", 0);
        var hub = new PubSubHub(endereco, pasta, publicacoes::add, eventos::add, relogio::get);
        var par = chaves();
        var outro = chaves();
        String token = hub.gerarToken();
        String provaAnterior;
        try {
            hub.iniciar();
            URI url = url(hub);
            try (var invalido = new ClienteTeste(url)) {
                String nonce = invalido.ola("r1", par, token);
                invalido.enviar(prova("r1", "r1", nonce, outro));
                invalido.esperarFecho();
            }
            assertFalse(Files.exists(pasta.resolve("replicas.txt")));
            try (var primeiro = new ClienteTeste(url); var concorrente = new ClienteTeste(url)) {
                String nonce = primeiro.ola("r1", par, token);
                String nonce2 = concorrente.ola("r2", outro, token);
                assertNotEquals(nonce, nonce2);
                provaAnterior = prova("r1", "r1", nonce, par);
                primeiro.enviar(provaAnterior);
                primeiro.barreira();
                concorrente.enviar(prova("r2", "r2", nonce2, outro));
                concorrente.esperarFecho();
            }
            assertEquals(1, Files.readAllLines(pasta.resolve("replicas.txt")).size());
            try (var reutiliza = new ClienteTeste(url)) {
                reutiliza.enviar("OLA;r2;" + java.util.Base64.getEncoder().encodeToString(outro.getPublic()
                        .getEncoded()) + ";" + token);
                reutiliza.esperarFecho();
            }
            try (var reutiliza = new ClienteTeste(url)) {
                reutiliza.enviar("OLA;r1;" + java.util.Base64.getEncoder().encodeToString(par.getPublic()
                        .getEncoded()) + ";" + token);
                reutiliza.esperarFecho();
            }
            String aExpirar = hub.gerarToken();
            try (var expirado = new ClienteTeste(url)) {
                String nonce = expirado.ola("r2", outro, aExpirar);
                relogio.addAndGet(Wire.TOKEN_VALIDADE_MS);
                expirado.enviar(prova("r2", "r2", nonce, outro));
                expirado.esperarFecho();
            }
            try (var expirado = new ClienteTeste(url)) {
                expirado.enviar("OLA;r2;" + java.util.Base64.getEncoder().encodeToString(outro.getPublic()
                        .getEncoded()) + ";" + aExpirar);
                expirado.esperarFecho();
            }
            try (var replay = new ClienteTeste(url)) {
                replay.ola("r1", null, null);
                replay.enviar(provaAnterior);
                replay.esperarFecho();
            }
            try (var idErrado = new ClienteTeste(url)) {
                String nonce = idErrado.ola("r1", null, null);
                idErrado.enviar(prova("r1", "r2", nonce, par));
                idErrado.esperarFecho();
            }
            try (var chaveErrada = new ClienteTeste(url)) {
                chaveErrada.enviar("OLA;r1;" + java.util.Base64.getEncoder().encodeToString(outro.getPublic()
                        .getEncoded()) + ";" + hub.gerarToken());
                chaveErrada.esperarFecho();
            }
            for (String foraDeOrdem : List.of("SUB;tpf-ctl", "PUB;tpf-lider;HB;r1;0;10", provaAnterior)) {
                try (var nova = new ClienteTeste(url)) {
                    nova.enviar(foraDeOrdem);
                    nova.esperarFecho();
                }
            }
            for (String foraDeOrdem : List.of("OLA;r1", "SUB;tpf-ctl", "PUB;tpf-lider;HB;r1;0;10")) {
                try (var desafiada = new ClienteTeste(url)) {
                    desafiada.ola("r1", null, null);
                    desafiada.enviar(foraDeOrdem);
                    desafiada.esperarFecho();
                }
            }
            try (var r1 = new ClienteTeste(url); var r2 = new ClienteTeste(url)) {
                r1.autenticar("r1", par, null);
                r2.autenticar("r2", outro, hub.gerarToken());
                assertEquals(1, hub.publish("tpf-r-r1", "dirigida"));
                assertEquals(2, hub.publish("tpf-ctl", "difusa"));
                assertEquals("dirigida", r1.receber());
                assertEquals("difusa", r1.receber());
                assertEquals("difusa", r2.receber());
                r1.enviar("PUB;tpf-lider;HB;r1;0;10");
                var pub = publicacoes.poll(5, TimeUnit.SECONDS);
                assertNotNull(pub);
                assertEquals("r1", pub.id());
                assertEquals("HB;r1;0;10", pub.mensagem());
                r1.enviar("SUB;tpf-r-r2");
                r1.esperarFecho();
                r2.enviar("PUB;tpf-r-r1;STORE");
                r2.esperarFecho();
            }
            assertTrue(eventos.size() >= 14);
            assertTrue(eventos.stream().allMatch(e -> e.startsWith("ERRO") && !e.contains(token)));
        } finally {
            hub.stop(1000);
        }

        var reiniciado = new PubSubHub(endereco, pasta, publicacoes::add, eventos::add);
        try {
            reiniciado.iniciar();
            try (var r1 = new ClienteTeste(url(reiniciado))) {
                r1.autenticar("r1", par, null);
                assertTrue(reiniciado.remover("r1"));
                r1.esperarFecho();
                assertFalse(reiniciado.remover("r1"));
            }
            assertEquals(1, Files.readAllLines(pasta.resolve("replicas.txt")).size());
            try (var revogada = new ClienteTeste(url(reiniciado))) {
                revogada.enviar("OLA;r1");
                revogada.esperarFecho();
            }
        } finally {
            reiniciado.stop(1000);
        }
    }

    static URI url(PubSubHub hub) {
        return URI.create("ws://127.0.0.1:" + hub.getPort());
    }
}
