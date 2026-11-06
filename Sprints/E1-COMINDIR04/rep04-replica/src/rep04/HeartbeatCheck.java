package rep04;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

// Analisa os ficheiros depois do ensaio. Não participa no protocolo.
public final class HeartbeatCheck {
    private static final class Stats {
        long count;
        double sum, max = Double.NEGATIVE_INFINITY, min = Double.POSITIVE_INFINITY;
        void add(double value) {
            count++;
            sum += value;
            max = Math.max(max, value);
            min = Math.min(min, value);
        }
        String mean() { return count == 0 ? "NA" : number(sum / count); }
        String maximum() { return count == 0 ? "NA" : number(max); }
        String minimum() { return count == 0 ? "NA" : number(min); }
    }

    private static final class Published {
        int partition, count;
        long inc, hb, time, lastSeq, bytes;
        Published(int k, String line) {
            String[] f = line.split(";", -1);
            if (f.length != 6) throw new IllegalArgumentException("Linha hb inválida: " + line);
            partition = k;
            inc = Long.parseLong(f[0]);
            hb = Long.parseLong(f[1]);
            time = Long.parseLong(f[2]);
            lastSeq = Long.parseLong(f[3]);
            count = Integer.parseInt(f[4]);
            bytes = Long.parseLong(f[5]);
            if (inc < 0 || hb < 1 || lastSeq < 0 || count < 0 || bytes < 0) {
                throw new IllegalArgumentException("Valores hb inválidos.");
            }
        }
    }

    private static final class Event {
        long time;
        String type;
        int partition;
        Map<String, String> details = new HashMap<>();
        Event(String line) {
            String[] f = line.split(";", -1);
            if (f.length < 3 || !f[2].startsWith("p")) {
                throw new IllegalArgumentException("Evento inválido: " + line);
            }
            time = Long.parseLong(f[0]);
            type = f[1];
            partition = Integer.parseInt(f[2].substring(1));
            for (int i = 3; i < f.length; i++) {
                int equals = f[i].indexOf('=');
                if (equals > 0) details.put(f[i].substring(0, equals), f[i].substring(equals + 1));
            }
        }
        long value(String key) {
            if (!details.containsKey(key)) throw new IllegalArgumentException("Falta " + key + " no evento.");
            return Long.parseLong(details.get(key));
        }
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static List<String> lines(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        List<String> result = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) result.add(line);
        }
        return result;
    }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Uso: HeartbeatCheck <N> <pasta> [falhas.txt]");
            System.exit(1);
        }
        try {
            int n = Integer.parseInt(args[0]);
            if (n < 1) throw new IllegalArgumentException("N deve ser positivo.");
            analyse(n, Path.of(args[1]), args.length == 3 ? Path.of(args[2]) : null);
        } catch (IOException | RuntimeException e) {
            System.err.println("HeartbeatCheck FAIL: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void analyse(int n, Path folder, Path failureFile) throws IOException {
        if (!Files.isDirectory(folder)) throw new IllegalArgumentException("Pasta inexistente.");
        List<Published> publications = new ArrayList<>();
        Map<String, List<Published>> groups = new LinkedHashMap<>();
        List<Map<Long, SensorRecord>> records = new ArrayList<>();
        List<List<Long>> failures = new ArrayList<>();
        Stats[] latency = new Stats[n];
        long allBytes = 0, dataBytes = 0, transported = 0, equivalentBytes = 0;
        int empty = 0;
        for (int k = 0; k < n; k++) {
            records.add(new TreeMap<>());
            failures.add(new ArrayList<>());
            latency[k] = new Stats();
            for (String line : lines(folder.resolve("lider-p" + k + ".txt"))) {
                SensorRecord record = SensorRecord.fromLine(line, n);
                records.get(k).put(record.getSeq(), record);
            }
            for (String line : lines(folder.resolve("hb-p" + k + ".txt"))) {
                Published hb = new Published(k, line);
                publications.add(hb);
                groups.computeIfAbsent("p" + k + ";inc=" + hb.inc, key -> new ArrayList<>()).add(hb);
                allBytes += hb.bytes;
                transported += hb.count;
                if (hb.count == 0) empty++;
                else dataBytes += hb.bytes;
                // O ensaio usa o endereço local e os portos definidos em Wire.
                for (long seq = hb.lastSeq - hb.count + 1; seq <= hb.lastSeq && hb.count > 0; seq++) {
                    SensorRecord record = records.get(k).get(seq);
                    if (record != null) {
                        equivalentBytes += ("REG;127.0.0.1;" + (Wire.PORTO_BASE + k) + ";"
                                + record.toLine()).getBytes(StandardCharsets.UTF_8).length;
                    }
                }
            }
        }
        if (publications.isEmpty()) throw new IllegalArgumentException("Sem ficheiros de heartbeats publicados.");
        for (Map.Entry<String, List<Published>> group : groups.entrySet()) {
            Stats sizes = new Stats(), intervals = new Stats();
            int emptyGroup = 0;
            Published previous = null;
            for (Published hb : group.getValue()) {
                sizes.add(hb.count);
                if (hb.count == 0) emptyGroup++;
                if (previous != null) intervals.add(hb.time - previous.time);
                previous = hb;
            }
            System.out.println("LOTES;" + group.getKey() + ";total=" + sizes.count
                    + ";vazios_pct=" + number(100.0 * emptyGroup / sizes.count)
                    + ";n_media=" + sizes.mean() + ";n_max=" + sizes.maximum()
                    + ";intervalo_media_ms=" + intervals.mean() + ";intervalo_max_ms=" + intervals.maximum());
        }
        if (failureFile != null) {
            if (!Files.isRegularFile(failureFile)) throw new IOException("Ficheiro de falhas inexistente.");
            for (String line : lines(failureFile)) {
                String[] f = line.split(";", -1);
                if (f.length != 2 || !f[0].startsWith("p")) throw new IllegalArgumentException("Falha inválida.");
                int k = Integer.parseInt(f[0].substring(1));
                failures.get(k).add(Long.parseLong(f[1]));
            }
        }

        Stats detection = new Stats();
        int real = 0, falseSuspicions = 0, unknown = 0, lost = 0, recoveries = 0, inconsistent = 0;
        List<Path> monitorFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "monitor-*.txt")) {
            for (Path path : stream) monitorFiles.add(path);
        }
        monitorFiles.sort(Comparator.comparing(Path::toString));
        for (Path path : monitorFiles) {
            String participant = path.getFileName().toString().replace("monitor-", "").replace(".txt", "");
            List<Event> events = new ArrayList<>();
            for (String line : lines(path)) events.add(new Event(line));
            long[] incarnation = new long[n], heartbeat = new long[n];
            Arrays.fill(incarnation, -1);
            Set<String> measured = new HashSet<>();
            Map<Integer, Long> usedFailure = new HashMap<>();
            for (int i = 0; i < events.size(); i++) {
                Event event = events.get(i);
                int k = event.partition;
                if (k < 0 || k >= n) throw new IllegalArgumentException("Partição inválida no log.");
                if (event.type.equals("HB_PERDIDO")) lost++;
                if (event.type.equals("RECUPERACAO")) recoveries++;
                if (event.type.equals("RECEBIDO")) {
                    long inc = event.value("inc"), hb = event.value("hb");
                    if (inc > incarnation[k] || (inc == incarnation[k] && hb > heartbeat[k])) {
                        incarnation[k] = inc;
                        heartbeat[k] = hb;
                    }
                    long count = event.value("n"), from = event.value("de"), to = event.value("ate");
                    if (count > 0 && (to - from + 1 != count || to != event.value("ultimoSeq"))) {
                        inconsistent++;
                        System.out.println("AVISO_CABECALHO;" + participant + ";p" + k + ";hb=" + hb);
                    }
                    for (long seq = from; count > 0 && seq <= to; seq++) {
                        SensorRecord record = records.get(k).get(seq);
                        if (record != null && measured.add(k + ":" + seq)) {
                            long sent = LocalDateTime.parse(record.getTimestamp())
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                            latency[k].add(event.time - sent);
                        }
                    }
                }
                if (event.type.equals("SUSPEITA")) {
                    long inc = incarnation[k];
                    boolean resumed = false;
                    for (int j = i + 1; j < events.size(); j++) {
                        Event next = events.get(j);
                        if (next.partition == k && next.type.equals("RETOMA") && next.value("inc") == inc) {
                            resumed = true;
                            break;
                        }
                    }
                    String classification;
                    if (inc < 0) { classification = "INDETERMINADA"; unknown++; }
                    else if (resumed) { classification = "FALSA_SUSPEITA"; falseSuspicions++; }
                    else { classification = "FALHA_REAL"; real++; }
                    long publishedTime = -1;
                    for (Published hb : publications) {
                        if (hb.partition == k && hb.inc == inc && hb.time <= event.time) {
                            publishedTime = Math.max(publishedTime, hb.time);
                        }
                    }
                    Long failure = null;
                    if (!resumed && inc >= 0) {
                        for (long time : failures.get(k)) {
                            if (time >= inc && time <= event.time
                                    && time > usedFailure.getOrDefault(k, -1L)
                                    && (failure == null || time > failure)) failure = time;
                        }
                    }
                    String elapsed = "NA";
                    if (failure != null) {
                        detection.add(event.time - failure);
                        elapsed = Long.toString(event.time - failure);
                        usedFailure.put(k, failure);
                    }
                    System.out.println("SUSPEITA;" + participant + ";p" + k + ";inc=" + inc
                            + ";instante=" + event.time + ";classe=" + classification
                            + ";silencio_publicacao_ms=" + (publishedTime < 0 ? "NA" : event.time - publishedTime)
                            + ";detecao_ms=" + elapsed);
                }
            }
        }
        for (int k = 0; k < n; k++) {
            System.out.println("LATENCIA;p" + k + ";amostras=" + latency[k].count
                    + ";media_ms=" + latency[k].mean() + ";max_ms=" + latency[k].maximum());
        }
        System.out.println("RESUMO;lotes=" + publications.size() + ";vazios=" + empty
                + ";registos_transportados=" + transported + ";falhas_reais=" + real
                + ";falsas_suspeitas=" + falseSuspicions + ";indeterminadas=" + unknown
                + ";HB_PERDIDO=" + lost + ";RECUPERACAO=" + recoveries + ";cabecalhos_incoerentes=" + inconsistent);
        System.out.println("DETECAO;amostras=" + detection.count + ";min_ms=" + detection.minimum()
                + ";media_ms=" + detection.mean() + ";max_ms=" + detection.maximum());
        System.out.println("BYTES;LOTE_total_por_registo=" + (transported == 0 ? "NA" : number((double) allBytes / transported))
                + ";LOTE_com_dados_por_registo=" + (transported == 0 ? "NA" : number((double) dataBytes / transported))
                + ";REG_equivalente=" + (transported == 0 ? "NA" : number((double) equivalentBytes / transported)));
        System.out.println("NOTAS: bytes UTF-8 da mensagem, sem HTTP/IPFS; REG equivalente usa 127.0.0.1 e 7000+k.");
        System.out.println("Latências: primeira receção de cada registo por participante, agregadas por partição; TCP excluído.");
        System.out.println("Falha real é a classificação operacional da ficha no intervalo observado, não uma prova de morte.");
        System.out.println("NA significa ausência de amostras. Executar na máquina/fuso que produziu os logs.");
    }
}
