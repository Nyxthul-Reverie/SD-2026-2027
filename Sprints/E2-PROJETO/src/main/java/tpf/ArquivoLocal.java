package tpf;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ArquivoLocal {
    record ChunkSelado(String id, long lsnDe, long lsnAte, long tamanho) {
    }

    private record ChunkVerificado(ChunkSelado meta, List<Registo> registos) {
    }

    private final Path log;
    private final Path eventos;
    private final Path selados;
    private final ChunkCrypto crypto;
    private final ScheduledExecutorService temporizador;
    private final Consumer<ChunkSelado> aoSelar;
    private Chunk aberto;
    private long ultimoLsn;
    private IOException falha;

    public ArquivoLocal(Path pasta, ScheduledExecutorService temporizador)
            throws IOException, GeneralSecurityException {
        this(pasta, temporizador, chunk -> { });
    }

    ArquivoLocal(Path pasta, ScheduledExecutorService temporizador, Consumer<ChunkSelado> aoSelar)
            throws IOException, GeneralSecurityException {
        Files.createDirectories(pasta);
        log = pasta.resolve("registos.log");
        eventos = pasta.resolve("eventos-lider.txt");
        selados = Files.createDirectories(pasta.resolve("selados"));
        this.temporizador = temporizador;
        this.aoSelar = aoSelar;

        List<Registo> anteriores = lerLog();
        Path chave = pasta.resolve("master.key");
        if (Files.notExists(chave)) {
            try (var ficheiros = Files.list(selados)) {
                if (!anteriores.isEmpty() || ficheiros.findAny().isPresent()) {
                    throw new IOException("Já existem dados. Repõe a master.key original.");
                }
            }
        }
        crypto = new ChunkCrypto(carregarOuCriarChave(chave));

        long seladoAte = verificarChunks(anteriores);
        ultimoLsn = anteriores.size();
        // Retoma apenas a parte do log que ainda não chegou a um chunk completo.
        synchronized (this) {
            for (int i = Math.toIntExact(seladoAte); i < anteriores.size(); i++) {
                agregar(anteriores.get(i));
            }
        }
    }

    public synchronized long registar(long autor, long canal, String texto) throws IOException {
        if (falha != null) {
            throw new IOException("Arquivo indisponível. Verifica o erro e reinicia o líder.", falha);
        }
        Registo registo = new Registo(ultimoLsn + 1, System.currentTimeMillis(), autor, canal, texto);
        try {
            appendDuravel(log, registo.linhaDoLog() + "\n");
            ultimoLsn = registo.lsn();
            evento("REGISTO", "lsn=" + registo.lsn());
            agregar(registo);
            return registo.lsn();
        } catch (IOException e) {
            falha = e;
            throw e;
        }
    }

    private void agregar(Registo registo) throws IOException {
        if (aberto == null) {
            Chunk novo = new Chunk();
            aberto = novo;
            temporizador.schedule(() -> selarPorTempo(novo), Wire.SELAGEM_MS, TimeUnit.MILLISECONDS);
        }
        if (aberto.adicionar(registo) >= Wire.CHUNK_MAX_BYTES) {
            selarAberto();
        }
    }

    private synchronized void selarPorTempo(Chunk esperado) {
        if (aberto != esperado || falha != null) {
            return;
        }
        try {
            selarAberto();
        } catch (IOException e) {
            falha = e;
            System.err.println("Falhou a selagem: " + e.getMessage());
        }
    }

    private void selarAberto() throws IOException {
        Chunk chunk = aberto;
        try {
            Sealed sealed = crypto.seal(chunk.selar());
            byte[] bytes = sealed.cifrado();
            ArquivosAtomicos.publicar(selados.resolve(sealed.id() + ".bin"), bytes);
            ArquivosAtomicos.publicar(selados.resolve(sealed.id() + ".hdr"),
                    (sealed.cabecalho() + "\n").getBytes(StandardCharsets.US_ASCII));
            evento("SELADO", "chunkId=" + sealed.id() + ",de=" + chunk.primeiroLsn()
                    + ",ate=" + chunk.ultimoLsn() + ",bytes=" + bytes.length);
            aberto = null;
            aoSelar.accept(new ChunkSelado(sealed.id(), chunk.primeiroLsn(), chunk.ultimoLsn(), bytes.length));
        } catch (GeneralSecurityException e) {
            throw new IOException("Falha ao cifrar o chunk.", e);
        }
    }

    private void evento(String tipo, String detalhe) throws IOException {
        ArquivosAtomicos.evento(eventos, tipo + ";" + detalhe);
    }

    private static void appendDuravel(Path path, String linha) throws IOException {
        try (FileChannel out = FileChannel.open(path, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ArquivosAtomicos.escreverTudo(out, linha.getBytes(StandardCharsets.UTF_8));
        }
    }

    private List<Registo> lerLog() throws IOException {
        if (Files.notExists(log)) {
            return List.of();
        }
        byte[] bytes = Files.readAllBytes(log);
        List<Registo> lidos = new ArrayList<>();
        int inicio = 0;
        try {
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == '\n') {
                    Registo registo = Registo.lerLinha(
                            new String(bytes, inicio, i - inicio, StandardCharsets.UTF_8));
                    if (registo.lsn() != lidos.size() + 1L) {
                        throw new IOException("Sequência de LSN inválida em registos.log.");
                    }
                    lidos.add(registo);
                    inicio = i + 1;
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Linha inválida em registos.log.", e);
        }
        if (inicio < bytes.length) {
            // Só se remove o fragmento final sem LF: nenhuma confirmação o poderia ter
            // usado.
            try (FileChannel out = FileChannel.open(log, StandardOpenOption.WRITE)) {
                out.truncate(inicio);
                out.force(true);
            }
        }
        return lidos;
    }

    private long verificarChunks(List<Registo> logLido) throws IOException, GeneralSecurityException {
        List<ChunkVerificado> chunks = new ArrayList<>();
        try (var ficheiros = Files.list(selados)) {
            for (Path hdr : ficheiros.filter(p -> p.toString().endsWith(".hdr")).toList()) {
                String nome = hdr.getFileName().toString();
                String id = nome.substring(0, nome.length() - 4);
                List<Registo> registos = abrir(selados, id, crypto);
                var meta = new ChunkSelado(id, registos.get(0).lsn(), registos.get(registos.size() - 1).lsn(),
                        Files.size(selados.resolve(id + ".bin")));
                chunks.add(new ChunkVerificado(meta, registos));
            }
        }
        chunks.sort(Comparator.comparingLong(chunk -> chunk.meta().lsnDe()));
        int posicao = 0;
        for (ChunkVerificado chunk : chunks) {
            for (Registo registo : chunk.registos()) {
                if (posicao >= logLido.size() || !registo.equals(logLido.get(posicao))) {
                    throw new IOException("Chunks e registos.log divergem no LSN " + (posicao + 1));
                }
                posicao++;
            }
        }
        // Inclui chunks do E2-02 ainda sem entrada no catálogo.
        chunks.forEach(chunk -> aoSelar.accept(chunk.meta()));
        return posicao;
    }

    static List<Registo> abrir(Path pastaSelados, String id, ChunkCrypto crypto)
            throws IOException, GeneralSecurityException {
        if (!id.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("chunkId deve ser um SHA-256 hexadecimal.");
        }
        Sealed sealed = Sealed.ler(id, Files.readString(pastaSelados.resolve(id + ".hdr")),
                Files.readAllBytes(pastaSelados.resolve(id + ".bin")));
        return Chunk.ler(crypto.open(sealed));
    }

    // Ferramenta local para demonstrar a abertura, sem depender do Discord.
    public static void main(String[] args) throws IOException, GeneralSecurityException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Uso: tpf.ArquivoLocal <pasta-dados> <chunkId>");
        }
        Path pasta = Path.of(args[0]);
        ChunkCrypto crypto = new ChunkCrypto(Files.readAllBytes(pasta.resolve("master.key")));
        abrir(pasta.resolve("selados"), args[1], crypto).forEach(System.out::println);
    }

    private static byte[] carregarOuCriarChave(Path path) throws IOException {
        if (Files.notExists(path)) {
            byte[] nova = new byte[ChunkCrypto.MASTER_KEY_BYTES];
            new SecureRandom().nextBytes(nova);
            try (FileChannel out = FileChannel.open(path,
                    EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    ArquivosAtomicos.permissoesDaChave(path.getParent()))) {
                ArquivosAtomicos.escreverTudo(out, nova);
            }
        }
        byte[] chave = Files.readAllBytes(path);
        if (chave.length != ChunkCrypto.MASTER_KEY_BYTES) {
            throw new IOException("master.key deve ter exatamente 32 bytes.");
        }
        return chave;
    }

}
