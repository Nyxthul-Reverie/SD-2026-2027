package rep01;

import java.io.IOException;
import java.util.Random;
import java.util.Scanner;

public class PubSubLeader {
    static final String TOPIC = "rep01-temperaturas";
    static final String DEFAULT_API = "http://127.0.0.1:5001/api/v0";

    public static void main(String[] args) {
        String api = args.length > 0 ? args[0] : DEFAULT_API;
        IpfsPubSub pubSub = new IpfsPubSub(api);
        RecordFile file = new RecordFile("lider.txt");

        try (Scanner sc = new Scanner(System.in)) {
            long seq = file.lastSeq(); // continua a numeração após reinício
            Random random = new Random();
            System.out.println("Líder PubSub pronto. Formato: <sensor> <temperatura> | rajada <n> | 'sair' para terminar");

            while (true) {
                System.out.print("> ");
                if (!sc.hasNextLine()) break; // espera pelo próximo comando
                String line = sc.nextLine().trim();
                if (line.equalsIgnoreCase("sair")) break;

                String[] p = line.split("\\s+");
                if (p.length != 2) {
                    System.out.println("Formato inválido.");
                    continue;
                }

                if (p[0].equalsIgnoreCase("rajada")) {
                    int n;
                    try {
                        n = Integer.parseInt(p[1]);
                    } catch (NumberFormatException e) {
                        System.out.println("Número da rajada inválido: " + p[1]);
                        continue;
                    }
                    if (n <= 0) {
                        System.out.println("O número de registos da rajada tem de ser positivo.");
                        continue;
                    }

                    long firstSendTime = 0;
                    long lastSendTime = 0;
                    for (int i = 0; i < n; i++) {
                        double temperature = 15.0 + random.nextDouble() * 15.0;
                        SensorRecord r = SensorRecord.now(++seq, "SIM", temperature);
                        file.append(r); // primeiro guarda no ficheiro do líder
                        if (i == 0) firstSendTime = System.currentTimeMillis();
                        pubSub.publish(TOPIC, r.toLine()); // depois publica uma mensagem por registo
                        if (i == n - 1) lastSendTime = System.currentTimeMillis();
                    }
                    System.out.println("Rajada concluída: " + n + " registos");
                    System.out.println("Primeiro envio (ms): " + firstSendTime);
                    System.out.println("Último envio (ms): " + lastSendTime);
                    continue;
                }

                double temperature;
                try {
                    temperature = Double.parseDouble(p[1]);
                } catch (NumberFormatException e) {
                    System.out.println("Temperatura inválida: " + p[1]);
                    continue;
                }

                SensorRecord r = SensorRecord.now(++seq, p[0], temperature);
                file.append(r);
                pubSub.publish(TOPIC, r.toLine());
                System.out.println("Registado e publicado: " + r.toLine());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Publicação interrompida: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IO (" + e.getClass().getSimpleName() + "): " + e.getMessage());
        }
    }
}
