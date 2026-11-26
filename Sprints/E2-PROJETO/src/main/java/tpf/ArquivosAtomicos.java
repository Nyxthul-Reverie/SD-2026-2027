package tpf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;

final class ArquivosAtomicos {
    private ArquivosAtomicos() {
    }

    static void publicar(Path destino, byte[] bytes, FileAttribute<?>... atributos) throws IOException {
        Path tmp = Files.createTempFile(destino.getParent(), ".tpf-", ".tmp", atributos);
        try {
            try (FileChannel out = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
                escreverTudo(out, bytes);
            }
            Files.move(tmp, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    static void escreverTudo(FileChannel out, byte[] bytes) throws IOException {
        ByteBuffer pendente = ByteBuffer.wrap(bytes);
        while (pendente.hasRemaining()) {
            out.write(pendente);
        }
        out.force(true);
    }

    // O líder partilha o ficheiro de eventos entre a selagem e a replicação.
    static synchronized void evento(Path ficheiro, String mensagem) throws IOException {
        Files.writeString(ficheiro, System.currentTimeMillis() + ";" + mensagem + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static FileAttribute<?> permissoesDaChave(Path pasta) throws IOException {
        var store = Files.getFileStore(pasta);
        if (store.supportsFileAttributeView("posix")) {
            return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
        }
        if (store.supportsFileAttributeView("acl")) {
            var dono = pasta.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            var entrada = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(dono)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
            return new FileAttribute<List<AclEntry>>() {
                @Override
                public String name() {
                    return "acl:acl";
                }

                @Override
                public List<AclEntry> value() {
                    return List.of(entrada);
                }
            };
        }
        throw new IOException("A pasta de dados tem de suportar permissões POSIX ou ACL.");
    }
}
