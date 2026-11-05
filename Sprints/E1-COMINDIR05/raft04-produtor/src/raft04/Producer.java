package raft04;

import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Origem de leituras IN e observador dos heartbeats de cada partição.
 * Publicar com sucesso no Kubo não confirma aceitação ou replicação pelos nós;
 * produtor.txt permite comparar os envios com os históricos finais.
 */
public class Producer {
    static final String DEFAULT_API = Wire.api();
    private static HeartbeatMonitor[] detectors;
    private static final long INTERVALO_MS = Long.parseLong(System.getProperty("raft04.intervalo", "10"));

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
        if (INTERVALO_MS < 0) throw new IllegalArgumentException("Intervalo negativo.");
        MonitorLog log = new MonitorLog("produtor");
        detectors = new HeartbeatMonitor[partitioner.count()];
        for (int k = 0; k < detectors.length; k++) {
            final int partition = k;
            detectors[k] = new HeartbeatMonitor(k, Wire.PERIODO_HB_MS, Wire.FATOR_TIMEOUT, log);
            Thread subscription = new Thread(() -> {
                try {
                    pubSub.subscribe(Wire.topicData(partition), message -> {
                        try {
                            // O produtor só precisa dos metadados do líder e
                            // do lote; não aplica nem interpreta os registos.
                            Batch header = Batch.decodeHeader(message);
                            int count = Integer.parseInt(message.split("\\R", 2)[0].split(";", -1)[8]);
                            detectors[partition].onHeartbeat(header.term, header.leader, header.hb);
                            // O líder fecha listas contíguas; não é preciso interpretar os registos.
                            long from = count == 0 ? 0 : header.lastSeq - count + 1;
                            long to = count == 0 ? 0 : header.lastSeq;
                            log.accept("RECEBIDO;p" + partition + ";termo=" + header.term + ";lider=n" + header.leader + ";hb=" + header.hb + ";ultimoSeq=" + header.lastSeq + ";n=" + count + ";de=" + from + ";ate=" + to);
                        } catch (IllegalArgumentException e) {
                            System.out.println("LOTE rejeitado: " + e.getMessage());
                        }
                    });
                } catch (IOException e) {
                    System.out.println("Erro de subscrição: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "produtor-p" + k);
            subscription.setDaemon(true);
            subscription.start();
        }
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        timer.scheduleAtFixedRate(() -> {
            for (HeartbeatMonitor detector : detectors) detector.check();
        }, Wire.VERIFICACAO_MS, Wire.VERIFICACAO_MS, TimeUnit.MILLISECONDS);
        System.out.println("Pausa entre envios da rajada: " + INTERVALO_MS + " ms; <s> continua a ser o número de sensores.");

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
                    // Uma leitura externa não traz dependências causais: L/V
                    // começam em zero. O líder atribui seq, termo e os novos L/V.
                    String message = Wire.in(p[0], temperature, 0L, new VectorClock(partitioner.count()));
                    int k = partitioner.of(p[0]);
                    warn(k, p[0]);
                    long sent = System.currentTimeMillis();
                    pubSub.publish(Wire.topicIn(k), message);
                    sent(k, p[0], temperature, sent);
                    System.out.println("Publicado em " + Wire.topicIn(k) + ": " + message + " → p" + k);
                } catch (IOException e) {
                    System.out.println("Erro de publicação (" + e.getClass().getSimpleName() + "): " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Publicação interrompida: " + e.getMessage());
        } finally {
            timer.shutdownNow();
        }
    }

    // Uma linha por publicação concluída, tanto manual como em rajada.
    // O timestamp é capturado antes do HTTP; não é uma confirmação do líder.
    private static synchronized void sent(int k, String sensor, double temperature, long stamp) throws IOException {
        Files.writeString(Path.of("produtor.txt"), stamp+";p"+k+";"+sensor+";"+temperature+System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    // Avisar não bloqueia o envio nem implementa retenção/reenvio automático.
    private static void warn(int k, String sensor) {
        if (detectors[k].state() == HeartbeatMonitor.State.SUSPEITO) {
            System.out.println("AVISO: partição p" + k + " sem líder conhecido; leitura " + sensor + " pode perder-se");
        }
    }

    private static void publishBurst(IpfsPubSub pubSub, Partitioner partitioner, Random random, int n, int s, boolean hot) throws IOException, InterruptedException {
        long firstSendTime = 0;
        long lastSendTime = 0;
        long[] counts = new long[partitioner.count()];
        for (int i = 0; i < n; i++) {
            int sensorNumber = hot ? (random.nextDouble() < 0.8 ? 1 : random.nextInt(s - 1) + 2) : random.nextInt(s) + 1;
            String sensor = (sensorNumber < 10 ? "S0" : "S") + sensorNumber;
            double temperature = 15.0 + random.nextDouble() * 15.0;
            String message = Wire.in(sensor, temperature, 0L, new VectorClock(partitioner.count()));
            int k = partitioner.of(sensor);
            if (i == 0) firstSendTime = System.currentTimeMillis();
            warn(k, sensor);
            long sent = System.currentTimeMillis();
            pubSub.publish(Wire.topicIn(k), message);
            sent(k, sensor, temperature, sent);
            counts[k]++;
            if (i == n - 1) lastSendTime = System.currentTimeMillis();
            System.out.println("Publicado em " + Wire.topicIn(k) + ": " + message + " → p" + k);
            if (i + 1 < n) Thread.sleep(INTERVALO_MS);
        }
        System.out.println("Rajada concluída: " + n + " registos enviados");
        System.out.println("Primeiro envio (ms): " + firstSendTime);
        System.out.println("Último envio (ms): " + lastSendTime);
        for (int k = 0; k < counts.length; k++) {
            System.out.println("Envios p" + k + ": " + counts[k]);
        }
    }
}
