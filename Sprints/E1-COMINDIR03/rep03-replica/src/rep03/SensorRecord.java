package rep03;

import java.time.LocalDateTime;

public class SensorRecord {
    private final long seq;
    private final long lamport;
    private final VectorClock vector;
    private final String sensorId;
    private final double temperature;
    private final String timestamp;

    public SensorRecord(long seq, long lamport, VectorClock vector, String sensorId, double temperature, String timestamp) {
        this.seq = seq;
        this.lamport = lamport;
        this.vector = vector;
        this.sensorId = sensorId;
        this.temperature = temperature;
        this.timestamp = timestamp;
    }

    // cria um registo com o instante atual (usado só pelo líder)
    public static SensorRecord now(long seq,  String sensorId, double temperature, long lamport, VectorClock vector) {
        return new SensorRecord(seq, lamport, vector, sensorId, temperature, LocalDateTime.now().withNano(0).toString());
    }

    // representação textual: é o que se guarda no ficheiro e o que se envia pela rede
    public String toLine() {
        return seq + ";" + sensorId + ";" + temperature + ";" + timestamp + ";" + lamport + ";" + vector;
    }

    public static SensorRecord fromLine(String line, int n) {
        String[] f = line.trim().split(";", -1); // Para preservar todos os campos mesmo que estejam vazios
        if (f.length != 6) {
            throw new IllegalArgumentException("Registo mal formado: " + line);
        }
        return new SensorRecord(Long.parseLong(f[0]), Long.parseLong(f[4]), VectorClock.parse(f[5], n), f[1], Double.parseDouble(f[2]), f[3]);
    }

    public long getSeq() { return seq; }
    public long getLamport() { return lamport; }
    public VectorClock getVector() { return vector; }
    public String getSensorId() { return sensorId; }
    public double getTemperature() { return temperature; }
    public String getTimestamp() { return timestamp; }
}
