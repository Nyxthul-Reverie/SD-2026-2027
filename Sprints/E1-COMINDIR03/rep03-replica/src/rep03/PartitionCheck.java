package rep03;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PartitionCheck {
    private static final int R = 2;
    private static final Pattern LEADER = Pattern.compile("lider-p(\\d+)\\.txt");
    private static final Pattern REPLICA = Pattern.compile("replica-(.+)-p(\\d+)\\.txt");

    private PartitionCheck() { }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Uso: PartitionCheck <N> <pasta>");
            System.exit(1);
        }
        try {
            Partitioner partitioner = new Partitioner(Integer.parseInt(args[0]));
            if (!verify(partitioner, Paths.get(args[1]))) System.exit(1);
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("PartitionCheck FAIL: " + e.getMessage());
            System.exit(1);
        }
    }

    private static boolean verify(Partitioner partitioner, Path folder) throws IOException {
        if (!Files.isDirectory(folder)) throw new IllegalArgumentException("Pasta inexistente: " + folder);
        List<Path> files;
        try (Stream<Path> stream = Files.list(folder)) {
            files = stream.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        }
        Map<Integer, List<String>> leaders = new HashMap<>();
        Map<Integer, List<Path>> replicas = new HashMap<>();
        boolean valid = true;
        long systemTotal = 0;
        for (Path path : files) {
            String name = path.getFileName().toString();
            Matcher leader = LEADER.matcher(name);
            Matcher replica = REPLICA.matcher(name);
            if (!leader.matches() && !replica.matches()) continue;
            boolean isLeader = leader.matches();
            int k = Integer.parseInt(isLeader ? leader.group(1) : replica.group(2));
            if (k >= partitioner.count()) {
                System.out.println("ERRO: partição fora de N em " + name);
                valid = false;
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            valid &= sequence(path, lines, partitioner.count());
            if (isLeader) {
                if (leaders.put(k, lines) != null) {
                    System.out.println("ERRO: mais de um ficheiro de líder para p" + k);
                    valid = false;
                }
                systemTotal += lines.size();
            } else {
                replicas.computeIfAbsent(k, ignored -> new ArrayList<>()).add(path);
            }
        }

        long sum = 0;
        for (int k = 0; k < partitioner.count(); k++) {
            List<String> source = leaders.get(k);
            if (source == null) {
                System.out.println("ERRO: falta lider-p" + k + ".txt");
                valid = false;
                continue;
            }
            Set<String> sensors = new HashSet<>();
            for (String line : source) {
                try {
                    SensorRecord record = SensorRecord.fromLine(line, partitioner.count());
                    sensors.add(record.getSensorId());
                    if (partitioner.of(record.getSensorId()) != k) {
                        System.out.println("ERRO: sensor " + record.getSensorId() + " na partição errada p" + k);
                        valid = false;
                    }
                } catch (IllegalArgumentException e) {
                    valid = false;
                }
            }
            sum += source.size();
            List<Path> copies = replicas.getOrDefault(k, new ArrayList<>());
            System.out.println("p" + k + ": registos=" + source.size() + ", sensores=" + sensors.size()
                    + ", réplicas=" + copies.size() + " (R=" + R + ")");
            if (copies.size() != R) {
                System.out.println("ERRO: p" + k + " requer exatamente " + R + " réplicas");
                valid = false;
            }
            for (Path copy : copies) {
                List<String> lines = Files.readAllLines(copy, StandardCharsets.UTF_8);
                boolean prefix = lines.size() <= source.size()
                        && source.subList(0, lines.size()).equals(lines);
                long missing = source.size() - lines.size();
                System.out.println("  " + copy.getFileName() + ": "
                        + (prefix ? (missing == 0 ? "IGUAL" : "PREFIXO") : "DIVERGENTE")
                        + ", faltam=" + Math.max(0, missing));
                if (!prefix) valid = false;
            }
        }
        System.out.println("total sistema=" + systemTotal + "; soma das partições=" + sum);
        valid &= systemTotal == sum;
        System.out.println("PartitionCheck " + (valid ? "PASS" : "FAIL"));
        return valid;
    }

    private static boolean sequence(Path path, List<String> lines, int n) {
        boolean valid = true;
        for (int i = 0; i < lines.size(); i++) {
            try {
                String line = lines.get(i);
                if (line.split(";", -1).length != 6) throw new IllegalArgumentException("formato inválido");
                SensorRecord record = SensorRecord.fromLine(line, n);
                if (record.getSeq() != i + 1L || record.getSensorId().isBlank()
                        || !Double.isFinite(record.getTemperature())) {
                    throw new IllegalArgumentException("seq esperado=" + (i + 1L) + ", obtido=" + record.getSeq());
                }
                LocalDateTime.parse(record.getTimestamp());
            } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
                System.out.println("ERRO: " + path.getFileName() + ", linha " + (i + 1) + ": " + e.getMessage());
                valid = false;
            }
        }
        return valid;
    }
}
