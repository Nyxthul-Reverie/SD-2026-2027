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
            System.out.println("Réplica " + id + " à escuta em " + GROUP + ":" + PORT);
            byte[] buffer = new byte[1000];
            while (true) {
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                socket.receive(p); // bloqueia até chegar um datagrama
                String line = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);

                try {
                    SensorRecord current = SensorRecord.fromLine(line);
                    long seq = current.getSeq();

                    if (seq <= last || pending.containsKey(seq)) {
                        System.out.println("DUPLICADO: " + seq);
                        continue;
                    }

                    if (seq > last + 1) {
                        pending.put(seq, current);
                        System.out.println("EM ESPERA: " + seq + " (falta o registo " + (last + 1) + ")");
                        continue;
                    }

                    file.append(current);
                    last = seq;
                    System.out.println("Aplicado: " + current.toLine());

                    SensorRecord next;
                    while ((next = pending.remove(last + 1)) != null) {
                        file.append(next);
                        last = next.getSeq();
                        System.out.println("Aplicado em cascata: " + next.toLine());
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("REJEITADO: " + line);
                }
            }
        } catch (IOException e) {
            System.out.println("IO: " + e.getMessage());
        }
    }
}
