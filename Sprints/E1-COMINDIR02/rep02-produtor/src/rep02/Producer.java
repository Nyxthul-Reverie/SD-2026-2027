package rep02;

import java.io.IOException;
import java.util.Random;
import java.util.Scanner;

public class Producer {
    static final String DEFAULT_API = "http://127.0.0.1:5001/api/v0";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.out.println("Uso: Producer <N> [api]");
            return;
        }
        Partitioner partitioner;
        try {
            partitioner = new Partitioner(Integer.parseInt(args[0]));
        } catch (IllegalArgumentException e) {
            System.out.println("Argumentos inválidos: " + e.getMessage());
            return;
        }
        String api = args.length > 1 ? args[1] : DEFAULT_API;
        IpfsPubSub pubSub = new IpfsPubSub(api);
        Random random = new Random();

        try (Scanner sc = new Scanner(System.in)) {
            System.out.println("Produtor pronto. Formato: <sensor> <temperatura> | rajada <n> <s> | rajada-quente <n> <s> | sair");
            while (true) {
                System.out.print("> ");
                if (!sc.hasNextLine()) break;
                String line = sc.nextLine().trim();
                if (line.equalsIgnoreCase("sair")) break;
                String[] p = line.split("\\s+");

                try {
                    if (p[0].equalsIgnoreCase("rajada") || p[0].equalsIgnoreCase("rajada-quente")) {
                        boolean hot = p[0].equalsIgnoreCase("rajada-quente");
                        if (p.length != 3) {
                            System.out.println("Formato inválido. Use: " + p[0] + " <n> <s>");
                            continue;
                        }
                        int n;
                        int s;
                        try {
                            n = Integer.parseInt(p[1]);
                            s = Integer.parseInt(p[2]);
                        } catch (NumberFormatException e) {
                            System.out.println("n e s têm de ser inteiros positivos.");
                            continue;
                        }
                        if (n <= 0 || s <= 0) {
                            System.out.println("n e s têm de ser inteiros positivos.");
                            continue;
                        }
                        if (hot && s < 2) {
                            System.out.println("rajada-quente requer s >= 2 para os sensores S02..S<s>.");
                            continue;
                        }
                        publishBurst(pubSub, partitioner, random, n, s, hot);
                        continue;
                    }

                    if (p.length != 2) {
                        System.out.println("Formato inválido. Use: <sensor> <temperatura>");
                        continue;
                    }
                    if (p[0].isEmpty() || p[0].contains(";")) {
                        System.out.println("Sensor inválido: " + p[0]);
                        continue;
                    }
                    double temperature;
                    try {
                        temperature = Double.parseDouble(p[1]);
                    } catch (NumberFormatException e) {
                        System.out.println("Temperatura inválida: " + p[1]);
                        continue;
                    }
                    if (!Double.isFinite(temperature)) {
                        System.out.println("Temperatura inválida: " + p[1]);
                        continue;
                    }
                    String message = Wire.in(p[0], temperature);
                    int k = partitioner.of(p[0]);
                    pubSub.publish(Wire.topicIn(k), message);
                    System.out.println("Publicado em " + Wire.topicIn(k) + ": " + message + " → p" + k);
                } catch (IOException e) {
                    System.out.println("Erro de publicação (" + e.getClass().getSimpleName() + "): " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Publicação interrompida: " + e.getMessage());
        }
    }

    private static void publishBurst(IpfsPubSub pubSub, Partitioner partitioner,
                                     Random random, int n, int s, boolean hot)
            throws IOException, InterruptedException {
        long firstSendTime = 0;
        long lastSendTime = 0;
        long[] counts = new long[partitioner.count()];
        for (int i = 0; i < n; i++) {
            int sensorNumber = hot
                    ? (random.nextDouble() < 0.8 ? 1 : random.nextInt(s - 1) + 2)
                    : random.nextInt(s) + 1;
            String sensor = (sensorNumber < 10 ? "S0" : "S") + sensorNumber;
            double temperature = 15.0 + random.nextDouble() * 15.0;
            String message = Wire.in(sensor, temperature);
            int k = partitioner.of(sensor);
            if (i == 0) firstSendTime = System.currentTimeMillis();
            pubSub.publish(Wire.topicIn(k), message);
            counts[k]++;
            if (i == n - 1) lastSendTime = System.currentTimeMillis();
            System.out.println("Publicado em " + Wire.topicIn(k) + ": " + message + " → p" + k);
        }
        System.out.println("Rajada concluída: " + n + " registos enviados");
        System.out.println("Primeiro envio (ms): " + firstSendTime);
        System.out.println("Último envio (ms): " + lastSendTime);
        for (int k = 0; k < counts.length; k++) {
            System.out.println("Envios p" + k + ": " + counts[k]);
        }
    }
}
