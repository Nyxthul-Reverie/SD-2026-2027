package rep03;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

public final class CausalCheck {
    private CausalCheck() {
    }

    private static final class Event {
        private final int partition;
        private final SensorRecord record;

        private Event(int partition, SensorRecord record) {
            this.partition = partition;
            this.record = record;
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Uso: CausalCheck <N> <pasta>");
            return;
        }

        try {
            int n = Integer.parseInt(args[0]);
            if (n < 1) {
                throw new IllegalArgumentException("N tem de ser pelo menos 1.");
            }

            Path folder = Paths.get(args[1]);
            analyse(n, folder);
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("CausalCheck FAIL: " + e.getMessage());
        }
    }

    private static void analyse(int n, Path folder) throws IOException {
        List<Event> events = new ArrayList<>();
        long vectorViolations = 0;
        long aBeforeB = 0;
        long bBeforeA = 0;
        long concurrent = 0;
        long concurrentDifferentLamport = 0;
        long lamportViolations = 0;
        long equalVectors = 0;

        for (int k = 0; k < n; k++) {
            Path file = folder.resolve("lider-p" + k + ".txt");

            for (String line : Files.readAllLines(
                    file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }

                SensorRecord record = SensorRecord.fromLine(line, n);

                if (record.getVector().get(k) != record.getSeq()) {
                    vectorViolations++;

                    System.out.println("VIOLAÇÃO V[k] == seq: p" + k + " seq=" + record.getSeq() + " V=" + record.getVector());

                }

                events.add(new Event(k, record));
            }

        }
        System.out.println("Registos lidos: " + events.size());
        System.out.println("Violações V[k] == seq: " + vectorViolations);
        for (int i = 0; i < events.size(); i++) {
            for (int j = i + 1; j < events.size(); j++) {
                Event a = events.get(i);
                Event b = events.get(j);

                if (a.partition == b.partition) {
                    continue;
                }

                VectorClock.Order order = a.record.getVector().compare(b.record.getVector());
                switch (order) {
                    case BEFORE:
                        aBeforeB++;

                        if (a.record.getLamport() >= b.record.getLamport()) {
                            lamportViolations++;

                            System.out.println(
                                    "VIOLAÇÃO L: p" + a.partition
                                            + "#" + a.record.getSeq()
                                            + " → p" + b.partition
                                            + "#" + b.record.getSeq());
                        }
                        break;

                    case AFTER:
                        bBeforeA++;

                        if (b.record.getLamport() >= a.record.getLamport()) {
                            lamportViolations++;

                            System.out.println(
                                    "VIOLAÇÃO L: p" + b.partition
                                            + "#" + b.record.getSeq()
                                            + " → p" + a.partition
                                            + "#" + a.record.getSeq());
                        }
                        break;

                    case CONCURRENT:
                        concurrent++;

                        if (a.record.getLamport() != b.record.getLamport()) {
                            concurrentDifferentLamport++;
                        }
                        break;

                    case EQUAL:
                        equalVectors++;

                        System.out.println(
                                "VETORES IGUAIS EM EVENTOS DISTINTOS: "
                                        + "p" + a.partition + "#"
                                        + a.record.getSeq()
                                        + " e p" + b.partition + "#"
                                        + b.record.getSeq());
                        break;
                }
            }
        }
        long causalTotal = aBeforeB + bBeforeA;

        double proportion = concurrent == 0 ? 0.0 : 100.0 * concurrentDifferentLamport / concurrent;

        System.out.println("Pares a → b: " + aBeforeB);
        System.out.println("Pares b → a: " + bBeforeA);
        System.out.println("Pares causalmente relacionados: " + causalTotal);
        System.out.println("Pares concorrentes: " + concurrent);
        System.out.println("Violações da condição de Lamport: " + lamportViolations);
        System.out.println("Vetores iguais em eventos distintos: " + equalVectors);

        System.out.printf("Pares concorrentes com L distinto: %d (%.2f%%)%n", concurrentDifferentLamport, proportion);
        List<Event> ordered = new ArrayList<>(events);

        ordered.sort(Comparator.comparingLong((Event e) -> e.record.getLamport()).thenComparingInt(e -> e.partition));
        List<String> viewLines = new ArrayList<>();

        for (Event event : ordered) {
            viewLines.add("p" + event.partition + ";" + event.record.toLine());
        }
        Path viewFile = folder.resolve("vista-lamport.txt");

        Files.write(viewFile, viewLines, StandardCharsets.UTF_8);

        System.out.println("Vista Lamport: " + viewFile);
    }
}