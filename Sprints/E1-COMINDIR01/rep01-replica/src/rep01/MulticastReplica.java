package rep01;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;

public class MulticastReplica {
    static final String GROUP = "230.0.0.1";
    static final int PORT = 6789;

    public static void main(String[] args) {
        String id = args.length > 0 ? args[0] : "1";
        RecordFile file = new RecordFile("replica-" + id + ".txt");
        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(GROUP);
            socket.joinGroup(group); // passa a receber o que é enviado ao grupo
            long last = file.lastSeq();
            TreeMap<Long, SensorRecord> pending = new TreeMap<>();
            long firstBurstReceiveTime = 0;
            long lastBurstReceiveTime = 0;
            long burstReceived = 0;
            long burstWritten = 0;
            System.out.println("Réplica " + id + " à escuta em " + GROUP + ":" + PORT);
            byte[] buffer = new byte[1000];
            while (true) {
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                socket.receive(p); // bloqueia até chegar um datagrama
                long receivedAt = System.currentTimeMillis();
                String line = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);

                try {
                    SensorRecord current = SensorRecord.fromLine(line);
                    long seq = current.getSeq();
                    boolean burstStatsChanged = false;

                    if (current.getSensorId().equalsIgnoreCase("SIM")) {
                        burstReceived++;
                        if (firstBurstReceiveTime == 0) {
                            firstBurstReceiveTime = receivedAt;
                        }
                        lastBurstReceiveTime = receivedAt;
                        burstStatsChanged = true;
                    }

                    if (seq <= last || pending.containsKey(seq)) {
                        System.out.println("DUPLICADO: " + seq);
                        if (burstStatsChanged) {
                            printBurstStats(firstBurstReceiveTime, lastBurstReceiveTime,
                                    burstReceived, burstWritten, pending.size());
                        }
                        continue;
                    }

                    if (seq > last + 1) {
                        pending.put(seq, current);
                        System.out.println("EM ESPERA: " + seq + " (falta o registo " + (last + 1) + ")");
                        if (burstStatsChanged) {
                            printBurstStats(firstBurstReceiveTime, lastBurstReceiveTime,
                                    burstReceived, burstWritten, pending.size());
                        }
                        continue;
                    }

                    file.append(current);
                    last = seq;
                    if (current.getSensorId().equalsIgnoreCase("SIM")) {
                        burstWritten++;
                    }
                    System.out.println("Aplicado: " + current.toLine());

                    SensorRecord next;
                    while ((next = pending.remove(last + 1)) != null) {
                        file.append(next);
                        last = next.getSeq();
                        if (next.getSensorId().equalsIgnoreCase("SIM")) {
                            burstWritten++;
                            burstStatsChanged = true;
                        }
                        System.out.println("Aplicado em cascata: " + next.toLine());
                    }

                    if (burstStatsChanged) {
                        printBurstStats(firstBurstReceiveTime, lastBurstReceiveTime,
                                burstReceived, burstWritten, pending.size());
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("REJEITADO: " + line);
                }
            }
        } catch (IOException e) {
            System.out.println("IO: " + e.getMessage());
        }
    }

    private static void printBurstStats(long firstReceiveTime, long lastReceiveTime,
                                        long received, long written, int pending) {
        System.out.println("Rajada SIM | primeira receção (ms): " + firstReceiveTime
                + " | última receção (ms): " + lastReceiveTime
                + " | recebidos: " + received
                + " | escritos: " + written
                + " | em memória: " + pending);
    }
}
