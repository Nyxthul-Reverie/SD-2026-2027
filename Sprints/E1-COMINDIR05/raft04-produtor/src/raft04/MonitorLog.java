package raft04;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;
public final class MonitorLog implements Consumer<String> {
    private final Path path;
    public MonitorLog(String id){path=Path.of(id.startsWith("eventos-")?id+".txt":"monitor-"+id+".txt");}
    public synchronized void accept(String event) {
        String line=System.currentTimeMillis()+";"+event;System.out.println(line);
        try{Files.writeString(path,line+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(IOException e){throw new UncheckedIOException(e);}
    }
    public void received(int k,Batch b){accept("RECEBIDO;p"+k+";termo="+b.term+";lider=n"+b.leader+";hb="+b.hb+";ultimoSeq="+b.lastSeq+";ultimoTermo="+b.lastTerm+";n="+b.records.size());}
}
