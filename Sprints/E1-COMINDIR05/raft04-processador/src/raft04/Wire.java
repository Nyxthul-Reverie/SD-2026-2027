package raft04;
import java.nio.file.*;
public final class Wire {
    public static final long PERIODO_HB_MS=Long.getLong("raft04.periodo",2000L);
    public static final int FATOR_TIMEOUT=Integer.getInteger("raft04.fator",3);
    public static final long VERIFICACAO_MS=200;
    public static final long ELEICAO_MIN_MS=Long.getLong("raft04.eleicaoMin",3*PERIODO_HB_MS);
    public static final long ELEICAO_MAX_MS=Long.getLong("raft04.eleicaoMax",6*PERIODO_HB_MS);
    public static final int GRUPO=3, T_TCP_MS=5000;
    public static final double LIMIAR_ALARME=28.0;
    static {
        if(PERIODO_HB_MS<=0 || FATOR_TIMEOUT<=0 || ELEICAO_MIN_MS<=0 || ELEICAO_MAX_MS<ELEICAO_MIN_MS)
            throw new IllegalArgumentException("Temporizacao invalida");
    }
    private Wire() {}
    public static String api() { return System.getProperty("raft04.api","http://127.0.0.1:5001/api/v0"); }
    public static String topicIn(int k) { return "raft04-in-p"+k; }
    public static String topicData(int k) { return "raft04-p"+k; }
    public static String topicElection(int k) { return "raft04-eleicao-p"+k; }
    public static int nodePort(int k,int i) { return Integer.getInteger("raft04.portoBase",7100)+k*GRUPO+i-1; }
    public static boolean isolated(int k,int i) { return Files.exists(Path.of("isolar-p"+k+"-n"+i)); }
    public static String in(String s,double t,long l,VectorClock v) { return "IN;"+s+";"+t+";"+l+";"+v; }
    public static String type(String s) { int p=s.indexOf(';'); if(p<1)throw new IllegalArgumentException("Tipo ausente"); return s.substring(0,p); }
    public static String body(String s) { type(s); return s.substring(s.indexOf(';')+1); }
    public static int node(int i) { if(i<1||i>GRUPO)throw new IllegalArgumentException("No invalido"); return i; }
}
