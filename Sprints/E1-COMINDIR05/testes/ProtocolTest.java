package raft04;
import java.nio.file.*;
import java.util.*;
public final class ProtocolTest {
    private static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    private static void rejects(Runnable action){try{action.run();throw new AssertionError("Aceitou mensagem invalida");}catch(IllegalArgumentException|java.time.DateTimeException expected){}}
    public static void main(String[] args)throws Exception{
        Path dir=Path.of(args[0]);Files.createDirectories(dir);List<String> events=new ArrayList<>();
        Path state=dir.resolve("unit-state.txt");RaftState r=new RaftState(1,1,3,1,1,state,events::add);
        Thread.sleep(4);long term=r.tick();require(term==1,"Eleicao inicial");require(Files.readString(state).trim().equals("1;1"),"Persistencia antes de publicar");
        require(!r.onVote(1,1,1,true),"Voto proprio duplicado nao e maioria");
        require(r.onVote(1,2,1,true),"Dois votos elegem");require(r.termIfLeader()==1,"Papel lider");
        require(!r.onBatch(0,2),"Rejeita termo antigo");require(r.onBatch(2,2),"Demissao por termo superior");require(r.termIfLeader()==-1,"Demissao");
        RaftState restart=new RaftState(1,1,3,1000,1000,state,events::add);
        require(restart.term()==2&&restart.role()==RaftState.Role.SEGUIDOR,"Reinicio como seguidor");
        require(restart.onVoteRequest(3,2,4,2,3,2).granted,"Candidato atualizado");
        require(!restart.onVoteRequest(3,3,4,2,3,2).granted,"Nao vota duas vezes");
        RaftState again=new RaftState(1,1,3,1000,1000,state,events::add);
        require(!again.onVoteRequest(3,3,9,2,3,2).granted,"Voto sobrevive ao reinicio");
        require(!again.onVoteRequest(4,3,99,1,3,2).granted,"Termo ultimo registo tem prioridade");
        SensorRecord a=new SensorRecord(1,1,"S01",20,"2026-11-05T10:00:00",1,VectorClock.parse("1,0,0",3));
        Batch b=new Batch(1,1,"127.0.0.1",7100,1,1,1,List.of(a));
        require(Batch.decode(b.encode(),3).records.get(0).toLine().equals(a.toLine()),"Round trip");
        require(Batch.decode(new Batch(1,1,"x",7100,2,1,1,List.of()).encode(),3).records.isEmpty(),"Heartbeat vazio");
        rejects(()->Batch.decode(b.encode()+"\n"+a.toLine(),3));rejects(()->VoteReply.decode("VOTO;1;2;1;X"));
        require(VoteRequest.decode(new VoteRequest(1,1,0,0).encode()).lastSeq==0,"Pedido votos");
        require(VoteReply.decode(new VoteReply(1,2,1,true).encode()).granted,"Resposta votos");
        RecordFile f=new RecordFile(dir.resolve("unit-log.txt").toString(),3);f.append(a);
        SensorRecord replacement=new SensorRecord(1,2,"S04",21,"2026-11-05T10:00:01",1,VectorClock.parse("1,0,0",3));
        f.replaceFrom(1,List.of(replacement));require(new RecordFile(dir.resolve("unit-log.txt").toString(),3).lastTerm()==2,"Truncagem atomica");
        List<String> mon=new ArrayList<>();HeartbeatMonitor h=new HeartbeatMonitor(0,1,2,mon::add);
        h.onHeartbeat(1,1,1);Thread.sleep(5);h.onHeartbeat(1,1,1);h.check();require(h.state()==HeartbeatMonitor.State.SUSPEITO,"Duplicado nao renova");
        require(!h.onHeartbeat(0,2,99),"Monitor rejeita termo antigo");h.onHeartbeat(2,2,1);require(h.state()==HeartbeatMonitor.State.ATIVO,"Novo lider retoma");
        recoveryBlocksAndStaleReply();
        System.out.println("ProtocolTest PASS: maioria, votos persistentes, upToDate, demissao, formatos, vazio, truncagem e monitor");
    }

    private static SensorRecord record(int seq,int term){return new SensorRecord(seq,term,"S01",20,"2026-11-05T10:00:00",seq,VectorClock.parse(seq+",0,0",3));}

    private static void recoveryBlocksAndStaleReply()throws Exception{
        RecordFile original=new RecordFile("no-p0-n1.txt",3);
        for(int seq=1;seq<=6;seq++)original.append(record(seq,(seq+1)/2));
        java.lang.reflect.Constructor<PartitionNode> ctor=PartitionNode.class.getDeclaredConstructor(int.class,int.class,int.class);ctor.setAccessible(true);
        PartitionNode node=ctor.newInstance(0,1,3);
        java.lang.reflect.Field stateField=PartitionNode.class.getDeclaredField("raft");stateField.setAccessible(true);RaftState state=(RaftState)stateField.get(node);
        java.lang.reflect.Field fileField=PartitionNode.class.getDeclaredField("file");fileField.setAccessible(true);RecordFile data=(RecordFile)fileField.get(node);
        java.lang.reflect.Method recover=PartitionNode.class.getDeclaredMethod("recover",Batch.class,long.class);recover.setAccessible(true);
        List<SensorRecord> remote=List.of(record(1,1),record(2,1),record(3,4),record(4,4));
        List<Long> requests=Collections.synchronizedList(new ArrayList<>());
        try(java.net.ServerSocket server=new java.net.ServerSocket(0)){
            server.setSoTimeout(5000);
            Thread responder=new Thread(()->{
                try{for(int attempt=0;attempt<2;attempt++)try(java.net.Socket socket=server.accept()){
                    java.io.BufferedReader in=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),java.nio.charset.StandardCharsets.UTF_8));
                    java.io.PrintWriter out=new java.io.PrintWriter(socket.getOutputStream(),true);
                    String[] request=in.readLine().split(" ");long from=Long.parseLong(request[1]);requests.add(from);
                    for(SensorRecord rec:remote)if(rec.getSeq()>=from)out.println(rec.toLine());out.println("FIM");
                }}catch(Exception e){throw new RuntimeException(e);}
            });responder.start();state.onBatch(4,2);
            recover.invoke(node,new Batch(4,2,"127.0.0.1",server.getLocalPort(),1,4,4,List.of()),0L);
            responder.join(6000);require(!responder.isAlive(),"Servidor de teste bloqueado");
        }
        require(requests.equals(List.of(3L,1L)),"Nao recuou pelos blocos: "+requests);
        require(data.lastSeq()==4&&data.at(2).getTerm()==1&&data.at(3).getTerm()==4,"Truncagem por blocos falhou");
        try(java.net.ServerSocket server=new java.net.ServerSocket(0)){
            Thread responder=new Thread(()->{
                try(java.net.Socket socket=server.accept()){
                    java.io.BufferedReader in=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));in.readLine();
                    state.onBatch(5,2);
                    java.io.PrintWriter out=new java.io.PrintWriter(socket.getOutputStream(),true);out.println(remote.get(2).toLine());out.println("FIM");
                }catch(Exception e){throw new RuntimeException(e);}
            });responder.start();
            recover.invoke(node,new Batch(4,2,"127.0.0.1",server.getLocalPort(),2,3,4,List.of()),1L);
            responder.join(6000);
        }
        require(state.term()==5&&data.lastSeq()==4,"Resposta TCP obsoleta alterou o historico");
        System.out.println("RecoveryTest PASS: recuo 5->3->1, preservacao do prefixo e resposta TCP obsoleta rejeitada");
    }
}
