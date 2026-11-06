package rep04;

import java.util.ArrayList;
import java.util.List;

public final class ProtocolTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(String message) {
        try {
            Batch.decode(message, 3);
            throw new AssertionError("Mensagem inválida aceite: " + message);
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException expected) {
            // Rejeição esperada.
        }
    }

    public static void main(String[] args) throws Exception {
        SensorRecord a = new SensorRecord(1, 1, VectorClock.parse("1,0,0", 3),
                "S01", 21.5, "2026-11-03T10:00:00.123");
        SensorRecord b = new SensorRecord(2, 2, VectorClock.parse("2,0,0", 3),
                "S04", 22, "2026-11-03T10:00:00.456");
        Batch empty = new Batch("127.0.0.1", 7000, 100, 1, 0, List.of());
        require(Batch.decode(empty.encode(), 3).records.isEmpty(), "Lote vazio");
        List<SensorRecord> mutable = new ArrayList<>(List.of(a, b));
        Batch batch = new Batch("127.0.0.1", 7000, 100, 2, 2, mutable);
        mutable.clear();
        Batch decoded = Batch.decode(batch.encode(), 3);
        require(decoded.records.size() == 2 && decoded.records.get(1).toLine().equals(b.toLine()),
                "Round trip com duas linhas e milissegundos");
        require(Batch.decode(batch.encode().replace("\n", "\r\n"), 3).records.size() == 2, "CRLF");
        require(Batch.decodeHeader(batch.encode()).count == 2, "Cabeçalho independente");
        rejects(batch.encode().replace(";2\n", ";3\n"));
        rejects(empty.encode() + "\n" + a.toLine());
        rejects("LOTE;x;0;1;1;0;0");
        rejects("LOTE;x;7000;1;0;0;0");
        rejects("LOTE;x;7000;1;1;0;-1");
        rejects("LOTE;x;7000;1;1;1;1\n1;S01;NaN;2026-11-03T10:00;1;1,0,0");
        rejects("LOTE;x;7000;1;1;1;1\n1;S01;20;data-invalida;1;1,0,0");

        List<String> events = new ArrayList<>();
        HeartbeatMonitor monitor = new HeartbeatMonitor(0, 10, 3, events::add);
        require(monitor.state() == HeartbeatMonitor.State.DESCONHECIDO, "Inicial");
        monitor.onHeartbeat(100, 1);
        Thread.sleep(50);
        monitor.onHeartbeat(100, 1); // não pode renovar o timeout
        monitor.check();
        require(monitor.state() == HeartbeatMonitor.State.SUSPEITO, "Duplicado não renova");
        monitor.onHeartbeat(100, 3);
        require(monitor.state() == HeartbeatMonitor.State.ATIVO, "Retoma");
        require(events.stream().anyMatch(x -> x.startsWith("HB_PERDIDO")), "Salto de hb");
        require(events.stream().anyMatch(x -> x.startsWith("RETOMA")), "Evento RETOMA");
        monitor.onHeartbeat(200, 1);
        require(events.stream().anyMatch(x -> x.startsWith("REINICIO")), "Nova incarnação");
        Thread.sleep(50);
        monitor.onHeartbeat(100, 99); // incarnação antiga também não renova
        monitor.check();
        require(monitor.state() == HeartbeatMonitor.State.SUSPEITO, "Incarnação antiga ignorada");
        System.out.println("ProtocolTest PASS: lotes, validacao, duplicados, timeout e reinicio.");
    }
}
