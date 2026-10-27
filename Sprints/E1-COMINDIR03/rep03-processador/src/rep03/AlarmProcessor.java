package rep03;

import java.util.Set;
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

        synchronized (monitor) {
            String key = "p" + k + "#" + record.getSeq();

            if (!seen.add(key)) { // Same message -> Ignore / seen.add(key) == false
                return;
            }

            lamport = Math.max(lamport, record.getLamport());
            vector.merge(record.getVector());
        }

    }

    public static void main(String[] args) {

    }
}
