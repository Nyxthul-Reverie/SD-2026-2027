package tpf;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record EntradaCatalogo(String chunkId, long lsnDe, long lsnAte, long tamanho,
                              Set<String> replicas, Estado estado) {
    public enum Estado { SELADO, DURAVEL }

    public EntradaCatalogo {
        replicas = Collections.unmodifiableSet(new LinkedHashSet<>(replicas));
    }

    public boolean subreplicado() {
        return replicas.size() < Wire.R;
    }
}
