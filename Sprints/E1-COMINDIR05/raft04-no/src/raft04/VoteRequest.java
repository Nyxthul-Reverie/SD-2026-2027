package raft04;

/**
 * Pedido de voto enviado por um candidato após guardar o termo e o voto próprio.
 * Formato: PEDIDO_VOTO;termo;candidato;últimoSeq;últimoTermo.
 * O primeiro termo pertence à eleição. O último termo pertence ao histórico
 * do candidato e, juntamente com o último seq, permite avaliar se está atualizado.
 * Um histórico vazio é representado pelo par últimoSeq=0 e últimoTermo=0.
 */
public final class VoteRequest {
    public final long term,lastSeq,lastTerm;public final int candidate;
    public VoteRequest(long t,int c,long s,long st){if(t<1||s<0||st<0||(s==0)!=(st==0)||st>t)throw new IllegalArgumentException("Pedido invalido");term=t;candidate=Wire.node(c);lastSeq=s;lastTerm=st;}
    public String encode(){return "PEDIDO_VOTO;"+term+";"+candidate+";"+lastSeq+";"+lastTerm;}
    public static VoteRequest decode(String s){String[]f=s.split(";",-1);if(f.length!=5||!f[0].equals("PEDIDO_VOTO"))throw new IllegalArgumentException("Pedido invalido");return new VoteRequest(Long.parseLong(f[1]),Integer.parseInt(f[2]),Long.parseLong(f[3]),Long.parseLong(f[4]));}
}
