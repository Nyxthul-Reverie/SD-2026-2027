package raft04;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
/** Directed execution of the faulty state class; not a network experiment. */
public final class FaultTest {
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    private static Consumer<String> logger(Path dir,int node){
        return event->{try{Files.writeString(dir.resolve("eventos-p0-n"+node+".txt"),System.currentTimeMillis()+";"+event+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}};
    }
    public static void main(String[] args)throws Exception{
        String kind=args[0];Path dir=Path.of(args[1]);
        for(int k=0;k<3;k++)for(int i=1;i<=3;i++)Files.writeString(dir.resolve("no-p"+k+"-n"+i+".txt"),"");
        if(kind.equals("maioria")){
            RaftState s=new RaftState(0,1,3,1,1,dir.resolve("estado-p0-n1.txt"),logger(dir,1));
            Thread.sleep(4);long t=s.tick();
            boolean elected=s.onVote(t,1,1,true);
            require(elected,"Mutacao de maioria nao observada");
            System.out.println("FALHA OBSERVADA: API de estado elege com um voto distinto apos repeticao do proprio voto. O handler de rede filtra o voto proprio; efeito distribuido nao demonstrado por este teste.");
        }else{
            Path vote=dir.resolve("estado-p0-n3.txt");
            RaftState a=new RaftState(0,1,3,1,1,dir.resolve("estado-p0-n1.txt"),logger(dir,1));
            RaftState b=new RaftState(0,2,3,1,1,dir.resolve("estado-p0-n2.txt"),logger(dir,2));
            RaftState v=new RaftState(0,3,3,1000,1000,vote,logger(dir,3));
            Thread.sleep(4);long ta=a.tick(),tb=b.tick();require(ta==tb,"Termos diferentes");
            require(v.onVoteRequest(ta,1,0,0,0,0).granted,"Voto inicial");
            require(a.onVote(ta,3,1,true),"Primeiro lider");
            RaftState reboot=new RaftState(0,3,3,1000,1000,vote,logger(dir,3));
            require(reboot.onVoteRequest(tb,2,0,0,0,0).granted,"Reinicio nao perdeu voto");
            require(b.onVote(tb,3,2,true),"Segundo lider");
            System.out.println("FALHA OBSERVADA: reinicio do eleitor sem persistencia permitiu dois lideres no mesmo termo.");
        }
        System.out.println("FaultTest PASS: mutacao "+kind+" demonstrada por execucao dirigida da classe alterada.");
    }
}
