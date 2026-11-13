package raft04;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real JVM/Kubo experiments. Owns every child Process it can terminate. */
public final class Experiments {
    private static Path root;
    private static String java,javac;
    private static final List<Process> owned=new ArrayList<>();
    private static final List<String> summary=new ArrayList<>();
    private static Path suite;
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    private static void waitFor(BooleanSupplier p,long millis,String reason)throws Exception{
        long end=System.nanoTime()+millis*1000000;while(System.nanoTime()<end){if(p.getAsBoolean())return;Thread.sleep(40);}throw new AssertionError("Timeout: "+reason);
    }
    private static String read(Path p){try{return Files.exists(p)?Files.readString(p,StandardCharsets.UTF_8):"";}catch(IOException e){return "";}}
    private static List<String> lines(Path p){List<String> out=new ArrayList<>();for(String l:read(p).split("\\R"))if(!l.isBlank())out.add(l);return out;}
    private static void append(Path p,String s)throws IOException{Files.writeString(p,s+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    private static final class Run implements AutoCloseable {
        final Path dir;final Map<String,Process> processes=new LinkedHashMap<>();final long min,max;final String mutation;
        final Map<String,Long> deadlines=new HashMap<>();
        long barrier;
        Path nodeClasses;
        Run(String name,long min,long max,String mutation)throws Exception{
            this.min=min;this.max=max;this.mutation=mutation;
            dir=suite.resolve(name);Files.createDirectory(dir);nodeClasses=root.resolve("raft04-no/out");
            if(mutation!=null)nodeClasses=mutate(mutation,dir);
            Files.writeString(dir.resolve("configuracao.md"),"Ensaio "+name+"; periodo=2000; eleicao=["+min+","+max+"]; mutacao="+mutation+"\n",StandardCharsets.UTF_8);
        }
        Process start(String key,String project,String cls,String...args)throws Exception{
            List<String> cmd=new ArrayList<>(List.of(java,"-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8",
                "-Draft04.api=http://127.0.0.1:5005/api/v0","-Draft04.arranqueEm="+barrier,"-Draft04.eleicaoMin="+deadlines.getOrDefault(key,min),"-Draft04.eleicaoMax="+deadlines.getOrDefault(key,max),
                "-cp",(project.equals("raft04-no")?nodeClasses:root.resolve(project+"/out")).toString(),"raft04."+cls));
            cmd.addAll(Arrays.asList(args));
            Process p=new ProcessBuilder(cmd).directory(dir.toFile()).redirectOutput(ProcessBuilder.Redirect.appendTo(dir.resolve(key+"-console.txt").toFile())).redirectErrorStream(true).start();
            owned.add(p);processes.put(key,p);return p;
        }
        void node(int k,int i)throws Exception{start("p"+k+"-n"+i,"raft04-no","PartitionNode",""+k,""+i,"3");}
        void boot(boolean clients,long seed)throws Exception{
            if(min==max)barrier=System.currentTimeMillis()+5000;
            List<Integer> order=new ArrayList<>();for(int j=0;j<9;j++)order.add(j);if(seed>=0)Collections.shuffle(order,new Random(seed));
            for(int j:order)node(j/3,j%3+1);
            if(min==max){
                long until=System.nanoTime()+25000000000L;
                while(System.nanoTime()<until&&!(leader(0)>0&&leader(1)>0&&leader(2)>0))Thread.sleep(50);
                append(dir.resolve("observacoes.txt"),"JANELA_FIXA;lideres="+leader(0)+","+leader(1)+","+leader(2)+";0=nao eleito na janela");
            }else waitFor(()->leader(0)>0&&leader(1)>0&&leader(2)>0,45000,"primeiros lideres");
            if(clients)clients();
        }
        void clients()throws Exception{
            start("processador","raft04-processador","AlarmProcessor","3");
            start("produtor","raft04-produtor","Producer","3");
            waitFor(()->read(dir.resolve("produtor-console.txt")).contains("Produtor pronto"),10000,"produtor");
            Thread.sleep(2300);
        }
        int leader(int k){
            long best=-1;int elected=0;
            for(int i=1;i<=3;i++)for(String l:lines(dir.resolve("eventos-p"+k+"-n"+i+".txt"))){
                if(l.contains(";LIDER;p"+k+";")){
                    String t=l.substring(l.indexOf("termo=")+6).split(";")[0];long term=Long.parseLong(t);
                    Process p=processes.get("p"+k+"-n"+i);
                    if(p!=null&&p.isAlive()&&!Files.exists(dir.resolve("isolar-p"+k+"-n"+i))&&term>best){best=term;elected=i;}
                }
            }return elected;
        }
        long term(int k,int i){String s=read(dir.resolve("estado-p"+k+"-n"+i+".txt")).trim();return s.isEmpty()?0:Long.parseLong(s.split(";")[0]);}
        List<String> records(int k,int i){return lines(dir.resolve("no-p"+k+"-n"+i+".txt"));}
        boolean equal(int k){return records(k,1).equals(records(k,2))&&records(k,1).equals(records(k,3));}
        void send(String command)throws Exception{Process p=processes.get("produtor");p.getOutputStream().write((command+"\n").getBytes(StandardCharsets.UTF_8));p.getOutputStream().flush();}
        void kill(int k,int i,boolean leaderFailure)throws Exception{
            Process p=processes.get("p"+k+"-n"+i);require(p!=null&&owned.contains(p),"Processo nao pertence ao ensaio");
            if(leaderFailure)append(dir.resolve("falhas.txt"),"p"+k+";n"+i+";"+System.currentTimeMillis());
            p.destroyForcibly();p.waitFor(10,TimeUnit.SECONDS);
        }
        void isolate(int k,int i)throws Exception{Files.createFile(dir.resolve("isolar-p"+k+"-n"+i));append(dir.resolve("isolamentos.txt"),System.currentTimeMillis()+";ISOLAR;p"+k+";n"+i);}
        void heal(int k,int i)throws Exception{Files.delete(dir.resolve("isolar-p"+k+"-n"+i));append(dir.resolve("isolamentos.txt"),System.currentTimeMillis()+";REPARAR;p"+k+";n"+i);}
        boolean converge(){for(int k=0;k<3;k++)if(!equal(k))return false;return true;}
        void settle()throws Exception{waitFor(this::converge,30000,"coincidencia final");Thread.sleep(2500);require(converge(),"Divergencia apos espera");}
        String checker(String name)throws Exception{
            List<String> cmd=new ArrayList<>(List.of(java,"-Dfile.encoding=UTF-8","-cp",root.resolve("raft04-no/out").toString(),"raft04."+name,"3",dir.toString()));
            if(name.equals("RaftCheck")&&Files.exists(dir.resolve("falhas.txt")))cmd.add(dir.resolve("falhas.txt").toString());
            Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start();String out=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);int status=p.waitFor();
            Files.writeString(dir.resolve(name+".txt"),out,StandardCharsets.UTF_8);
            append(dir.resolve("verificadores-status.txt"),name+";exit="+status);
            return out;
        }
        void finish(boolean correct)throws Exception{
            close();
            int published=read(dir.resolve("produtor-console.txt")).split("Publicado em",-1).length-1;
            require(lines(dir.resolve("produtor.txt")).size()==published,"produtor.txt nao corresponde aos envios publicados");
            String raft=checker("RaftCheck"),causal=checker("CausalCheck");
            if(correct){require(raft.contains("RaftCheck PASS"),raft);require(causal.contains("CausalCheck PASS"),causal);}
            String sums=Arrays.stream(raft.split("\\R")).filter(l->l.startsWith("RESUMO;")||l.startsWith("PRIMEIRO_LIDER;")||l.startsWith("FALHA;")).reduce("",(a,b)->a+b+"\n");
            Files.writeString(dir.resolve("resultado.md"),"Estado: "+(correct?"PASS":"MUTACAO EXECUTADA: interpretar efeito nos logs")+"\n\n"+sums,StandardCharsets.UTF_8);
            summary.add(dir.getFileName()+";"+(correct?"PASS":"MUTACAO")+";"+sums.replace("\n"," | "));
            Files.write(suite.resolve("resumo.csv"),summary,StandardCharsets.UTF_8);
            System.out.println("CONCLUIDO "+dir.getFileName()+" "+(correct?"PASS":"MUTACAO"));System.out.flush();
        }
        public void close(){
            for(Process p:processes.values())if(p.isAlive())p.destroyForcibly();
            for(Process p:processes.values())try{p.waitFor(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        }
    }

    private static Path mutate(String kind,Path dir)throws Exception{
        Path src=dir.resolve("mutacao-src"),out=dir.resolve("mutacao-out");Files.createDirectory(src);Files.createDirectory(out);
        try(DirectoryStream<Path> stream=Files.newDirectoryStream(root.resolve("raft04-no/src/raft04"),"*.java")){
            for(Path p:stream){
                String s=Files.readString(p,StandardCharsets.UTF_8);
                if(p.getFileName().toString().equals("RaftState.java")){
                    if(kind.equals("maioria"))s=s.replace("if (votes.size() <= groupSize / 2) return false;","if (votes.size() < groupSize / 2) return false;");
                    if(kind.equals("volatil")){
                        int start=s.indexOf("    private void persist() {"),end=s.indexOf("    // termo superior",start);
                        s=s.substring(0,start)+"    private void persist() {}\n\n"+s.substring(end);
                        s=s.replace("if (Files.exists(stateFile)) {","if (false) {");
                    }
                    if(kind.equals("atualizacao"))s=s.replace("&& upToDate;","&& true;");
                    if(kind.equals("obsoleto"))s=s.replace("if (bTerm < term) return false;","if (bTerm < term) { leader = bLeader; role = Role.SEGUIDOR; resetTimer(); return true; }");
                }
                if(p.getFileName().toString().equals("PartitionNode.java")&&kind.equals("zero")){
                    s=s.replace("restoreClocks();hb=0;pendentes=new ArrayList<>();","restoreClocks();lamport=0;vector=new VectorClock(n);hb=0;pendentes=new ArrayList<>();");
                }
                Files.writeString(src.resolve(p.getFileName()),s,StandardCharsets.UTF_8);
            }
        }
        List<String> cmd=new ArrayList<>(List.of(javac,"--release","11","-encoding","UTF-8","-d",out.toString()));
        try(DirectoryStream<Path> stream=Files.newDirectoryStream(src,"*.java")){for(Path p:stream)cmd.add(p.toString());}
        Process p=new ProcessBuilder(cmd).inheritIO().start();require(p.waitFor()==0,"Mutacao nao compilou");return out;
    }

    private static void figure(String name,String mutation)throws Exception{
        try(Run r=new Run(name,6000,12000,mutation)){
            r.boot(true,1);int old=r.leader(1);
            r.send("S02 20.1");r.send("S05 23.7");
            waitFor(()->r.records(1,old).size()==2,3000,"duas leituras");
            waitFor(()->r.equal(1)&&r.records(1,1).size()==2,8000,"duas leituras replicadas");
            int hbs=lines(r.dir.resolve("hb-p1-n"+old+".txt")).size();
            waitFor(()->lines(r.dir.resolve("hb-p1-n"+old+".txt")).size()>hbs,4000,"fronteira do lote");
            r.send("S02 20.4");waitFor(()->r.records(1,old).size()==3,900,"append antes falha");
            r.kill(1,old,true);r.send("S05 23.9");
            waitFor(()->r.leader(1)>0&&r.leader(1)!=old,25000,"novo lider");
            int current=r.leader(1);
            r.send("S02 20.6");
            if(mutation==null){
                waitFor(()->r.records(1,current).size()==3,4000,"novo seq3");
            }else {
                Thread.sleep(3000);
                if(mutation.equals("zero"))require(read(r.dir.resolve("p1-n"+current+"-console.txt")).contains("V[k] diferente de seq"),"Falha dos relogios nao observada");
            }
            r.node(1,old);Thread.sleep(6000);
            if(mutation==null){
                r.settle();require(read(r.dir.resolve("eventos-p1-n"+old+".txt")).contains(";TRUNCADO;"),"Figura sem truncagem");
            }
            r.finish(mutation==null);
        }
    }

    private static void failure(int repetition)throws Exception{
        try(Run r=new Run("c-falha-"+repetition,6000,12000,null)){
            r.boot(true,20+repetition);int old=r.leader(1);
            r.send("rajada 300 10");
            waitFor(()->read(r.dir.resolve("produtor-console.txt")).split("Publicado em",-1).length>=30,8000,"rajada em curso");
            require(!read(r.dir.resolve("produtor-console.txt")).contains("Rajada concluída"),"Rajada ja terminou");
            r.kill(1,old,true);Thread.sleep(30000);r.node(1,old);Thread.sleep(5000);r.settle();r.finish(true);
        }
    }
    private static void partition(String name,String mutation)throws Exception{
        try(Run r=new Run(name,6000,12000,mutation)){
            r.boot(true,33);int old=r.leader(1);long oldTerm=r.term(1,old);
            r.send("rajada 300 10");r.isolate(1,old);Thread.sleep(30000);
            int next=r.leader(1);require(next>0&&next!=old,"Maioria nao elegeu");
            require(r.term(1,next)>oldTerm,"Termo nao aumentou");
            append(r.dir.resolve("observacoes.txt"),"DOIS_LIDERES;antigo=n"+old+";termo="+oldTerm+";novo=n"+next+";termo="+r.term(1,next));
            long oldLength=r.records(1,old).size();
            String delayed=new Batch(oldTerm,old,"127.0.0.1",Wire.nodePort(1,old),999,oldLength,oldLength==0?0:oldTerm,List.of()).encode();
            r.heal(1,old);Thread.sleep(6000);
            if(mutation!=null){
                // Replay a legitimate old-term header after the repair to make
                // delayed obsolete delivery deterministic in this fault test.
                Files.writeString(r.dir.resolve("replay-lote.txt"),delayed,StandardCharsets.UTF_8);
                new IpfsPubSub("http://127.0.0.1:5005/api/v0").publish(Wire.topicData(1),delayed);
                Thread.sleep(2000);
            }
            if(mutation==null){
                r.settle();
                int follower=old==r.leader(1)?old%3+1:old;long before=r.term(1,follower);
                r.isolate(1,follower);Thread.sleep(60000);long after=r.term(1,follower);
                require(after>before,"Seguidor isolado nao aumenta termo");
                append(r.dir.resolve("observacoes.txt"),"SEGUIDOR_ISOLADO;termo="+before+"->"+after);
                r.heal(1,follower);Thread.sleep(15000);r.settle();
            }
            r.finish(mutation==null);
            if(mutation!=null)require(read(r.dir.resolve("RaftCheck.txt")).matches("(?s).*lotes_obsoletos_aceites=[1-9][0-9]*.*"),"Mutacao de termo obsoleto nao observada");
        }
    }
    private static void quorum()throws Exception{
        try(Run r=new Run("e-dois-nos",6000,12000,null)){
            r.boot(true,41);int old=r.leader(1),other=old%3+1,survivor=6-old-other;
            r.kill(1,old,true);r.kill(1,other,false);
            long before=r.term(1,survivor);r.send("rajada 300 10");Thread.sleep(30000);
            require(r.term(1,survivor)>before,"Sobrevivente sem candidaturas");
            require(r.leader(1)==0,"Um no elegeu-se sozinho");
            r.node(1,other);long restart=System.currentTimeMillis();
            waitFor(()->r.leader(1)>0,30000,"maioria restaurada");
            append(r.dir.resolve("observacoes.txt"),"MAIORIA_RESTAURADA;espera_ms="+(System.currentTimeMillis()-restart));
            r.node(1,old);Thread.sleep(5000);r.settle();r.finish(true);
        }
    }

    private static void mutationUnit(String kind)throws Exception{
        try(Run r=new Run("mutacao-"+kind,6000,12000,kind)){
            // Dedicated actual JVM test of the mutated class, not the correct implementation.
            Path testOut=r.dir.resolve("fault-test-out");Files.createDirectory(testOut);
            Process compile=new ProcessBuilder(javac,"--release","11","-encoding","UTF-8","-cp",r.nodeClasses.toString(),"-d",testOut.toString(),root.resolve("testes/FaultTest.java").toString()).inheritIO().start();
            require(compile.waitFor()==0,"FaultTest compile");
            Process test=new ProcessBuilder(java,"-cp",testOut+File.pathSeparator+r.nodeClasses,"raft04.FaultTest",kind,r.dir.toString()).redirectErrorStream(true).start();
            String result=new String(test.getInputStream().readAllBytes(),StandardCharsets.UTF_8);int status=test.waitFor();
            Files.writeString(r.dir.resolve("FaultTest.txt"),result,StandardCharsets.UTF_8);require(status==0,result);
            String raft=r.checker("RaftCheck"),causal=r.checker("CausalCheck");
            require(raft.contains("RaftCheck FAIL"),"Verificador nao detetou a falha dirigida");
            Files.writeString(r.dir.resolve("resultado.md"),"Teste dirigido da classe alterada: PASS. RaftCheck: FAIL esperado. CausalCheck: "+(causal.contains("CausalCheck PASS")?"PASS":"FAIL")+". Nao e ensaio de rede.\n",StandardCharsets.UTF_8);
            System.out.println(result);summary.add("mutacao-"+kind+";DIRIGIDO_PASS;nao e ensaio distribuido completo");
            Files.write(suite.resolve("resumo.csv"),summary,StandardCharsets.UTF_8);
        }
    }

    private static void outdatedCandidate()throws Exception{
        try(Run r=new Run("mutacao-atualizacao",6000,12000,"atualizacao")){
            r.deadlines.put("p1-n1",6000L);r.deadlines.put("p1-n2",12000L);r.deadlines.put("p1-n3",18000L);
            r.boot(true,1);require(r.leader(1)==1,"Lider inicial dirigido inesperado");
            r.isolate(1,2);r.send("S02 20.1");r.send("S05 20.2");
            waitFor(()->r.records(1,3).size()==2,8000,"Copia atualizada");
            r.kill(1,1,true);
            waitFor(()->r.term(1,2)>=2,16000,"Candidatura isolada");
            r.heal(1,2);
            waitFor(()->r.leader(1)==2,35000,"Candidato atrasado eleito sem upToDate");
            require(r.records(1,2).isEmpty(),"Candidato nao estava atrasado");
            r.node(1,1);Thread.sleep(6000);r.settle();
            require(r.records(1,3).isEmpty(),"Mutacao nao descartou a copia atualizada");
            r.finish(false);
        }
    }

    private static void volatileNetwork()throws Exception{
        try(Run r=new Run("mutacao-volatil-rede",6000,12000,"volatil")){
            r.deadlines.put("p1-n1",6000L);r.deadlines.put("p1-n2",12000L);r.deadlines.put("p1-n3",18000L);
            r.boot(false,1);require(r.leader(1)==1,"Lider inicial inesperado");
            require(read(r.dir.resolve("eventos-p1-n3.txt")).contains("candidato=n1;concedido"),"Eleitor nao concedeu voto inicial");
            r.isolate(1,1);r.kill(1,2,false);r.kill(1,3,false);
            r.node(1,3);r.node(1,2);
            waitFor(()->read(r.dir.resolve("eventos-p1-n2.txt")).contains(";LIDER;p1;termo=1;"),25000,"Segundo lider no mesmo termo apos perda do voto");
            r.finish(false);
            require(read(r.dir.resolve("RaftCheck.txt")).contains("termos_multiplos_lideres=1"),"RaftCheck nao mostrou dois lideres");
        }
    }

    public static void main(String[] args)throws Exception{
        root=Path.of(args[0]).toAbsolutePath();String mode=args.length>1?args[1]:"all";
        java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        javac=Path.of(System.getProperty("java.home"),"bin","javac.exe").toString();
        suite=root.resolve("execucoes/suite-"+System.currentTimeMillis());Files.createDirectories(suite);
        System.out.println("RESULTADOS="+suite);System.out.flush();
        Runtime.getRuntime().addShutdownHook(new Thread(()->{for(Process p:owned)if(p.isAlive())p.destroyForcibly();}));
        try{
            if(mode.equals("a")||mode.equals("all"))figure("a-figura",null);
            if(mode.equals("b")||mode.equals("all")){
                for(int j=0;j<15;j++)try(Run r=new Run("b-"+(j<10?"aleatorio-":"fixo-")+j,j<10?6000:6000,j<10?12000:6000,null)){
                    r.boot(false,j);Thread.sleep(2200);r.finish(true);
                }
            }
            if(mode.equals("fixed"))for(int j=0;j<5;j++)try(Run r=new Run("b-fixo-sincronizado-"+j,6000,6000,null)){r.boot(false,300+j);Thread.sleep(2200);r.finish(true);}
            if(mode.equals("c")||mode.equals("all"))for(int j=1;j<=3;j++)failure(j);
            if(mode.equals("d")||mode.equals("all"))partition("d-isolamentos",null);
            if(mode.equals("e")||mode.equals("all"))quorum();
            if(mode.equals("statefaults")||mode.equals("faults")||mode.equals("all")){
                mutationUnit("maioria");mutationUnit("volatil");
            }
            if(mode.equals("faults")||mode.equals("networkfaults")||mode.equals("all")){
                try(Run r=new Run("mutacao-maioria-rede",6000,12000,"maioria")){r.boot(false,91);Thread.sleep(2200);r.finish(false);}
                volatileNetwork();
                outdatedCandidate();
                partition("mutacao-obsoleto","obsoleto");
                figure("mutacao-zero","zero");
            }
            System.out.println("SUITE CONCLUIDA: "+suite);
        }finally{for(Process p:owned)if(p.isAlive())p.destroyForcibly();}
    }
}
