package raft04;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Nó que alterna entre seguidor, candidato e líder (COMINDIR05, 4.1/3–5).
 * Há uma thread por subscrição, um executor para verificar eleições, outro
 * para enviar heartbeats e workers para responder a pedidos TCP.
 *
 * Ordem dos monitores: lock do nó -> RaftState -> RecordFile/MonitorLog.
 * synchronized(lock) exclui as outras threads que usem ESTE mesmo objeto;
 * não bloqueia automaticamente todo o programa nem a comunicação de rede.
 * O monitor coordena papel/termo com seq, relógios e revisão do histórico.
 * HTTP e TCP decorrem fora dele, para não bloquear IN, eleições e demissões.
 */
public final class PartitionNode {
    private final int k,self,n;
    private final Object lock=new Object();
    private final RecordFile file;
    private final RaftState raft;
    private final MonitorLog log;
    private final IpfsPubSub pubSub=new IpfsPubSub(Wire.api());
    private final ScheduledExecutorService checks=Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService heartbeats=Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> heartbeatJob;
    private final ExecutorService clients=Executors.newCachedThreadPool();
    private List<SensorRecord> pendentes=new ArrayList<>();
    // seq ordena registos; lamport/vector descrevem causalidade; hb conta lotes.
    // revision muda quando o histórico é alterado, invalidando recuperações antigas.
    private long seq,lamport,hb,revision,lastBatchTerm=-1,lastBatchHb;
    private VectorClock vector;
    private final String address=System.getProperty("raft04.endereco","127.0.0.1");

    private PartitionNode(int k,int self,int n)throws IOException{
        this.k=k;this.self=self;this.n=n;
        file=new RecordFile("no-p"+k+"-n"+self+".txt",n);
        log=new MonitorLog("eventos-p"+k+"-n"+self);
        raft=new RaftState(k,self,Wire.GRUPO,Wire.ELEICAO_MIN_MS,Wire.ELEICAO_MAX_MS,
                Path.of("estado-p"+k+"-n"+self+".txt"),log);
        restoreClocks();
        log.accept("ARRANQUE;p"+k+";no=n"+self+";termo="+raft.term()+";ultimoSeq="+seq);
    }

    // Um novo líder continua os relógios do histórico que conservou. Começar
    // em zero após uma eleição poderia quebrar V[k]=seq e a ordem de Lamport.
    // Na promoção, restaura seq, L e V do último registo para continuar a
    // numeração e os relógios lógicos a partir do histórico já guardado.
    private void restoreClocks(){
        SensorRecord last=file.last();seq=file.lastSeq();
        lamport=last==null?0:last.getLamport();
        vector=last==null?new VectorClock(n):last.getVector();
    }

    private void guard(Runnable work){
        try{work.run();}
        // Continuar após falhar a persistência poderia divulgar um voto/termo
        // que seria esquecido no reinício. O nó termina em vez de avançar assim.
        catch(UncheckedIOException e){System.err.println("ERRO FATAL DE PERSISTENCIA: "+e);System.exit(2);}
        catch(RuntimeException e){log.accept("ERRO;p"+k+";"+e.getClass().getSimpleName()+"="+e.getMessage());}
    }

    private void subscribe(String topic,Consumer<String> handler){
        new Thread(()->{
            while(!Thread.currentThread().isInterrupted()){
                try{pubSub.subscribe(topic,message->guard(()->handler.accept(message)));}
                catch(InterruptedException e){Thread.currentThread().interrupt();return;}
                catch(IOException|RuntimeException e){log.accept("ERRO;p"+k+";subscricao="+topic+";"+e.getClass().getSimpleName());}
                try{Thread.sleep(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            }
        },"sub-"+topic+"-n"+self).start();
    }

    // Chamado sob lock. Cancelar o agendamento não retira um HTTP já em trânsito;
    // por isso o recetor também tem de validar o termo de cada lote.
    private void demotionCheck(){
        if(raft.role()!=RaftState.Role.LIDER&&heartbeatJob!=null){
            heartbeatJob.cancel(false);heartbeatJob=null;pendentes=new ArrayList<>();
        }
    }

    // tick persiste o voto próprio. Capturamos também o último registo sob lock
    // e só depois publicamos, sem manter o monitor durante o pedido HTTP.
    private void electionTick(){
        String message=null;
        synchronized(lock){
            long term=raft.tick();demotionCheck();
            if(term>=0)message=new VoteRequest(term,self,file.lastSeq(),file.lastTerm()).encode();
        }
        if(message!=null)publish(Wire.topicElection(k),message);
    }

    /** Trata os dois tipos de mensagem eleitoral sob o mesmo lock usado por IN. */
    private void election(String message){
        if(Wire.isolated(k,self))return;
        String response=null;
        synchronized(lock){
            if(Wire.type(message).equals("PEDIDO_VOTO")){
                VoteRequest request=VoteRequest.decode(message);
                // Pubsub também entrega a publicação ao próprio emissor.
                if(request.candidate==self)return;
                RaftState.Vote vote=raft.onVoteRequest(request.term,request.candidate,request.lastSeq,request.lastTerm,file.lastSeq(),file.lastTerm());
                response=new VoteReply(vote.term,self,request.candidate,vote.granted).encode();
            }else if(Wire.type(message).equals("VOTO")){
                VoteReply reply=VoteReply.decode(message);if(reply.voter==self)return;
                if(raft.onVote(reply.term,reply.voter,reply.candidate,reply.granted)){
                    // Promoção indivisível: restaura histórico/relógios e começa
                    // uma nova sequência de heartbeats, sem pendentes do papel anterior.
                    restoreClocks();hb=0;pendentes=new ArrayList<>();
                    if(heartbeatJob!=null)heartbeatJob.cancel(false);
                    long electedTerm=raft.term();
                    heartbeatJob=heartbeats.scheduleAtFixedRate(()->guard(()->sendBatch(electedTerm)),0,Wire.PERIODO_HB_MS,TimeUnit.MILLISECONDS);
                }
            }else throw new IllegalArgumentException("Tipo de eleicao invalido");
            demotionCheck();
        }
        // O voto já foi persistido em RaftState. Publicar fora do lock evita
        // bloquear a receção de leituras e lotes durante o pedido HTTP.
        if(response!=null)publish(Wire.topicElection(k),response);
    }

    /**
     * Apenas o líder atribui seq e cria registos. O isolamento de rede não
     * bloqueia IN: o antigo líder pode continuar a aceitar escritas locais.
     * Sem commit por maioria, essas escritas podem ser truncadas na reparação.
     */
    private void input(String message){
        String[] f=Wire.body(message).split(";",-1);
        if(!Wire.type(message).equals("IN")||f.length!=4||f[0].isBlank()||new Partitioner(n).of(f[0])!=k)throw new IllegalArgumentException("IN invalido");
        double temperature=Double.parseDouble(f[1]);long msgL=Long.parseLong(f[2]);
        VectorClock msgV=VectorClock.parse(f[3],n);
        if(!Double.isFinite(temperature)||msgL<0)throw new IllegalArgumentException("Carimbo ou temperatura invalidos");
        for(int j=0;j<n;j++)if(msgV.get(j)<0)throw new IllegalArgumentException("V negativo");
        synchronized(lock){
            // Consultar o papel e fazer append na mesma secção impede atribuir
            // seq no termo antigo depois de outra thread ter adotado o novo.
            long term=raft.termIfLeader();
            if(term<0){log.accept("IGNORADO;p"+k+";IN;termo="+raft.term());return;}
            // Lamport: máximo local/recebido + 1. Vetor: merge e incremento
            // da componente da partição. Cada registo local exige V[k]=seq.
            long next=seq+1,nextL=Math.max(lamport,msgL)+1;
            VectorClock nextV=vector.copy();nextV.merge(msgV);nextV.tick(k);
            if(nextV.get(k)!=next)throw new IllegalStateException("V[k] diferente de seq");
            SensorRecord record=SensorRecord.now(next,term,f[0],temperature,nextL,nextV);
            // Grava antes de juntar aos pendentes; aceitar localmente ainda
            // não significa que o evento esteja replicado ou confirmado por maioria.
            try{file.append(record);}catch(IOException e){throw new UncheckedIOException(e);}
            seq=next;lamport=nextL;vector=nextV;revision++;pendentes.add(record);
            log.accept("EVENTO;p"+k+";termo="+term+";registo="+record.toLine());
        }
    }

    /** Fecha um lote coerente com o histórico, sem criar eventos de L/V. */
    private void sendBatch(long scheduledTerm){
        Batch batch;
        synchronized(lock){
            long term=raft.termIfLeader();
            // Impede uma tarefa de uma liderança anterior de enviar no novo termo.
            if(term!=scheduledTerm){demotionCheck();return;}
            // Trocar a lista separa os registos deste lote dos IN que chegam a seguir.
            List<SensorRecord> closed=pendentes;pendentes=new ArrayList<>();
            batch=new Batch(term,self,address,Wire.nodePort(k,self),++hb,file.lastSeq(),file.lastTerm(),closed);
        }
        // Uma demissão pode ocorrer durante HTTP. O lote mantém o termo capturado;
        // nós que já conhecem um termo superior rejeitam essa publicação atrasada.
        if(Wire.isolated(k,self))return;
        String encoded=batch.encode();long instant=System.currentTimeMillis();
        if(publish(Wire.topicData(k),encoded)){
            try{Files.writeString(Path.of("hb-p"+k+"-n"+self+".txt"),
                batch.term+";"+batch.hb+";"+instant+";"+batch.lastSeq+";"+batch.lastTerm+";"+batch.records.size()+";"+encoded.getBytes(StandardCharsets.UTF_8).length+System.lineSeparator(),
                StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
            catch(IOException e){throw new UncheckedIOException(e);}
        }
    }

    private boolean publish(String topic,String text){
        if(Wire.isolated(k,self))return false;
        try{pubSub.publish(topic,text);return true;}
        catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
        catch(IOException e){log.accept("ERRO;p"+k+";publicacao="+topic+";"+e.getClass().getSimpleName());return false;}
    }

    /**
     * Como seguidor: valida o termo, ignora duplicados (seq,termo), acrescenta
     * o próximo seq e pede TCP se detetar lacuna ou cauda divergente.
     */
    private void batch(String text){
        if(Wire.isolated(k,self))return;
        Batch b=Batch.decode(text,n);if(b.leader==self)return;
        boolean reconcile=false;long version;
        synchronized(lock){
            if(b.term==lastBatchTerm&&b.hb<=lastBatchHb)return;
            // A autoridade é validada ANTES de modificar qualquer registo local.
            if(!raft.onBatch(b.term,b.leader)){log.accept("IGNORADO;p"+k+";LOTE;termo="+b.term);return;}
            demotionCheck();lastBatchTerm=b.term;lastBatchHb=b.hb;log.received(k,b);
            for(SensorRecord r:b.records){
                if(new Partitioner(n).of(r.getSensorId())!=k||r.getVector().get(k)!=r.getSeq())throw new IllegalArgumentException("Registo na particao/carimbo errado");
                SensorRecord local=file.at(r.getSeq());
                if(local!=null){if(local.getTerm()!=r.getTerm()){reconcile=true;break;}}
                else if(r.getSeq()==file.lastSeq()+1){
                    try{file.append(r);}catch(IOException e){throw new UncheckedIOException(e);}revision++;
                }else{reconcile=true;break;}
            }
            if(file.lastSeq()>b.lastSeq||(file.at(b.lastSeq)!=null&&file.at(b.lastSeq).getTerm()!=b.lastTerm))reconcile=true;
            if(file.lastSeq()<b.lastSeq)reconcile=true;
            version=revision;
        }
        // A espera TCP fica fora do monitor. Outras threads podem entretanto
        // alterar termo ou histórico; recover revalida a resposta antes de a aplicar.
        if(reconcile)recover(b,version);
    }

    // Protocolo TCP: PEDIDO <primeiro> <último>, linhas de registos e FIM.
    // Valida contiguidade, partição, relógio e metadados do lote de referência.
    private List<SensorRecord> request(Batch b,long from)throws IOException{
        List<SensorRecord> result=new ArrayList<>();
        if(from>b.lastSeq)return result;
        if(Wire.isolated(k,self)||Wire.isolated(k,b.leader))throw new IOException("Isolamento TCP");
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(b.address,b.port),Wire.T_TCP_MS);socket.setSoTimeout(Wire.T_TCP_MS);
            try(BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                PrintWriter out=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true)){
                out.println("PEDIDO "+from+" "+b.lastSeq);if(out.checkError())throw new IOException("PEDIDO falhou");
                long expected=from;
                while(true){
                    if(Wire.isolated(k,self)||Wire.isolated(k,b.leader))throw new IOException("Isolamento TCP");
                    String line=in.readLine();if(line==null)throw new IOException("TCP sem FIM");
                    if(line.equals("FIM"))break;
                    if(line.startsWith("ERRO"))throw new IOException(line);
                    SensorRecord r=SensorRecord.fromLine(line,n);
                    if(r.getSeq()!=expected++||r.getSeq()>b.lastSeq||r.getTerm()>b.term||r.getVector().get(k)!=r.getSeq()||new Partitioner(n).of(r.getSensorId())!=k)throw new IOException("Resposta TCP incoerente");
                    result.add(r);
                }
                if(expected!=b.lastSeq+1)throw new IOException("Resposta TCP incompleta");
                if(!result.isEmpty()&&result.get(result.size()-1).getTerm()!=b.lastTerm)throw new IOException("Historico mudou durante TCP");
            }
        }
        return result;
    }

    /**
     * Reconcilia com o líder anunciado pelo lote. Procura um prefixo comum
     * recuando por blocos de termo e substitui só a cauda divergente.
     */
    private void recover(Batch b,long version){
        List<SensorRecord> local;
        synchronized(lock){local=file.snapshot();}
        try{
            // Começa no primeiro seq do último bloco de termo local. Se não
            // coincidir, recua um bloco: preserva o prefixo comum que encontrar.
            long start=local.size()+1L;
            if(!local.isEmpty()){
                start=local.size();long t=local.get((int)start-1).getTerm();
                while(start>1&&local.get((int)start-2).getTerm()==t)start--;
            }
            List<SensorRecord> remote;
            while(true){
                remote=request(b,start);
                boolean anchor=!remote.isEmpty()&&start<=local.size()&&local.get((int)start-1).getTerm()==remote.get(0).getTerm();
                if(start==1||anchor||local.isEmpty())break;
                long t=local.get((int)start-2).getTerm();start--;
                while(start>1&&local.get((int)start-2).getTerm()==t)start--;
            }
            synchronized(lock){
                // O TCP não suspendeu as restantes threads. Uma resposta que
                // já não corresponde a termo/líder/revisão atuais é descartada;
                // um lote posterior poderá voltar a desencadear recuperação.
                if(raft.term()!=b.term||raft.leader()!=b.leader||raft.role()==RaftState.Role.LIDER||revision!=version||Wire.isolated(k,self)){
                    log.accept("IGNORADO;p"+k+";resposta_TCP_obsoleta");return;
                }
                // Na ficha, seq e termo identificam a posição e a sua época.
                // Termos diferentes no mesmo seq representam uma divergência.
                long firstDiff=0;
                for(SensorRecord r:remote){
                    SensorRecord old=file.at(r.getSeq());
                    if(old==null||old.getTerm()!=r.getTerm()){firstDiff=r.getSeq();break;}
                }
                if(firstDiff==0&&file.lastSeq()>b.lastSeq)firstDiff=b.lastSeq+1;
                long oldLast=file.lastSeq();
                if(firstDiff>0){
                    List<SensorRecord> tail=new ArrayList<>();for(SensorRecord r:remote)if(r.getSeq()>=firstDiff)tail.add(r);
                    List<String> discarded=new ArrayList<>();List<Long> terms=new ArrayList<>();
                    for(long j=firstDiff;j<=oldLast;j++){SensorRecord r=file.at(j);discarded.add(r.toLine());terms.add(r.getTerm());}
                    // Só troca o ficheiro depois de escrever a versão completa
                    // temporária. Depois alinha os relógios com o novo último registo.
                    file.replaceFrom(firstDiff,tail);revision++;restoreClocks();
                    if(firstDiff<=oldLast){
                        log.accept("TRUNCADO;p"+k+";de="+firstDiff+";descartados="+discarded.size()+";termos="+terms);
                        for(String r:discarded)log.accept("DESCARTADO;p"+k+";registo="+r);
                    }
                }
                log.accept("RECUPERACAO;p"+k+";de="+start+";ate="+b.lastSeq);
            }
        }catch(IOException|IllegalArgumentException e){log.accept("ERRO;p"+k+";recuperacao="+e.getMessage());}
    }

    // Qualquer nó serve o seu histórico, mesmo como seguidor. Captura uma cópia
    // do intervalo sob lock e envia-a fora dele: um cliente lento não retém o nó.
    private void serve(Socket socket){
        try(Socket s=socket){
            s.setSoTimeout(Wire.T_TCP_MS);
            try(BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
                PrintWriter out=new PrintWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8),true)){
                if(Wire.isolated(k,self))return;
                String line=in.readLine();String[] f=line==null?new String[0]:line.split(" ");
                if(f.length!=3||!f[0].equals("PEDIDO")){out.println("ERRO pedido");return;}
                long from=Long.parseLong(f[1]),to=Long.parseLong(f[2]);
                if(from<1||from>to){out.println("ERRO intervalo");return;}
                List<SensorRecord> answer;
                synchronized(lock){answer=file.range(from,to);}
                for(SensorRecord r:answer){if(Wire.isolated(k,self))return;out.println(r.toLine());}
                out.println("FIM");
            }
        }catch(IOException|RuntimeException e){log.accept("ERRO;p"+k+";servidor_TCP="+e.getClass().getSimpleName());}
    }

    private void start()throws IOException{
        try(ServerSocket server=new ServerSocket(Wire.nodePort(k,self))){
            subscribe(Wire.topicIn(k),this::input);
            subscribe(Wire.topicData(k),this::batch);
            subscribe(Wire.topicElection(k),this::election);
            checks.scheduleAtFixedRate(()->guard(this::electionTick),Wire.VERIFICACAO_MS,Wire.VERIFICACAO_MS,TimeUnit.MILLISECONDS);
            Runtime.getRuntime().addShutdownHook(new Thread(()->{checks.shutdownNow();heartbeats.shutdownNow();clients.shutdownNow();}));
            while(true){Socket socket=server.accept();clients.execute(()->guard(()->serve(socket)));}
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length<2||args.length>3)throw new IllegalArgumentException("PartitionNode <k> <i> [N]");
        int k=Integer.parseInt(args[0]),i=Wire.node(Integer.parseInt(args[1])),n=args.length==3?Integer.parseInt(args[2]):3;
        if(n<1||k<0||k>=n||Wire.nodePort(k,i)>65535)throw new IllegalArgumentException("Particao/porto invalido");
        // Barreira opcional para arranques simultâneos nos ensaios de prazo fixo.
        // Por omissão é zero e não altera os temporizadores do protocolo.
        long startAt=Long.getLong("raft04.arranqueEm",0L);
        if(startAt>System.currentTimeMillis())Thread.sleep(Math.max(0,startAt-System.currentTimeMillis()));
        new PartitionNode(k,i,n).start();
    }
}
