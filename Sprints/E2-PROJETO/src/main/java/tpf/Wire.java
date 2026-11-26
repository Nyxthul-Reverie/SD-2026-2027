package tpf;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class Wire {
    public static final int CHUNK_MAX_BYTES = positive("tpf.chunkMax", 65_536); // max bypes per chunk antes da selagem
    public static final int PAD_BYTES = positive("tpf.pad", 4_096); // zeros -> pading
    public static final int SELAGEM_MS = positive("tpf.selagem", 5_000); // tempo de 
    public static final int PORTO = positive("tpf.porto", 8765);
    public static final int PERIODO_HB_MS = positive("tpf.periodoHbMs", 2_000);
    public static final int SUSPEITA_MS = positive("tpf.suspeitaMs", 6_000);
    public static final int MORTA_MS = positive("tpf.mortaMs", 10_000);
    public static final int TOKEN_VALIDADE_MS = positive("tpf.tokenValidadeMs", 600_000);
    public static final int R = positive("tpf.r", 3);
    public static final int W_MIN = positive("tpf.wMin", 2);
    public static final int ACK_TIMEOUT_MS = positive("tpf.ackTimeoutMs", 4_000);
    public static final int MAX_RONDAS = positive("tpf.maxRondas", 3);
    public static final long MARGEM_BYTES = Long.parseLong(System.getProperty("tpf.margemBytes", "65536"));
    public static final double ALFA = Double.parseDouble(System.getProperty("tpf.alfa", "1.0"));
    public static final int BACKOFF_MIN_MS = positive("tpf.backoffMinMs", 1_000);
    public static final int BACKOFF_MAX_MS = positive("tpf.backoffMaxMs", 30_000);

    static final String TOPICO_LIDER = "tpf-lider";
    static final String TOPICO_CONTROLO = "tpf-ctl";
    private static final Pattern ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");
    private static final Pattern CHUNK_ID = Pattern.compile("[0-9a-f]{64}");

    static {
        if (PORTO > 65_535 || MORTA_MS <= SUSPEITA_MS || BACKOFF_MAX_MS < BACKOFF_MIN_MS
                || MARGEM_BYTES < 0 || !Double.isFinite(ALFA) || ALFA < 0) {
            throw new IllegalArgumentException("Parâmetros E2-03 inválidos.");
        }
    }

    public static int acksNecessarios() {
        return Math.max(W_MIN, R / 2 + 1);
    }

    static boolean idValido(String id) {
        return ID.matcher(id).matches();
    }

    static boolean chunkIdValido(String id) {
        return CHUNK_ID.matcher(id).matches();
    }

    static String topicoReplica(String id) {
        return "tpf-r-" + id;
    }

    static byte[] dadosDaProva(String id, String nonceB64) {
        return ("tpf-registo|" + id + "|" + nonceB64).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível no JDK.", e);
        }
    }

    static String hash(byte[] bytes) {
        return HexFormat.of().formatHex(sha256(bytes));
    }

    private Wire() {

    }

    private static int positive(String property, int defaultValue) {
        int value = Integer.parseInt(System.getProperty(property, String.valueOf(defaultValue)));

        if (value <= 0) {
            throw new IllegalArgumentException(property + " tem de ser maior que zero.");
        }

        return value;
    }

}
