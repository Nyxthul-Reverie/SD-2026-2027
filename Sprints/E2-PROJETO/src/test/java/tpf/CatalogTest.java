package tpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CatalogTest {
    @TempDir
    Path pasta;

    @Test
    void maioriaMinimoAbsolutoECopiasDistintas() throws Exception {
        String id = "a".repeat(64);
        var catalogo = new Catalog(pasta);
        assertTrue(catalogo.adicionar(id, 1, 3, 4096));
        assertFalse(catalogo.adicionar(id, 1, 3, 4096));
        int limiar = Math.max(Wire.W_MIN, Wire.R / 2 + 1);
        assertEquals(limiar, Wire.acksNecessarios());
        for (int i = 1; i <= Math.max(limiar, Wire.R); i++) {
            var e = catalogo.confirmar(id, "r" + i);
            assertEquals(i, e.replicas().size());
            assertEquals(i < Wire.R, e.subreplicado());
            assertEquals(i < limiar ? EntradaCatalogo.Estado.SELADO : EntradaCatalogo.Estado.DURAVEL, e.estado());
            assertEquals(e, catalogo.confirmar(id, "r" + i));
        }
        assertEquals(catalogo.entradas(), new Catalog(pasta).entradas());
        var campos = Files.readString(pasta.resolve("catalogo.txt")).strip().split(";", -1);
        assertEquals(List.of(id, "1", "3", "4096"), List.of(campos).subList(0, 4));
        assertEquals(catalogo.copias(id), Set.of(campos[4].split(",")));
        assertEquals("DURAVEL", campos[5]);
    }
}
