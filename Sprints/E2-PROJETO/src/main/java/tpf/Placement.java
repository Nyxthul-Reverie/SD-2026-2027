package tpf;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class Placement {
    public record Candidato(String id, long livre) {
    }

    private record Pontuacao(String id, double valor) {
    }

    private Placement() {
    }

    public static List<String> choose(String chunkId, long tamanho, int count,
                                      List<Candidato> vivas, Set<String> exclude) {
        return choose(chunkId, tamanho, count, vivas, exclude, Wire.ALFA);
    }

    static List<String> choose(String chunkId, long tamanho, int count,
                               List<Candidato> vivas, Set<String> exclude, double alfa) {
        return vivas.stream()
                .filter(c -> !exclude.contains(c.id()) && c.livre() >= tamanho
                        && c.livre() - tamanho >= Wire.MARGEM_BYTES)
                .map(c -> new Pontuacao(c.id(), pontuacao(chunkId, c, alfa)))
                .sorted(Comparator.comparingDouble(Pontuacao::valor).reversed().thenComparing(Pontuacao::id))
                .limit(count)
                .map(Pontuacao::id)
                .toList();
    }

    private static double pontuacao(String chunkId, Candidato replica, double alfa) {
        byte[] entrada = (chunkId + "|" + replica.id()).getBytes(StandardCharsets.UTF_8);
        long v = ByteBuffer.wrap(Wire.sha256(entrada)).getLong() >>> 11;
        // Calcula -ln(u) pela distância a 1 na metade superior. Assim o último
        // ponto médio continua dentro de ]0,1[, mesmo com arredondamento double.
        double distancia = (0x1f_ffff_ffff_ffffL - v + 0.5) * 0x1.0p-53;
        double denominador = v < (1L << 52)
                ? -StrictMath.log((v + 0.5) * 0x1.0p-53)
                : -StrictMath.log1p(-distancia);
        // O logaritmo preserva a ordem da pontuação e evita calcular potências grandes.
        double logDenominador = StrictMath.log(denominador);
        if (alfa == 0) {
            return -logDenominador;
        }
        double logLivre = StrictMath.log(replica.livre());
        return alfa <= 1 ? alfa * logLivre - logDenominador : logLivre - logDenominador / alfa;
    }
}
