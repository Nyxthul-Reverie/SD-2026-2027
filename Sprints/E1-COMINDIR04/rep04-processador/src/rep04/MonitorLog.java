package rep04;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

// As threads das várias partições partilham um ficheiro de eventos.
public final class MonitorLog implements Consumer<String> {
    private final Path path;

    public MonitorLog(String id) {
        path = Path.of("monitor-" + id + ".txt");
    }

    @Override
    public synchronized void accept(String event) {
        String line = System.currentTimeMillis() + ";" + event;
        System.out.println(line);
        try {
            Files.writeString(path, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Erro ao escrever " + path + ": " + e.getMessage());
        }
    }

    public void received(int k, long inc, long hb, long lastSeq, int count, long from, long to) {
        accept("RECEBIDO;p" + k + ";inc=" + inc + ";hb=" + hb + ";ultimoSeq=" + lastSeq
                + ";n=" + count + ";de=" + from + ";ate=" + to);
    }

    public void received(int k, Batch batch) {
        long from = batch.records.isEmpty() ? 0 : batch.records.get(0).getSeq();
        long to = batch.records.isEmpty() ? 0 : batch.records.get(batch.records.size() - 1).getSeq();
        received(k, batch.inc, batch.hb, batch.lastSeq, batch.records.size(), from, to);
    }
}
