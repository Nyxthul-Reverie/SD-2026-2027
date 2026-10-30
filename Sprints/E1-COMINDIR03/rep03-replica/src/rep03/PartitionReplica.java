package rep03;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

public class PartitionReplica {
    private static final int N = 3;

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            System.out.println("Uso: PartitionReplica <id> <partições> [portoApi]");
            return;
        }

        String id = args[0];
        int portoApi;
        double perda;
        Long descartar;
        Set<Integer> partitions = new LinkedHashSet<>();
        try {
            if (id.isBlank() || id.contains("/") || id.contains("\\")) {
                throw new IllegalArgumentException("Identificador de réplica inválido.");
            }
            portoApi = args.length > 2 ? Integer.parseInt(args[2]) : 5001;
            if (portoApi < 1 || portoApi > 65535) {
                throw new IllegalArgumentException("Porto da API inválido.");
            }
            for (String part : args[1].split(",", -1)) {
                int k = Integer.parseInt(part.trim());
                if (k < 0 || !partitions.add(k)) {
                    throw new IllegalArgumentException("Partições inválidas: use inteiros não negativos, sem repetições.");
                }
            }
            perda = Double.parseDouble(System.getProperty("perda", "0"));
            if (!Double.isFinite(perda) || perda < 0 || perda > 1) {
                throw new IllegalArgumentException("perda tem de estar entre 0 e 1.");
            }
            String discardProperty = System.getProperty("descartar");
            descartar = discardProperty == null ? null : Long.parseLong(discardProperty);
            if (descartar != null && descartar < 1) {
                throw new IllegalArgumentException("descartar tem de ser um seq positivo.");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Argumentos/propriedades inválidos: " + e.getMessage());
            return;
        }

        String api = "http://127.0.0.1:" + portoApi + "/api/v0";
        List<PartitionState> states = new ArrayList<>();
        try {
            for (int k : partitions) {
                states.add(new PartitionState(k, new RecordFile("replica-" + id + "-p" + k + ".txt", N),
                        perda, descartar));
            }
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("Erro ao iniciar réplica: " + e.getMessage());
            return;
        }

        for (PartitionState state : states) {
            Thread subscription = new Thread(() -> {
                try {
                    System.out.println("Réplica " + id + " à escuta em " + Wire.topicData(state.k));
                    new IpfsPubSub(api).subscribe(Wire.topicData(state.k), state::receive);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Subscrição interrompida: " + e.getMessage());
                } catch (IOException | UncheckedIOException e) {
                    System.out.println("Erro de subscrição: " + e.getMessage());
                }
            }, "subscricao-p" + state.k);
            subscription.start();
        }
    }

    private static SensorRecord parseRecord(String line) {
        if (line.trim().split(";", -1).length != 6) {
            throw new IllegalArgumentException("Formato de registo inválido.");
        }
        SensorRecord record = SensorRecord.fromLine(line, N);
        if (record.getSeq() < 1 || record.getSensorId().isBlank() || !Double.isFinite(record.getTemperature())) {
            throw new IllegalArgumentException("Registo inválido.");
        }
        try {
            LocalDateTime.parse(record.getTimestamp());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Instante inválido.", e);
        }
        return record;
    }

    // Cada estado é acedido exclusivamente pela thread de subscrição da sua partição.
    private static class PartitionState {
        private final int k;
        private final RecordFile file;
        private long last;
        private final TreeMap<Long, SensorRecord> pending = new TreeMap<>();
        private String leaderHost;
        private int leaderPort;
        private final double perda;
        private final Long descartar;
        private boolean discarded;
        private final Random random = new Random();

        PartitionState(int k, RecordFile file, double perda, Long descartar) throws IOException {
            this.k = k;
            this.file = file;
            this.last = file.lastSeq();
            this.perda = perda;
            this.descartar = descartar;
        }

        void receive(String message) {
            SensorRecord current;
            String host;
            int port;
            try {
                String[] parts = Wire.regParts(message);
                host = parts[0];
                port = Integer.parseInt(parts[1]);
                if (host.isBlank() || port < 1 || port > 65535) {
                    throw new IllegalArgumentException("Endereço/porto inválido.");
                }
                current = parseRecord(parts[2]);
            } catch (IllegalArgumentException e) {
                System.out.println("REJEITADO: " + message);
                return;
            }

            leaderHost = host;
            leaderPort = port;
            boolean discardOnce = descartar != null && current.getSeq() == descartar && !discarded;
            if (discardOnce) discarded = true;
            if (discardOnce || random.nextDouble() < perda) {
                System.out.println("DESCARTADO (simulação): " + current.getSeq());
                return;
            }

            try {
                apply(current);
                // Também permite repetir uma recuperação falhada ao receber um duplicado.
                if (pending.size() > Wire.LIMITE_RETIDOS) recover();
            } catch (IOException e) {
                System.out.println("Erro de escrita: " + e.getMessage());
            }
        }

        private void apply(SensorRecord current) throws IOException {
            long seq = current.getSeq();
            if (seq <= last || pending.containsKey(seq)) {
                System.out.println("DUPLICADO: " + seq);
            } else if (seq == last + 1) {
                file.append(current);
                last = seq;
                System.out.println("Aplicado: " + current.toLine());
                cascade();
            } else {
                pending.put(seq, current);
                System.out.println("EM ESPERA: " + seq + " (falta o registo " + (last + 1) + ")");
            }
        }

        private void cascade() throws IOException {
            SensorRecord next;
            while ((next = pending.get(last + 1)) != null) {
                file.append(next);
                // Só retirar de pending depois de o append ter sucesso.
                pending.remove(next.getSeq());
                last = next.getSeq();
                System.out.println("Aplicado em cascata: " + next.toLine());
            }
        }

        private void recover() {
            long from = last + 1;
            long to = pending.firstKey() - 1;
            if (leaderHost == null || leaderHost.isBlank() || leaderPort < 1 || leaderPort > 65535) {
                System.out.println("RECUPERAÇÃO FALHOU: endereço/porto do líder indisponível.");
                return;
            }
            System.out.println("RECUPERAÇÃO: " + from + ".." + to + " pedida a " + leaderHost + ":" + leaderPort);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(leaderHost, leaderPort), Wire.T_TCP_MS);
                socket.setSoTimeout(Wire.T_TCP_MS);
                try (BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                    out.println("PEDIDO " + from + " " + to);
                    if (out.checkError()) throw new IOException("Falha ao enviar PEDIDO.");
                    while (true) {
                        String line = in.readLine();
                        if (line == null) throw new IOException("Resposta interrompida antes de FIM.");
                        if (line.equals("FIM")) {
                            cascade();
                            return;
                        }
                        if (line.equals("ERRO") || line.startsWith("ERRO ")) throw new IOException(line);
                        SensorRecord record = parseRecord(line);
                        if (record.getSeq() < from || record.getSeq() > to) {
                            throw new IOException("Registo fora do intervalo pedido: " + record.getSeq());
                        }
                        System.out.println("RECUPERADO: " + record.getSeq());
                        // Sem simulação de perdas e sem iniciar outra recuperação.
                        apply(record);
                    }
                }
            } catch (IOException | IllegalArgumentException e) {
                System.out.println("RECUPERAÇÃO FALHOU: " + e.getMessage());
            }
        }
    }
}
