package raft04;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
/**
 * Registo imutável: seq;term;sensor;temperatura;timestamp;L;V.
 * seq ordena a partição, term identifica a época em que o evento foi criado,
 * L/V representam causalidade e timestamp é apenas o instante físico.
 * O vetor é copiado ao entrar e sair para não alterar um evento já gravado.
 */
public final class SensorRecord {
    private final long seq,term,lamport;
    private final VectorClock vector;
    private final String sensor,timestamp;
    private final double temperature;
    public SensorRecord(long seq,long term,String sensor,double temperature,String timestamp,long lamport,VectorClock vector) {
        if(seq<1||term<1||lamport<1||sensor.isBlank()||sensor.contains(";")||!Double.isFinite(temperature))throw new IllegalArgumentException("Registo invalido");
        LocalDateTime.parse(timestamp);
        this.seq=seq; this.term=term; this.sensor=sensor; this.temperature=temperature;
        this.timestamp=timestamp; this.lamport=lamport; this.vector=vector.copy();
    }
    public static SensorRecord now(long seq,long term,String s,double t,long l,VectorClock v) {
        return new SensorRecord(seq,term,s,t,LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS).toString(),l,v);
    }
    public String toLine(){return seq+";"+term+";"+sensor+";"+temperature+";"+timestamp+";"+lamport+";"+vector;}
    public static SensorRecord fromLine(String s,int n) {
        String[] f=s.split(";",-1); if(f.length!=7)throw new IllegalArgumentException("Esperados sete campos");
        VectorClock v=VectorClock.parse(f[6],n); for(int k=0;k<n;k++)if(v.get(k)<0)throw new IllegalArgumentException("V negativo");
        return new SensorRecord(Long.parseLong(f[0]),Long.parseLong(f[1]),f[2],Double.parseDouble(f[3]),f[4],Long.parseLong(f[5]),v);
    }
    public long getSeq(){return seq;} public long getTerm(){return term;} public long getLamport(){return lamport;}
    public String getSensorId(){return sensor;} public double getTemperature(){return temperature;}
    public String getTimestamp(){return timestamp;} public VectorClock getVector(){return vector.copy();}
}
