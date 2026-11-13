package raft04;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Estado de eleição de um único nó de uma partição (COMINDIR05, 4.1/1).
 * O termo identifica uma época de eleição; não é o relógio de Lamport.
 * Apenas termo e candidato votado sobrevivem ao reinício. O papel, o líder
 * conhecido, os votos recebidos e o prazo de eleição são estado volátil.
 *
 * Os métodos synchronized usam o monitor desta instância. PartitionNode
 * adquire primeiro o seu próprio monitor para coordenar este estado com o
 * histórico. Esta classe não publica mensagens nem chama métodos do nó.
 */
public final class RaftState {
    public enum Role { SEGUIDOR, CANDIDATO, LIDER }

    // resposta a um PEDIDO_VOTO: termo em que é dada e se o voto foi concedido
    public static final class Vote {
        public final long term;
        public final boolean granted;
        Vote(long term, boolean granted) { this.term = term; this.granted = granted; }
    }

    private final int partition;
    private final int self;
    private final int groupSize;
    private final long minNanos;
    private final long maxNanos;
    private final Path stateFile;
    private final Consumer<String> log;

    // estado persistente: gravado antes de qualquer mensagem que dele dependa
    private long term = 0;
    private int votedFor = 0;              // 0 = ainda não votou neste termo

    // estado volátil
    private Role role = Role.SEGUIDOR;
    private int leader = 0;                // 0 = desconhecido
    private final Set<Integer> votes = new HashSet<>();
    private long deadline;                 // System.nanoTime() a partir do qual há eleição

    // Reiniciar nunca restaura o cargo de líder: é necessária uma nova eleição.
    public RaftState(int partition, int self, int groupSize, long minMs, long maxMs,
                     Path stateFile, Consumer<String> log) throws IOException {
        this.partition = partition;
        this.self = self;
        this.groupSize = groupSize;
        this.minNanos = minMs * 1_000_000L;
        this.maxNanos = maxMs * 1_000_000L;
        this.stateFile = stateFile;
        this.log = log;
        if (Files.exists(stateFile)) {
            String[] f = Files.readString(stateFile, StandardCharsets.UTF_8).trim().split(";");
            term = Long.parseLong(f[0]);
            votedFor = Integer.parseInt(f[1]);
        }
        resetTimer();
    }

    // nanoTime mede durações sem depender de ajustes no relógio físico.
    // Sortear de novo reduz candidaturas simultâneas em eleições sucessivas.
    private void resetTimer() {
        long t = (maxNanos > minNanos)
                ? ThreadLocalRandom.current().nextLong(minNanos, maxNanos + 1)
                : minNanos;
        deadline = System.nanoTime() + t;
    }

    /**
     * Grava antes de autorizar mensagens dependentes do novo termo/voto.
     * O temporário e ATOMIC_MOVE evitam substituir o estado por meia escrita.
     * Isto não equivale a forçar os dados no disco perante corte de energia.
     */
    private void persist() {
        try {
            Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
            Files.writeString(tmp, term + ";" + votedFor + "\n", StandardCharsets.UTF_8);
            // No Windows, uma leitura externa pode impedir o rename por instantes.
            // Só este erro tem repetição limitada; sem gravação não se pode publicar.
            for (int attempt = 0; ; attempt++) {
                try {
                    Files.move(tmp, stateFile,
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    break;
                } catch (java.nio.file.AccessDeniedException busy) {
                    if (attempt >= 19) throw busy;
                    try { Thread.sleep(10); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Persistencia interrompida", interrupted);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // termo superior observado: adota-o e passa a seguidor
    private void adoptTerm(long newTerm) {
        if (role == Role.LIDER) log.accept("DEMISSAO;p" + partition + ";termo=" + term);
        log.accept("TERMO;p" + partition + ";" + term + "->" + newTerm);
        term = newTerm;
        // O voto anterior pertence a outro termo. No novo termo pode votar de novo.
        votedFor = 0;
        role = Role.SEGUIDOR;
        leader = 0;
        persist();
    }

    /**
     * Chamado pelo verificador periódico: devolve o termo da nova candidatura
     * ou -1 se não for altura de iniciar uma. O voto próprio é persistido antes
     * de o chamador poder publicar PEDIDO_VOTO; sozinho não constitui maioria.
     */
    public synchronized long tick() {
        if (role == Role.LIDER || System.nanoTime() < deadline) return -1;
        term++;
        votedFor = self;
        persist();
        role = Role.CANDIDATO;
        leader = 0;
        votes.clear();
        votes.add(self);
        resetTimer();
        log.accept("CANDIDATO;p" + partition + ";termo=" + term);
        return term;
    }

    /**
     * Só concede voto no termo atual, sem escolher dois candidatos distintos.
     * upToDate compara primeiro o termo do último registo e só depois o seq:
     * um histórico mais comprido pode continuar a ser menos atualizado.
     */
    public synchronized Vote onVoteRequest(long cTerm, int candidate, long cLastSeq, long cLastTerm,
                                           long myLastSeq, long myLastTerm) {
        if (cTerm > term) adoptTerm(cTerm);
        boolean upToDate = cLastTerm > myLastTerm
                || (cLastTerm == myLastTerm && cLastSeq >= myLastSeq);
        boolean granted = cTerm == term
                && (votedFor == 0 || votedFor == candidate)
                && upToDate;
        if (granted) {
            // Repetir o voto ao mesmo candidato é idempotente, por exemplo
            // quando o mesmo PEDIDO_VOTO volta a ser entregue pela rede.
            votedFor = candidate;
            // Se o nó reiniciar depois de responder, tem de se lembrar deste voto
            // para não conceder uma segunda maioria a outro candidato no mesmo termo.
            persist();
            // Dá ao candidato tempo para reunir a maioria antes de este eleitor
            // tentar iniciar outra eleição.
            resetTimer();
        }
        log.accept("VOTO;p" + partition + ";termo=" + term + ";candidato=n" + candidate
                + ";" + (granted ? "concedido" : "recusado"));
        // Mesmo uma recusa informa o candidato sobre um possível termo superior.
        return new Vote(term, granted);
    }

    // Só devolve true na promoção: HashSet conta eleitores distintos, incluindo
    // o voto próprio. Com G=3 são necessários dois; a maioria é estrita (> G/2).
    public synchronized boolean onVote(long vTerm, int voter, int candidate, boolean granted) {
        if (vTerm > term) { adoptTerm(vTerm); return false; }
        if (role != Role.CANDIDATO || vTerm != term || candidate != self || !granted) return false;
        if(voter < 1 || voter > groupSize) return false;
        votes.add(voter);
        if (votes.size() <= groupSize / 2) return false;
        role = Role.LIDER;
        leader = self;
        log.accept("LIDER;p" + partition + ";termo=" + term + ";votos=" + votes);
        return true;
    }

    /**
     * Valida a autoridade do lote antes de PartitionNode tratar os registos.
     * Um termo superior demite o nó; um inferior é rejeitado sem renovar prazo.
     * O ramo ERRO corresponde a outro líder no mesmo termo, situação impedida
     * por maiorias que se intersetam e pelo voto único persistido nesse termo.
     */
    public synchronized boolean onBatch(long bTerm, int bLeader) {
        if (bTerm < term) return false;                    // líder obsoleto
        if (bTerm > term) adoptTerm(bTerm);
        if (role == Role.LIDER) {                          // nunca deveria acontecer
            log.accept("ERRO;p" + partition + ";dois lideres no termo " + term);
            return false;
        }
        role = Role.SEGUIDOR;                              // um candidato reconhece o líder
        if (leader != bLeader) {
            leader = bLeader;
            log.accept("NOVO_LIDER;p" + partition + ";termo=" + term + ";lider=n" + bLeader);
        }
        resetTimer();
        return true;
    }

    // O chamador mantém também o monitor do nó enquanto atribui seq e grava:
    // uma demissão não pode intercalar-se entre esta consulta e esse append.
    public synchronized long termIfLeader() { return role == Role.LIDER ? term : -1; }
    public synchronized long term()         { return term; }
    public synchronized Role role()         { return role; }
    public synchronized int leader()        { return leader; }
}
