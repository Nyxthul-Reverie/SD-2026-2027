package tpf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Chunk {
    private static final int FIXO_POR_REGISTO = 4 * Long.BYTES + Integer.BYTES; // calcula quantos bytes cada registo ocupa no chunk antes de contar o texto
    // *4 cuz 4 variables long (lsn, instance, autor, canal) -> cada uma tem 8 bytes
    // basicamente 36 bytes por registo sem contar o text 
    private final List<Registo> registos = new ArrayList<>();
    private int tamanho = Integer.BYTES;
    private boolean selado;

    public int adicionar(Registo registo) { // Add registo ao chunk
        if(selado) {
            throw new IllegalStateException("O chunk já está selado.");
        }
        int bytesTexto = registo.texto().getBytes(StandardCharsets.UTF_8).length;
        tamanho = Math.addExact(tamanho, Math.addExact(FIXO_POR_REGISTO, bytesTexto)); // ArithmeticException aqui se os bytes passarem de 2.something bilhoes
        registos.add(registo);
        return tamanho;
    }

    public long primeiroLsn() { // primeiro registo
        return registos.get(0).lsn();
    }

    public long ultimoLsn() { // ultimo registo
        return registos.get(registos.size() - 1).lsn();
    }

    public byte[] selar() { // selar o chunk
        if (selado || registos.isEmpty()) {
            throw new IllegalStateException("O chunk está vazio ou já foi selado.");
        }
        int resto = tamanho % Wire.PAD_BYTES;
        int padding = resto == 0 ? 0 : Wire.PAD_BYTES - resto;
        ByteBuffer buffer = ByteBuffer.allocate(Math.addExact(tamanho, padding));
        buffer.putInt(registos.size());
        for (Registo registo : registos) {
            byte[] texto = registo.texto().getBytes(StandardCharsets.UTF_8);
            buffer.putLong(registo.lsn());
            buffer.putLong(registo.instante());
            buffer.putLong(registo.autor());
            buffer.putLong(registo.canal());
            buffer.putInt(texto.length);
            buffer.put(texto);
        }
        selado = true;
        return buffer.array();
    }

    public static List<Registo> ler(byte[] dados) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(dados);
        if (buffer.remaining() < Integer.BYTES) {
            throw new IOException("Falta o contador de registos.");
        }
        int quantidade = buffer.getInt();
        if (quantidade <= 0 || quantidade > buffer.remaining() / FIXO_POR_REGISTO) {
            throw new IOException("Contador de registos inválido.");
        }
        List<Registo> resultado = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            if (buffer.remaining() < FIXO_POR_REGISTO) {
                throw new IOException("Registo incompleto.");
            }
            long lsn = buffer.getLong();
            long instante = buffer.getLong();
            long autor = buffer.getLong();
            long canal = buffer.getLong();
            int tamanhoTexto = buffer.getInt();
            if (tamanhoTexto < 0 || tamanhoTexto > buffer.remaining()) {
                throw new IOException("Tamanho de texto inválido.");
            }
            byte[] texto = new byte[tamanhoTexto];
            buffer.get(texto);
            resultado.add(new Registo(lsn, instante, autor, canal,
                    new String(texto, StandardCharsets.UTF_8)));
        }
        return List.copyOf(resultado);
    }
}
