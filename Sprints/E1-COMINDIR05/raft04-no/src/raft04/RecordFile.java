package raft04;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
public final class RecordFile {
    private final Path path;private final int n;
    private List<SensorRecord> records=new ArrayList<>();
    public RecordFile(String name,int n)throws IOException{
        path=Path.of(name);this.n=n;
        if(Files.exists(path))for(String line:Files.readAllLines(path,StandardCharsets.UTF_8))if(!line.isBlank()){
            SensorRecord r=SensorRecord.fromLine(line,n);
            if(r.getSeq()!=records.size()+1L)throw new IOException("Sequencia invalida: "+name);
            records.add(r);
        }
        if(!Files.exists(path))Files.writeString(path,"",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    public synchronized long lastSeq(){return records.size();}
    public synchronized long lastTerm(){return records.isEmpty()?0:records.get(records.size()-1).getTerm();}
    public synchronized SensorRecord last(){return records.isEmpty()?null:records.get(records.size()-1);}
    public synchronized SensorRecord at(long seq){return seq<1||seq>records.size()?null:records.get((int)seq-1);}
    public synchronized List<SensorRecord> snapshot(){return List.copyOf(records);}
    public synchronized List<SensorRecord> range(long from,long to){List<SensorRecord>rs=new ArrayList<>();for(long j=Math.max(1,from);j<=Math.min(to,records.size());j++)rs.add(at(j));return rs;}
    public synchronized void append(SensorRecord r)throws IOException{
        if(r.getSeq()!=records.size()+1L)throw new IOException("Append nao contiguo");
        Files.writeString(path,r.toLine()+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.APPEND);
        records.add(r);
    }
    public synchronized void replaceFrom(long from,List<SensorRecord> tail)throws IOException{
        if(from<1||from>records.size()+1L)throw new IOException("Truncagem invalida");
        List<SensorRecord> next=new ArrayList<>(records.subList(0,(int)from-1));
        for(SensorRecord r:tail){if(r.getSeq()!=next.size()+1L)throw new IOException("Cauda nao contigua");next.add(r);}
        Path tmp=path.resolveSibling(path.getFileName()+".tmp");
        List<String> lines=new ArrayList<>();for(SensorRecord r:next)lines.add(r.toLine());
        Files.write(tmp,lines,StandardCharsets.UTF_8);
        Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        records=next;
    }
}
