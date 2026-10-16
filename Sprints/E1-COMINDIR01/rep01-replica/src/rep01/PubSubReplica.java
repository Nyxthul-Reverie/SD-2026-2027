package rep01;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.TreeMap;

public class PubSubReplica {
    static final String TOPIC = "rep01-temperaturas";

    public static void main(String[] args) {
        String id = args.length > 0 ? args[0] : "1";
        int port;
        try {
            port = args.length > 1 ? Integer.parseInt(args[1]) : 5001;
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.out.println("Porto da API inválido.");
            return;
        }

        RecordFile file = new RecordFile("replica-" + id + ".txt");
        IpfsPubSub pubSub = new IpfsPubSub("http://127.0.0.1:" + port + "/api/v0");
        try {
            ReplicaState state = new ReplicaState(file);
            System.out.println("Réplica PubSub " + id + " à escuta no tópico " + TOPIC + " (API " + port + ")");
            pubSub.subscribe(TOPIC, state::receive); // espera por linhas da subscrição HTTP
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Subscrição interrompida: " + e.getMessage());
        } catch (UncheckedIOException e) {
            System.out.println("IO (" + e.getCause().getClass().getSimpleName() + "): "
                    + e.getCause().getMessage());
        } catch (IOException e) {
            System.out.println("IO (" + e.getClass().getSimpleName() + "): " + e.getMessage());
        }
    }

    private static class ReplicaState {
        private final RecordFile file;
        private final TreeMap<Long, SensorRecord> pending = new TreeMap<>();
        private long last;
        private long firstBurstReceiveTime;
        private long lastBurstReceiveTime;
        private long burstReceived;
        private long burstWritten;

        ReplicaState(RecordFile file) throws IOException {
            this.file = file;
            this.last = file.lastSeq();
        }

        void receive(String line) {
            long receivedAt = System.currentTimeMillis();
            try {
                SensorRecord current = SensorRecord.fromLine(line);
                long seq = current.getSeq();
                boolean burstStatsChanged = false;

                if (current.getSensorId().equalsIgnoreCase("SIM")) {
                    burstReceived++;
                    if (firstBurstReceiveTime == 0) firstBurstReceiveTime = receivedAt;
                    lastBurstReceiveTime = receivedAt;
                    burstStatsChanged = true;
                }

                if (seq <= last || pending.containsKey(seq)) {
                    System.out.println("DUPLICADO: " + seq);
                } else if (seq > last + 1) {
                    pending.put(seq, current);
                    System.out.println("EM ESPERA: " + seq + " (falta o registo " + (last + 1) + ")");
                } else {
                    append(current);
                    last = seq;
                    if (current.getSensorId().equalsIgnoreCase("SIM")) burstWritten++;
                    System.out.println("Aplicado: " + current.toLine());

                    SensorRecord next;
                    while ((next = pending.remove(last + 1)) != null) {
                        append(next);
                        last = next.getSeq();
                        if (next.getSensorId().equalsIgnoreCase("SIM")) {
                            burstWritten++;
                            burstStatsChanged = true;
                        }
                        System.out.println("Aplicado em cascata: " + next.toLine());
                    }
                }

                if (burstStatsChanged) {
                    System.out.println("Rajada SIM | primeira receção (ms): " + firstBurstReceiveTime
                            + " | última receção (ms): " + lastBurstReceiveTime
                            + " | recebidos: " + burstReceived
                            + " | escritos: " + burstWritten
                            + " | em memória: " + pending.size());
                }
            } catch (IllegalArgumentException e) {
                System.out.println("REJEITADO: " + line);
            }
        }

        private void append(SensorRecord record) {
            try {
                file.append(record);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
