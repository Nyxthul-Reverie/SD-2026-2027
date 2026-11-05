package raft04;
import java.util.*;
/**
 * LOTE;term;leader;address;port;hb;lastSeq;lastTerm;n, seguido de n registos.
 * term identifica o líder atual; lastTerm é o termo do último registo local
 * e pode ser anterior. Um lote vazio continua a anunciar liderança/histórico.
 */
public final class Batch {
    public final long term,hb,lastSeq,lastTerm;
    public final int leader,port;
    public final String address;
    public final List<SensorRecord> records;
    public Batch(long t,int l,String a,int p,long h,long s,long st,List<SensorRecord> rs) {
        if(t<1||h<1||s<0||st<0||(s==0)!=(st==0)||st>t||a.isBlank()||p<1||p>65535)throw new IllegalArgumentException("Cabecalho invalido");
        term=t;leader=Wire.node(l);address=a;port=p;hb=h;lastSeq=s;lastTerm=st;records=List.copyOf(rs);
    }
    public String encode(){
        StringBuilder b=new StringBuilder("LOTE;"+term+";"+leader+";"+address+";"+port+";"+hb+";"+lastSeq+";"+lastTerm+";"+records.size());
        for(SensorRecord r:records)b.append('\n').append(r.toLine());return b.toString();
    }
    // Usado pelo produtor: valida o cabeçalho sem carregar o corpo.
    // A lista devolvida fica vazia, mesmo que a mensagem anuncie n > 0.
    public static Batch decodeHeader(String s) {
        String[] f=s.split("\\R",2)[0].split(";",-1);
        if(f.length!=9||!f[0].equals("LOTE"))throw new IllegalArgumentException("Formato LOTE invalido");
        if(Integer.parseInt(f[8])<0)throw new IllegalArgumentException("n negativo");
        return new Batch(Long.parseLong(f[1]),Integer.parseInt(f[2]),f[3],Integer.parseInt(f[4]),Long.parseLong(f[5]),Long.parseLong(f[6]),Long.parseLong(f[7]),List.of());
    }
    // Aqui n é o número de partições (dimensão do vetor), não a contagem
    // de registos do cabeçalho. O corpo tem de ser contíguo e acabar em lastSeq.
    public static Batch decode(String s,int n) {
        Batch h=decodeHeader(s);String[] lines=s.split("\\R",-1);
        int count=Integer.parseInt(lines[0].split(";",-1)[8]);
        if(lines.length!=count+1)throw new IllegalArgumentException("n difere do corpo");
        List<SensorRecord> rs=new ArrayList<>();long prev=-1;
        for(int j=1;j<lines.length;j++){
            SensorRecord r=SensorRecord.fromLine(lines[j],n);
            if(r.getTerm()>h.term||r.getSeq()>h.lastSeq||(prev>=0&&r.getSeq()!=prev+1))throw new IllegalArgumentException("Lote incoerente");
            prev=r.getSeq();rs.add(r);
        }
        if(!rs.isEmpty()&&(prev!=h.lastSeq||rs.get(rs.size()-1).getTerm()!=h.lastTerm))throw new IllegalArgumentException("Ultimo registo incoerente");
        return new Batch(h.term,h.leader,h.address,h.port,h.hb,h.lastSeq,h.lastTerm,rs);
    }
}
