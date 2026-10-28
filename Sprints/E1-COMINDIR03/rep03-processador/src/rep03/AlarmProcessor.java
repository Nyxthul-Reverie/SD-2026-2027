package rep03;

import java.util.Set;
import java.io.IOException;
import java.util.HashSet;

public class AlarmProcessor {
    private static final String DEFAULT_API = "http://127.0.0.1:5001/api/v0";
    private final int n;
    private final Object monitor = new Object();

    private long lamport;
    private final VectorClock vector;
    private final Set<String> seen = new HashSet<>();

    private final IpfsPubSub pubSub;
    private final Partitioner partitioner;

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
    }

    private void receive(int k, String message) {
        SensorRecord record;

        try {
            String[] parts = Wire.regParts(message);
            record = SensorRecord.fromLine(parts[2], n);
        } catch (IllegalArgumentException e) {
            System.out.println("REJEITADO: " + message);
            return;
        }

        if (record.getSensorId().startsWith("ALARM-")) {
            return;
        }

        long alarmLamport;
        VectorClock alarmVector;

        synchronized (monitor) {
            String key = "p" + k + "#" + record.getSeq();

            if (!seen.add(key)) { // Same message -> Ignore / seen.add(key) == false
                return;
            }

            // Atualiza os conhecimentos para todos os registos normais
            lamport = Math.max(lamport, record.getLamport());
            vector.merge(record.getVector());

            // Registo a baixo do limiar do alarme atualizam o clock mas obviamente nao
            // criam alarme
            if (record.getTemperature() < Wire.LIMIAR_ALARME) {
                return;
            }

            alarmLamport = lamport;
            alarmVector = vector.copy();
        }

        String alarmSensor = "ALARM-" + record.getSensorId();
        int j = partitioner.of(alarmSensor);
        String alarmMessage = Wire.in(alarmSensor, record.getTemperature(), alarmLamport, alarmVector);

        try {
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
