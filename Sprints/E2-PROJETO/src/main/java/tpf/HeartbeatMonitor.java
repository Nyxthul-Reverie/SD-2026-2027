package tpf;

public final class HeartbeatMonitor {
    public enum Estado { VIVA, SUSPEITA, MORTA }

    private long ultimoHeartbeat;
    private Estado estado = Estado.MORTA;

    Estado estado() {
        return estado;
    }

    long ultimoHeartbeat() {
        return ultimoHeartbeat;
    }

    void onHeartbeat(long agora) {
        ultimoHeartbeat = agora;
        estado = Estado.VIVA;
    }

    void atualizarEstado(long agora) {
        if (estado == Estado.MORTA) {
            return;
        }
        long silencio = agora - ultimoHeartbeat;
        if (silencio >= Wire.MORTA_MS) {
            estado = Estado.MORTA;
        } else if (silencio >= Wire.SUSPEITA_MS) {
            estado = Estado.SUSPEITA;
        }
    }
}
