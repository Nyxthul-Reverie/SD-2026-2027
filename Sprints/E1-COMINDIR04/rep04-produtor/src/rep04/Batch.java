package rep04;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class Batch {
    public final String address;
    public final int port;
    public final long inc, hb, lastSeq;
    public final List<SensorRecord> records;

    public Batch(String address, int port, long inc, long hb, long lastSeq, List<SensorRecord> records) {
        this.address = address;
        this.port = port;
        this.inc = inc;
        this.hb = hb;
        this.lastSeq = lastSeq;
        this.records = List.copyOf(records);
    }

    public String encode() {
        StringBuilder text = new StringBuilder("LOTE;" + address + ";" + port + ";" + inc
                + ";" + hb + ";" + lastSeq + ";" + records.size());
        for (SensorRecord record : records) text.append('\n').append(record.toLine());
        return text.toString();
    }

    // N é a dimensão dos vetores. A quantidade de registos vem do cabeçalho.
    public static Batch decode(String message, int n) {
        Header header = decodeHeader(message);
        String[] lines = message.split("\\R", -1);
        if (lines.length - 1 != header.count) {
            throw new IllegalArgumentException("Quantidade de registos diferente do cabeçalho.");
        }
        List<SensorRecord> records = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            SensorRecord record = SensorRecord.fromLine(lines[i], n);
            if (record.getSeq() < 1 || record.getSensorId().isBlank()
                    || !Double.isFinite(record.getTemperature()) || record.getLamport() < 0) {
                throw new IllegalArgumentException("Registo inválido.");
            }
            LocalDateTime.parse(record.getTimestamp());
            records.add(record);
        }
        return new Batch(header.address, header.port, header.inc, header.hb, header.lastSeq, records);
    }

    // O produtor só precisa do cabeçalho: não descodifica SensorRecord nem vetores.
    public static Header decodeHeader(String message) {
        int end = message.indexOf('\n');
        String line = end < 0 ? message : message.substring(0, end);
        String[] fields = line.stripTrailing().split(";", -1);
        if (fields.length != 7 || !fields[0].equals("LOTE")) {
            throw new IllegalArgumentException("Cabeçalho LOTE inválido.");
        }
        Header header = new Header(fields[1], Integer.parseInt(fields[2]),
                Long.parseLong(fields[3]), Long.parseLong(fields[4]),
                Long.parseLong(fields[5]), Integer.parseInt(fields[6]));
        if (header.address.isBlank() || header.port < 1 || header.port > 65535
                || header.inc < 0 || header.hb < 1 || header.lastSeq < 0 || header.count < 0) {
            throw new IllegalArgumentException("Campos do cabeçalho inválidos.");
        }
        return header;
    }

    public static final class Header {
        public final String address;
        public final int port, count;
        public final long inc, hb, lastSeq;

        private Header(String address, int port, long inc, long hb, long lastSeq, int count) {
            this.address = address;
            this.port = port;
            this.inc = inc;
            this.hb = hb;
            this.lastSeq = lastSeq;
            this.count = count;
        }
    }
}
