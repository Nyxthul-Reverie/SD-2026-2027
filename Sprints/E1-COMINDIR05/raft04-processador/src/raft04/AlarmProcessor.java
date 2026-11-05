package raft04;

import java.util.Set;
import java.io.IOException;
import java.util.HashSet;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Observa todas as partições e transforma leituras quentes em IN de alarme.
 * Mantém o conhecimento causal recebido; o nó de destino carimba o novo
 * evento. Não confirma escritas por maioria nem desfaz alarmes de ramos truncados.
 */
public class AlarmProcessor {
    private static final String DEFAULT_API = Wire.api();
    private final int n;
    private final Object monitor = new Object();

    private long lamport;
    private final VectorClock vector;
    private final Set<String> seen = new HashSet<>();

    private final IpfsPubSub pubSub;
    private final Partitioner partitioner;
    private final MonitorLog log = new MonitorLog("processador");
    private final HeartbeatMonitor[] detectors;

    public AlarmProcessor(int n, String api) {
        if (n <= 0) {
            throw new IllegalArgumentException("N tem de ser pelo menos 1.");
        }
        this.n = n;

        this.lamport = 0L;
        this.vector = new VectorClock(n);
        if (api == null || api.isBlank()) {
            api = DEFAULT_API;
        }
        this.pubSub = new IpfsPubSub(api);
        this.partitioner = new Partitioner(n);
        this.detectors = new HeartbeatMonitor[n];
        for (int k = 0; k < n; k++) {
            detectors[k] = new HeartbeatMonitor(k, Wire.PERIODO_HB_MS, Wire.FATOR_TIMEOUT, log);
        }
    }

    private void receive(int k, String message) {
        Batch batch;
        try {
            batch = Batch.decode(message, n);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            System.out.println("REJEITADO: " + message);
            return;
        }
        // Rejeitar liderança obsoleta antes de processar o corpo. Cada partição
        // tem o seu maior termo observado e o seu detetor independente.
        if (!detectors[k].onHeartbeat(batch.term, batch.leader, batch.hb)) return;
        log.received(k, batch);
        for (SensorRecord record : batch.records) process(k, record);
    }

    private void process(int k, SensorRecord record) {
        // Evita uma cadeia infinita de alarmes gerados a partir de outros alarmes.
        if (record.getSensorId().startsWith("ALARM-")) {
            return;
        }

        long alarmLamport;
        VectorClock alarmVector;

        synchronized (monitor) {
            // As threads das partições partilham seen, Lamport e vetor.
            // O mesmo seq noutro termo é outro evento e pode gerar novo alarme.
            String key = "p" + k + "#" + record.getSeq() + "@" + record.getTerm();

            if (!seen.add(key)) { // false: este evento já foi tratado.
                return;
            }

            // Integra o conhecimento de todos os registos normais, mesmo frios.
            lamport = Math.max(lamport, record.getLamport());
            vector.merge(record.getVector());

            // Uma leitura abaixo do limiar atualiza o conhecimento, sem criar alarme.
            if (record.getTemperature() < Wire.LIMIAR_ALARME) {
                return;
            }

            // Captura uma cópia coerente para publicar fora do monitor.
            // Não faz tick aqui: o evento aceite na partição de destino fá-lo-á.
            alarmLamport = lamport;
            alarmVector = vector.copy();
        }

        String alarmSensor = "ALARM-" + record.getSensorId();
        int j = partitioner.of(alarmSensor);
        String alarmMessage = Wire.in(alarmSensor, record.getTemperature(), alarmLamport, alarmVector);

        try {
            if (detectors[j].state() == HeartbeatMonitor.State.SUSPEITO) {
                System.out.println("AVISO: partição p" + j + " sem líder conhecido; alarme " + alarmSensor + " pode perder-se");
            }
            // A suspeita é informativa: a ficha manda publicar na mesma,
            // assumindo a possibilidade de perda se não houver líder a aceitar.
            pubSub.publish(Wire.topicIn(j), alarmMessage);

            System.out.println("ALARME: " + record.getSensorId() + " p" + k + "#" + record.getSeq() + " → p" + j);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Publicação interrompida: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Erro ao publicar alarme: " + e.getMessage());
        }
    }

    private void start() {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        timer.scheduleAtFixedRate(() -> {
            for (HeartbeatMonitor detector : detectors) detector.check();
        }, Wire.VERIFICACAO_MS, Wire.VERIFICACAO_MS, TimeUnit.MILLISECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(timer::shutdownNow));
        for (int k = 0; k < n; k++) {
            final int partition = k;

            Thread subscription = new Thread(() -> {
                try {
                    System.out.println("Processador à escuta em " + Wire.topicData(partition));
                    pubSub.subscribe(Wire.topicData(partition), message -> receive(partition, message));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Subscrição interrompida em p" + partition + ": " + e.getMessage());
                } catch (IOException e) {
                    System.out.println("Erro de subscrição em p" + partition + ": " + e.getMessage());
                }
            }, "alarmas-p" + partition);

            subscription.start();
        }
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.out.println("Uso: AlarmProcessor <N> [api]");
            return;
        }

        try {
            int n = Integer.parseInt(args[0]);
            String api = args.length == 2 ? args[1] : DEFAULT_API;

            AlarmProcessor processor = new AlarmProcessor(n, api);
            processor.start();
        } catch (IllegalArgumentException e) {
            System.out.println("Argumentos inválidos: " + e.getMessage());
        }
    }
}
