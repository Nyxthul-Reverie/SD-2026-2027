package raft04;
import java.util.function.Consumer;
/**
 * Detetor local do produtor/processador: observa liderança, não participa
 * na eleição. SUSPEITO significa silêncio acima do limite, não prova de falha.
 * Subscrição e verificador periódico partilham o estado sob o mesmo monitor.
 */
public final class HeartbeatMonitor {
    public enum State {DESCONHECIDO,ATIVO,SUSPEITO}
    private State state=State.DESCONHECIDO;
    private long term=-1,hb,lastNanos=System.nanoTime();
    private int leader;
    private final int k;private final long timeout;private final Consumer<String> log;
    public HeartbeatMonitor(int k,long period,int factor,Consumer<String> log){this.k=k;timeout=period*factor*1000000L;this.log=log;}
    /**
     * Rejeita termos inferiores ou outro líder no mesmo termo. Um hb repetido
     * pode ser aceite sem renovar o prazo: repetir mensagens antigas não deve
     * fazer parecer vivo um líder que deixou de publicar heartbeats novos.
     * Ao mudar de termo, a contagem hb pode recomeçar sem indicar perda.
     */
    public synchronized boolean onHeartbeat(long t,int l,long h){
        if(t<term)return false;
        if(t==term&&l!=leader){log.accept("ERRO;p"+k+";dois_lideres_termo="+t);return false;}
        if(t==term&&h<=hb)return true;
        if(t>term)log.accept("NOVO_LIDER;p"+k+";termo="+t+";lider=n"+l);
        else if(h!=hb+1)log.accept("HB_PERDIDO;p"+k+";esperado="+(hb+1)+";recebido="+h);
        if(state==State.SUSPEITO)log.accept("RETOMA;p"+k+";termo="+t+";lider=n"+l);
        term=t;leader=l;hb=h;lastNanos=System.nanoTime();state=State.ATIVO;return true;
    }
    // nanoTime é usado para durações; o log usa timestamps físicos comparáveis
    // entre processos. A suspeita, por si só, não impede o cliente de publicar IN.
    public synchronized void check(){long elapsed=System.nanoTime()-lastNanos;if(state!=State.SUSPEITO&&elapsed>timeout){state=State.SUSPEITO;log.accept("SUSPEITA;p"+k+";silencio_ms="+elapsed/1000000);}}
    public synchronized State state(){return state;}
}
