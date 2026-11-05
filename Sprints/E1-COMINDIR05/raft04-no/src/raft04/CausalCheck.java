package raft04;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/**
 * Verifica seq contíguo, V[k]=seq, termos não decrescentes e L crescente nas
 * cópias. Entre partições, verifica se a ordem vetorial implica a ordem de L.
 * Não demonstra entrega de todos os IN nem desfaz efeitos de ramos truncados.
 */
public final class CausalCheck {
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("CausalCheck <N> <pasta>");
        int n=Integer.parseInt(args[0]);Path dir=Path.of(args[1]);List<SensorRecord> all=new ArrayList<>();List<Integer> parts=new ArrayList<>();
        int errors=0;
        for(int k=0;k<n;k++){
            List<SensorRecord> chosen=null;long bestTerm=-1;
            for(int node=1;node<=Wire.GRUPO;node++){
                Path file=dir.resolve("no-p"+k+"-n"+node+".txt");if(!Files.exists(file))continue;
                List<SensorRecord> rs=new ArrayList<>();
                for(String line:Files.readAllLines(file,StandardCharsets.UTF_8))if(!line.isBlank()){
                    SensorRecord r=SensorRecord.fromLine(line,n);
                    if(r.getSeq()!=rs.size()+1L||r.getVector().get(k)!=r.getSeq())errors++;
                    if(!rs.isEmpty()&&(r.getLamport()<=rs.get(rs.size()-1).getLamport()||r.getTerm()<rs.get(rs.size()-1).getTerm()))errors++;
                    rs.add(r);
                }
                // Para a comparação entre partições escolhe uma cópia pelo
                // maior termo do último registo e depois pelo comprimento.
                // Nos ensaios corretos o harness já exige cópias finais iguais.
                long t=rs.isEmpty()?0:rs.get(rs.size()-1).getTerm();
                if(chosen==null||t>bestTerm||(t==bestTerm&&rs.size()>chosen.size())){chosen=rs;bestTerm=t;}
            }
            if(chosen==null){errors++;continue;}
            for(SensorRecord r:chosen){all.add(r);parts.add(k);}
        }
        // Vetores concorrentes não impõem ordem de Lamport nesta verificação.
        // A implicação verificada é causalidade -> L menor, nunca a recíproca.
        int causal=0;
        for(int a=0;a<all.size();a++)for(int b=a+1;b<all.size();b++){
            if(parts.get(a).equals(parts.get(b)))continue;
            VectorClock.Order order=all.get(a).getVector().compare(all.get(b).getVector());
            if(order==VectorClock.Order.BEFORE&&all.get(a).getLamport()>=all.get(b).getLamport())causal++;
            if(order==VectorClock.Order.AFTER&&all.get(a).getLamport()<=all.get(b).getLamport())causal++;
        }
        System.out.println("CAUSAL;registos="+all.size()+";violacoes_locais="+errors+";violacoes_Lamport="+causal);
        System.out.println("Nota: analisa um historico final por particao e invariantes de todas as copias; nao prova entrega nem desfaz dependencias de ramos truncados.");
        System.out.println("CausalCheck "+(errors+causal==0?"PASS":"FAIL"));
        if(errors+causal>0)System.exit(1);
    }
}
