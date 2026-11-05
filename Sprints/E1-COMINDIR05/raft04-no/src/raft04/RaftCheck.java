package raft04;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Observational checker, not a proof of complete Raft or durable commits. */
public final class RaftCheck {
    private static final class Event {
        final long time;final String type;final int k,node;final String line;
        Event(String line,int node){
            String[] f=line.split(";",-1);time=Long.parseLong(f[0]);type=f[1];k=Integer.parseInt(f[2].substring(1));this.node=node;this.line=line;
        }
        String field(String key){for(String f:line.split(";"))if(f.startsWith(key+"="))return f.substring(key.length()+1);return "";}
        long number(String key){return Long.parseLong(field(key));}
    }
    private static final class Publication {
        int k,node;long term,time;
        Publication(int k,int node,String line){this.k=k;this.node=node;String[] f=line.split(";");if(f.length!=7)throw new IllegalArgumentException("hb invalido");term=Long.parseLong(f[0]);time=Long.parseLong(f[2]);}
    }
    private static List<String> lines(Path p)throws IOException{
        List<String> out=new ArrayList<>();if(Files.exists(p))for(String l:Files.readAllLines(p,StandardCharsets.UTF_8))if(!l.isBlank())out.add(l);return out;
    }
    private static String reading(SensorRecord r){return r.getSensorId()+";"+r.getTemperature();}
    public static void main(String[] args){
        try{if(args.length<2||args.length>3)throw new IllegalArgumentException("RaftCheck <N> <pasta> [falhas.txt]");
            if(!analyse(Integer.parseInt(args[0]),Path.of(args[1]),args.length==3?Path.of(args[2]):null))System.exit(1);
        }catch(Exception e){System.err.println("RaftCheck FAIL: "+e);System.exit(1);}
    }
    public static boolean analyse(int n,Path dir,Path failureFile)throws IOException{
        if(n<1||!Files.isDirectory(dir))throw new IllegalArgumentException("N/pasta invalidos");
        List<Event> events=new ArrayList<>();List<Publication> hbs=new ArrayList<>();
        Map<String,List<SensorRecord>> logs=new TreeMap<>();
        Map<String,Set<Integer>> leaders=new TreeMap<>();
        Map<String,Set<Integer>> candidates=new TreeMap<>();
        Map<String,List<String>> votes=new TreeMap<>();
        Map<Integer,Long> boot=new TreeMap<>();Map<String,Long> candidateTime=new HashMap<>(),electedTime=new HashMap<>();
        Map<Integer,Long> firstLeader=new TreeMap<>();Map<Integer,Event> latestLeader=new TreeMap<>();
        int regressions=0,badStates=0,missing=0;
        for(int k=0;k<n;k++)for(int node=1;node<=Wire.GRUPO;node++){
            String key="p"+k+"-n"+node;Path data=dir.resolve("no-"+key+".txt");
            if(!Files.exists(data))missing++;
            List<SensorRecord> rs=new ArrayList<>();for(String line:lines(data)){
                SensorRecord r=SensorRecord.fromLine(line,n);
                if(r.getSeq()!=rs.size()+1L||r.getVector().get(k)!=r.getSeq())badStates++;
                rs.add(r);
            }logs.put(key,rs);
            long previous=-1,maxEventTerm=0;
            for(String line:lines(dir.resolve("eventos-"+key+".txt"))){
                Event e=new Event(line,node);events.add(e);
                String term=e.field("termo");
                if(!term.isEmpty()){
                    long t=Long.parseLong(term);
                    if(t<previous&&(e.type.equals("ARRANQUE")||e.type.equals("CANDIDATO")||e.type.equals("LIDER")))regressions++;
                    if(e.type.equals("ARRANQUE")||e.type.equals("CANDIDATO")||e.type.equals("LIDER")){previous=Math.max(previous,t);maxEventTerm=Math.max(maxEventTerm,t);}
                }
                if(e.type.equals("TERMO")){
                    String transition=line.split(";",-1)[3];String[] t=transition.split("->");
                    long before=Long.parseLong(t[0]),after=Long.parseLong(t[1]);
                    if(after<before||after<previous)regressions++;previous=Math.max(previous,after);maxEventTerm=Math.max(maxEventTerm,after);
                }
                if(e.type.equals("ARRANQUE"))boot.merge(k,e.time,Math::min);
            }
            Path state=dir.resolve("estado-"+key+".txt");
            if(Files.exists(state)){
                String[] st=Files.readString(state).trim().split(";",-1);
                long term=Long.parseLong(st[0]);int voted=Integer.parseInt(st[1]);
                if(term<maxEventTerm||voted<0||voted>Wire.GRUPO)badStates++;
            }else if(maxEventTerm>0)badStates++;
            for(String line:lines(dir.resolve("hb-"+key+".txt")))hbs.add(new Publication(k,node,line));
        }
        events.sort(Comparator.comparingLong(e->e.time));hbs.sort(Comparator.comparingLong(p->p.time));
        int monitorEvents=0;
        try(DirectoryStream<Path> stream=Files.newDirectoryStream(dir,"monitor-*.txt")){
            for(Path p:stream)monitorEvents+=lines(p).size();
        }
        for(Event e:events){
            if(e.type.equals("CANDIDATO")||e.type.equals("LIDER")){
                long t=e.number("termo");String key="p"+e.k+";termo="+t;
                if(e.type.equals("CANDIDATO")){
                    candidates.computeIfAbsent(key,x->new TreeSet<>()).add(e.node);candidateTime.merge(key,e.time,Math::min);
                }else{
                    leaders.computeIfAbsent(key,x->new TreeSet<>()).add(e.node);electedTime.merge(key,e.time,Math::min);
                    firstLeader.merge(e.k,e.time,Math::min);
                    Event prev=latestLeader.get(e.k);if(prev==null||e.number("termo")>prev.number("termo"))latestLeader.put(e.k,e);
                }
            }
            if(e.type.equals("VOTO"))votes.computeIfAbsent("p"+e.k+";termo="+e.number("termo"),x->new ArrayList<>()).add("n"+e.node+":"+e.line.substring(e.line.indexOf(";candidato=")+1));
        }
        int unsafe=0,split=0;Set<String> terms=new TreeSet<>();terms.addAll(candidates.keySet());terms.addAll(leaders.keySet());terms.addAll(votes.keySet());
        for(String key:terms){
            Set<Integer> ls=leaders.getOrDefault(key,Set.of());if(ls.size()>1)unsafe++;if(ls.isEmpty())split++;
            Long start=candidateTime.get(key),end=electedTime.get(key);
            System.out.println("TERMO;"+key+";candidatos="+candidates.getOrDefault(key,Set.of())+";lideres="+ls+";votos="+votes.getOrDefault(key,List.of())+";voto_dividido="+ls.isEmpty()+";eleicao_ms="+(start==null||end==null?"NA":end-start));
        }
        for(int k=0;k<n;k++)System.out.println("PRIMEIRO_LIDER;p"+k+";tempo_ms="+(!boot.containsKey(k)||!firstLeader.containsKey(k)?"NA":firstLeader.get(k)-boot.get(k)));
        int differences=0,lengthDifferences=0;
        Map<String,Integer> available=new HashMap<>();Set<String> union=new HashSet<>();
        for(int k=0;k<n;k++){
            int min=Integer.MAX_VALUE,max=0;for(int node=1;node<=Wire.GRUPO;node++){int size=logs.get("p"+k+"-n"+node).size();min=Math.min(min,size);max=Math.max(max,size);}
            List<Long> diffs=new ArrayList<>();
            for(int j=0;j<min;j++){
                String ref=logs.get("p"+k+"-n1").get(j).toLine();
                for(int node=2;node<=Wire.GRUPO;node++)if(!logs.get("p"+k+"-n"+node).get(j).toLine().equals(ref)){diffs.add(j+1L);break;}
            }
            differences+=diffs.size();if(min!=max)lengthDifferences++;
            System.out.println("COINCIDENCIA;p"+k+";min="+min+";max="+max+";seq_divergentes="+diffs+";comprimentos_iguais="+(min==max));
            for(int node=1;node<=Wire.GRUPO;node++)for(SensorRecord r:logs.get("p"+k+"-n"+node)){
                if(union.add(k+";"+r.toLine())&&!r.getSensorId().startsWith("ALARM-"))available.merge(k+";"+reading(r),1,Integer::sum);
            }
        }
        List<String> sent=lines(dir.resolve("produtor.txt"));int lost=0;
        for(String line:sent){
            String[] f=line.split(";",-1);if(f.length!=4)throw new IllegalArgumentException("produtor.txt invalido");
            String key=Integer.parseInt(f[1].substring(1))+";"+f[2]+";"+Double.parseDouble(f[3]);
            int count=available.getOrDefault(key,0);if(count==0)lost++;else available.put(key,count-1);
        }
        if(failureFile!=null)for(String line:lines(failureFile)){
            String[] f=line.split(";");int k=Integer.parseInt(f[0].substring(1)),node=Integer.parseInt(f[1].substring(1));long time=Long.parseLong(f[2]);
            long failedTerm=-1;for(Publication p:hbs)if(p.k==k&&p.node==node&&p.time<=time)failedTerm=p.term;
            Publication next=null;for(Publication p:hbs)if(p.k==k&&p.time>=time&&p.term>failedTerm){next=p;break;}
            int inWindow=0;for(String s:sent){String[]sf=s.split(";");long st=Long.parseLong(sf[0]);if(sf[1].equals("p"+k)&&st>=time&&(next==null||st<next.time))inWindow++;}
            System.out.println("FALHA;p"+k+";no=n"+node+";instante="+time+";indisponibilidade_ms="+(next==null?"NA":next.time-time)+";enviadas_intervalo="+inWindow);
        }
        int truncated=0,reappeared=0;
        for(Event e:events){
            if(e.type.equals("TRUNCADO")){truncated+=Integer.parseInt(e.field("descartados"));System.out.println(e.line);}
            if(e.type.equals("DESCARTADO")){
                SensorRecord old=SensorRecord.fromLine(e.line.substring(e.line.indexOf("registo=")+8),n);
                Event finalLeader=latestLeader.get(e.k);
                if(finalLeader!=null)for(SensorRecord r:logs.get("p"+e.k+"-n"+finalLeader.node))if(r.getSeq()!=old.getSeq()&&reading(r).equals(reading(old))){reappeared++;break;}
            }
        }
        System.out.println("RESUMO;termos="+terms.size()+";votos_divididos="+split+";termos_multiplos_lideres="+unsafe+";regressoes_termo="+regressions+";estados_invalidos="+badStates+";ficheiros_ausentes="+missing+";seq_divergentes="+differences+";comprimentos_diferentes="+lengthDifferences+";enviadas="+sent.size()+";perdidas="+lost+";truncados="+truncated+";reaparecidos_outro_seq="+reappeared+";eventos_monitor="+monitorEvents);
        System.out.println("NOTA: perdas/reaparecimentos usam multiconjunto sensor/temperatura; sem ID de pedido nao distinguem repeticoes identicas. Voto dividido inclui termos observados sem lider no fim da janela. Coincidencia nao prova commit por maioria.");
        boolean ok=unsafe==0&&regressions==0&&badStates==0&&missing==0&&differences==0;
        System.out.println("RaftCheck "+(ok?"PASS":"FAIL"));return ok;
    }
}
