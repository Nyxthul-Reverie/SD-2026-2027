package tpf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Catalog {
    private final Path ficheiro;
    private final Map<String, EntradaCatalogo> entradas = new LinkedHashMap<>();

    public Catalog(Path pasta) throws IOException {
        Files.createDirectories(pasta);
        ficheiro = pasta.resolve("catalogo.txt");
        if (Files.exists(ficheiro)) {
            carregar();
        }
    }

    public synchronized boolean adicionar(String chunkId, long lsnDe, long lsnAte, long tamanho)
            throws IOException {
        EntradaCatalogo atual = entradas.get(chunkId);
        if (atual != null) {
            if (atual.lsnDe() != lsnDe || atual.lsnAte() != lsnAte || atual.tamanho() != tamanho) {
                throw new IOException("Metadados divergentes para " + chunkId);
            }
            return false;
        }
        if (!Wire.chunkIdValido(chunkId) || lsnDe <= 0 || lsnAte < lsnDe || tamanho <= 0) {
            throw new IllegalArgumentException("Metadados de chunk inválidos.");
        }
        substituir(new EntradaCatalogo(chunkId, lsnDe, lsnAte, tamanho, Set.of(), estado(0)));
        return true;
    }

    public synchronized EntradaCatalogo confirmar(String chunkId, String id) throws IOException {
        EntradaCatalogo anterior = entradas.get(chunkId);
        if (anterior == null || !Wire.idValido(id)) {
            throw new IllegalArgumentException("Chunk ou réplica inválidos.");
        }
        var copias = new LinkedHashSet<>(anterior.replicas());
        if (!copias.add(id)) {
            return anterior;
        }
        var novo = new EntradaCatalogo(chunkId, anterior.lsnDe(), anterior.lsnAte(),
                anterior.tamanho(), copias, estado(copias.size()));
        substituir(novo);
        return novo;
    }

    public synchronized Set<String> copias(String chunkId) {
        EntradaCatalogo entrada = entradas.get(chunkId);
        return entrada == null ? Set.of() : entrada.replicas();
    }

    synchronized boolean contem(String chunkId) {
        return entradas.containsKey(chunkId);
    }

    public synchronized List<EntradaCatalogo> entradas() {
        return List.copyOf(entradas.values());
    }

    public synchronized void gravarAtomico() throws IOException {
        gravarAtomico(entradas);
    }

    private void substituir(EntradaCatalogo nova) throws IOException {
        var propostas = new LinkedHashMap<>(entradas);
        propostas.put(nova.chunkId(), nova);
        // Só se publica a alteração em memória depois de a escrita ter terminado.
        gravarAtomico(propostas);
        entradas.put(nova.chunkId(), nova);
    }

    private void gravarAtomico(Map<String, EntradaCatalogo> propostas) throws IOException {
        var texto = new StringBuilder();
        for (EntradaCatalogo e : propostas.values()) {
            texto.append(e.chunkId()).append(';').append(e.lsnDe()).append(';').append(e.lsnAte())
                    .append(';').append(e.tamanho()).append(';').append(String.join(",", e.replicas()))
                    .append(';').append(e.estado()).append('\n');
        }
        ArquivosAtomicos.publicar(ficheiro, texto.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void carregar() throws IOException {
        boolean recalculado = false;
        try {
            for (String linha : Files.readAllLines(ficheiro, StandardCharsets.UTF_8)) {
                String[] campos = linha.split(";", -1);
                if (campos.length != 6 || !Wire.chunkIdValido(campos[0])) {
                    throw new IllegalArgumentException("Linha inválida.");
                }
                var ids = new LinkedHashSet<String>();
                if (!campos[4].isEmpty()) {
                    for (String id : campos[4].split(",", -1)) {
                        if (!Wire.idValido(id) || !ids.add(id)) {
                            throw new IllegalArgumentException("Lista de réplicas inválida.");
                        }
                    }
                }
                var e = new EntradaCatalogo(campos[0], Long.parseLong(campos[1]),
                        Long.parseLong(campos[2]), Long.parseLong(campos[3]), ids, estado(ids.size()));
                if (e.lsnDe() <= 0 || e.lsnAte() < e.lsnDe() || e.tamanho() <= 0
                        || entradas.putIfAbsent(e.chunkId(), e) != null) {
                    throw new IllegalArgumentException("Metadados inválidos ou repetidos.");
                }
                recalculado |= EntradaCatalogo.Estado.valueOf(campos[5]) != e.estado();
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("catalogo.txt inválido.", e);
        }
        if (recalculado) {
            gravarAtomico();
        }
    }

    private static EntradaCatalogo.Estado estado(int copias) {
        return copias >= Wire.acksNecessarios()
                ? EntradaCatalogo.Estado.DURAVEL : EntradaCatalogo.Estado.SELADO;
    }
}
