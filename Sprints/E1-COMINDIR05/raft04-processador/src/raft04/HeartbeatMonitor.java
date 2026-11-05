package raft04;
import java.util.function.Consumer;
public final class HeartbeatMonitor {
    public enum State {DESCONHECIDO,ATIVO,SUSPEITO}
    private State state=State.DESCONHECIDO;
    private long term=-1,hb,lastNanos=System.nanoTime();
    private int leader;
    private final int k;private final long timeout;private final Consumer<String> log;
    public HeartbeatMonitor(int k,long period,int factor,Consumer<String> log){this.k=k;timeout=period*factor*1000000L;this.log=log;}
    public synchronized boolean onHeartbeat(long t,int l,long h){
        if(t<term)return false;
        if(t==term&&l!=leader){log.accept("ERRO;p"+k+";dois_lideres_termo="+t);return false;}
        if(t==term&&h<=hb)return true;
        if(t>term)log.accept("NOVO_LIDER;p"+k+";termo="+t+";lider=n"+l);
        else if(h!=hb+1)log.accept("HB_PERDIDO;p"+k+";esperado="+(hb+1)+";recebido="+h);
        if(state==State.SUSPEITO)log.accept("RETOMA;p"+k+";termo="+t+";lider=n"+l);
        term=t;leader=l;hb=h;lastNanos=System.nanoTime();state=State.ATIVO;return true;
    }
    public synchronized void check(){long elapsed=System.nanoTime()-lastNanos;if(state!=State.SUSPEITO&&elapsed>timeout){state=State.SUSPEITO;log.accept("SUSPEITA;p"+k+";silencio_ms="+elapsed/1000000);}}
    public synchronized State state(){return state;}
}
