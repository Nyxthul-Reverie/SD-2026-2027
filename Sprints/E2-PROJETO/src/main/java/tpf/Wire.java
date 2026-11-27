package tpf;

public final class Wire {
    public static final int CHUNK_MAX_BYTES = positive("tpf.chunkMax", 65_536); // max bypes per chunk antes da selagem
    public static final int PAD_BYTES = positive("tpf.pad", 4_096); // zeros -> pading
    public static final int SELAGEM_MS = positive("tpf.selagem", 5_000); // tempo de 

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