package rep02;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PartitionLeader {
    static final String DEFAULT_ADDRESS = "127.0.0.1";
    static final String DEFAULT_API = "http://127.0.0.1:5001/api/v0";

    private final RecordFile file;
    // Depois do arranque, todos os acessos a lastSeq usam o monitor de file.
    private long lastSeq;

    private PartitionLeader(RecordFile file) throws IOException {
        this.file = file;
        this.lastSeq = file.lastSeq();
    }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 4) {
            System.out.println("Uso: PartitionLeader <k> <N> [endereço] [api]");
            return;
        }
        int k;
        int n;
        try {
            k = Integer.parseInt(args[0]);
            n = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Argumentos inválidos: k e N têm de ser inteiros.");
            return;
        }
        if (n < 1 || k < 0 || k >= n) {
            System.out.println("Argumentos inválidos: N >= 1 e 0 <= k < N.");
            return;
        }
        String endereco = args.length > 2 ? args[2] : DEFAULT_ADDRESS;
        String api = args.length > 3 ? args[3] : DEFAULT_API;
        int porto = Wire.PORTO_BASE + k;
        if (k > 65535 - Wire.PORTO_BASE) {
            System.out.println("Argumentos inválidos: porto de recuperação fora do intervalo TCP.");
            return;
        }
        IpfsPubSub ps = new IpfsPubSub(api);
        Partitioner partitioner = new Partitioner(n);
        RecordFile file = new RecordFile("lider-p" + k + ".txt");

        try {
            PartitionLeader leader = new PartitionLeader(file);
            // Abrir o porto antes de arrancar a entrada evita aceitar leituras se o bind falhar.
            try (ServerSocket server = new ServerSocket(porto)) {
                Thread entrada = new Thread(() -> {
                    try {
                        ps.subscribe(Wire.topicIn(k), message -> leader.receive(ps, partitioner, k, endereco, porto, message));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.out.println("Entrada interrompida: " + e.getMessage());
                    } catch (IOException e) {
                        System.out.println("Erro de subscrição: " + e.getMessage());
                    }
                }, "entrada");
                entrada.start();
                System.out.println("Líder p" + k + " pronto; recuperação TCP no porto " + porto);

                while (true) {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(Wire.T_TCP_MS);
                        leader.recover(socket);
                    } catch (IOException e) {
                        System.out.println("Recuperação interrompida: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao iniciar líder: " + e.getMessage());
        }
    }

    private void receive(IpfsPubSub ps, Partitioner partitioner, int k, String endereco, int porto, String message) {
        String sensor;
        double temperature;
        try {
            if (!Wire.type(message).equals("IN")) {
                throw new IllegalArgumentException("Tipo inválido");
            }
            String[] fields = Wire.body(message).split(";", -1);
            if (fields.length != 2 || fields[0].isBlank()) {
                throw new IllegalArgumentException("Corpo inválido");
            }
            sensor = fields[0];
            temperature = Double.parseDouble(fields[1]);
            if (!Double.isFinite(temperature)) {
                throw new IllegalArgumentException("Temperatura inválida");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("REJEITADO: " + message);
            return;
        }

        int j = partitioner.of(sensor);
        if (j != k) {
            System.out.println("PARTIÇÃO ERRADA: " + sensor + " pertence a p" + j);
            return;
        }

        try {
            SensorRecord record;
            synchronized (file) {
                record = SensorRecord.now(lastSeq + 1, sensor, temperature);
                file.append(record);
                lastSeq = record.getSeq();
            }
            ps.publish(Wire.topicData(k), Wire.reg(endereco, porto, record));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Publicação interrompida: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Erro ao registar/publicar: " + e.getMessage());
        }
    }

    private void recover(Socket socket) throws IOException {
        try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            String pedido = in.readLine();
            long from;
            long to;
            try {
                if (pedido == null) {
                    throw new IllegalArgumentException("pedido ausente");
                }
                String[] fields = pedido.trim().split("\\s+");
                if (fields.length != 3 || !fields[0].equals("PEDIDO")) {
                    throw new IllegalArgumentException("formato esperado: PEDIDO <de> <até>");
                }
                from = Long.parseLong(fields[1]);
                to = Long.parseLong(fields[2]);
                if (from > to) {
                    throw new IllegalArgumentException("de não pode ser superior a até");
                }
            } catch (IllegalArgumentException e) {
                out.println("ERRO " + e.getMessage());
                if (out.checkError()) throw new IOException("Falha ao enviar ERRO.");
                return;
            }

            List<SensorRecord> records;
            synchronized (file) {
                to = Math.min(to, lastSeq);
                records = file.range(from, to);
            }
            for (SensorRecord record : records) {
                out.println(record.toLine());
            }
            out.println("FIM");
            if (out.checkError()) throw new IOException("Falha ao enviar resposta de recuperação.");
            System.out.println("RECUPERAÇÃO: " + from + ".." + to + " para "
                    + socket.getRemoteSocketAddress() + " (" + records.size() + " registos)");
        }
    }
}
