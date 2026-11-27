package tpf;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record Registo(long lsn, long instante, long autor, long canal, String texto) {
    public String linhaDoLog() {
        String base64 = Base64.getEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
        return lsn + ";" + instante + ";" + autor + ";" + canal + ";" + base64;
    }

    public static Registo lerLinha(String linha) {
        String[] campos = linha.split(";", -1);
        if (campos.length != 5) {
            throw new IllegalArgumentException("Esperavam-se 5 campos em registos.log.");
        }

        return new Registo(
                Long.parseLong(campos[0]),
                Long.parseLong(campos[1]),
                Long.parseLong(campos[2]),
                Long.parseLong(campos[3]),
                new String(Base64.getDecoder().decode(campos[4]), StandardCharsets.UTF_8));
    }
}