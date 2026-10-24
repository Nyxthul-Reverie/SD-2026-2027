# COMINDIR02 — relatório técnico e revalidação

Data: 18/10/2026. Branch: tarefa/COMINDIR02. HEAD preservado: d0820a4ecb8aac5eec1e5088be50c61b765bbf88. Sem commit ou push.

**Nova auditoria independente: PASS técnico.** Conferência registada em NOVA-AUDITORIA-INDEPENDENTE.md e final-state.json na pasta de evidências. A avaliação oral do aluno e os valores COMINDIR01 indisponíveis não são certificados por este resultado.

## 1. Implementação final e correção autorizada

A entrega contém os três projetos separados, package rep02, seis Java no produtor, seis no líder e sete na réplica. SensorRecord, RecordFile e IpfsPubSub derivam de origin/tarefa/COMINDIR01; só se mudou o pacote e, no líder/réplica, se acrescentou o range fornecido. Wire e Partitioner são iguais nos três projetos. Não há bibliotecas externas.

| Componente | Comportamento final |
|---|---|
| Producer <N> [api] | Calcula a partição, publica IN e mostra `→ p<k>` em cada publicação individual, uniforme ou quente; mantém tempos e totais por partição |
| Partitioner | floorMod(sensorId.hashCode(), n), count(), rejeição de N<1 |
| PartitionLeader <k> <N> [endereço] [api] | Rejeita sensor de outra partição antes de seq/append; persiste antes de publicar REG; servidor TCP sequencial independente da thread PubSub |
| PartitionReplica <id> <partições> [portoApi] | Uma thread e um estado por partição; aplicação em sequência, retenção, cascata e recuperação quando pending>10 |
| PartitionCheck <N> <pasta> | Sensores corretos, seq contígua desde1, R=2, igualdade/prefixo, faltas, contagens e soma |

Nesta correção foi acrescentada exclusivamente uma linha System.out.println no ciclo publishBurst de Producer.java. A linha mostra o mesmo tópico, mensagem IN e seta já usados no input individual. Não se alteraram Random, distribuição, temperaturas, tópicos, protocolo, Wire, Partitioner, contadores ou posições das chamadas System.currentTimeMillis. A indicação é feita depois da captura da última publicação; o output das leituras anteriores pode alterar o intervalo total, pelo que as rajadas foram medidas novamente.

Os outros 18 Java ficaram com os mesmos hashes. O produtor continua sem seq, timestamp, SensorRecord, persistência, conhecimento de líderes ou TCP. Não foram acrescentados ACK, multicast de propagação, heartbeat, retransmissão PubSub, consenso, eleição, relógios lógicos/vetoriais, transações entre partições, hashing consistente, reparticionamento, snapshots, compactação ou armazenamento de ficheiros no IPFS.

## 2. Ambiente, evidência e método

Novas evidências, fora do repositório: `C:\Users\admin\Downloads\SDT\COMINDIR02-Revalidacao-2026-10-18-01`. As 22 execuções medidas incluem 18 observações das seis séries de três execuções e os casos 6(e),12(d),12(e) e falha1. As três validações pequenas não entram nas médias oficiais.

Cada cenário usa uma pasta nova. Cada arranque tem um log diferente (`r3-g1.log`, `r3-g2.log`, etc.); CREATE_NEW impede reutilizar nomes. Depois de terminar os processos, os logs são protegidos contra escrita e selados por SHA256 em logs.sha256. 6(e) tem pasta própria: copia o estado encerrado de 6(d)3, arranca na ordem réplicas/líder/produtor, para r3, arquiva e apaga só o seu ficheiro, reinicia-a vazia e publica as20 leituras. Não modifica os ficheiros ou logs de 6(d).

Fontes do observador/harness estão arquivados externamente. A medição consulta externamente os ficheiros a cada10ms e só considera uma linha completa. Cada alteração observada fica em observations.csv, com instante System.currentTimeMillis, ficheiro e seq. Snapshots JDI externos de last/pending/endereço/porto são feitos após o intervalo de desempenho, sem alterar a aplicação. Polling, I/O, logging, JDI e escalonamento têm custo; são medições locais, não benchmark de produção.

Envio = último timestamp do produtor menos primeiro. Convergência = deteção do último registo pela réplica mais lenta menos primeira publicação, apenas quando todas as cópias ficaram iguais. O débito dos líderes é o total escrito dividido pelo intervalo entre primeira e última deteção de escrita nos líderes. Estes são instantes de observação externa, não timestamps internos exatos do append. Não se calcula débito de 6(e) sobre o total histórico pré-carregado. Sem convergência, o tempo é N/A.

O novo cálculo independente usa logs brutos, ficheiros, states-final.txt e observations.csv, sem usar as médias anteriores como entrada: audit-data.ps1.txt, measurements-independent.csv, means-independent.csv, states-independent.csv e thresholds-independent.csv. sealed-log-audit.csv verifica os hashes. Todas as seis séries foram medidas de novo, incluindo a comparação sem perdas. Nenhuma média de rajada anterior é usada neste relatório.

Kubo 0.43.1 foi preservado. No diagnóstico anterior com IpfsPubSub herdado, dois publicadores concorrentes tiveram1999 sucessos e um HTTP500 validation ignored; um publicador teve2000/2000; Kubo 0.39.0 teve4000/4000 concorrentes. O código oficial0.43.1 instala um validador de nonce máximo por peer. Validação fora de ordem é uma inferência coerente com a reprodução e o código, não rastreio interno de cada nonce. Ver [integração](https://github.com/ipfs/kubo/blob/v0.43.1/core/node/libp2p/pubsub.go) e [validador](https://github.com/libp2p/go-libp2p-pubsub/blob/v0.17.0/validation_builtin.go).

Após autorização explícita do utilizador, usa-se o [Kubo 0.39.0 oficial](https://github.com/ipfs/kubo/releases/tag/v0.39.0), instalado separadamente em C:/Users/admin/Apps/kubo_v0.39.0/kubo/ipfs.exe. O asset oficial teve digest SHA256 5743ac292e762f69d4cb150268c513fbcdf04c6c4840aee16309cbbe27957826, confirmado na auditoria anterior; o executável coincide com o zip. Não há condições por versão ou alterações ao protocolo/código para0.39.0; IpfsPubSub continua herdado sem alterações.

| Nó | Repositório explícito IPFS_PATH | API | Peer ID |
|---|---|---|---|
| 1 | C:/Users/admin/Downloads/SDT/kubo-comindir02-compat-node1 | 127.0.0.1:5001/api/v0 | 12D3KooWL7mhZxj16Lewd1YyUJa52v9CYEEhhPBddfNSvcLicGjo |
| 2 | C:/Users/admin/Downloads/SDT/kubo-comindir02-compat-node2 | 127.0.0.1:5002/api/v0 | 12D3KooWNKan34R9L5Ja5r7Bix2hKx2McZrigTSQq8TQTkMDkJrh |

Configuração isolada: Bootstrap=[], mDNS=false, AutoConf=false, swarm loopback, Routing.Type=none, Pubsub.Enabled=true. IPFS_PATH e IPFS_TELEMETRY=off foram definidos só nos processos. Em um nó, zero peers; em dois, ligação manual exclusivamente entre os dois, com prova PubSub antes das rajadas. isolation-checks.log guarda as verificações antes/depois dos cenários. Peers externos observados nas medições:0.

A preparação PowerShell de 6(f) recebeu403 sem Origin, comportamento previamente conhecido. Repetiu-se só essa consulta com Origin explícito; nenhuma rajada tinha começado. Os clientes Java continuam sem Origin explícito, com os pedidos herdados aceites. setup-notes.txt distingue esse incidente de preparação dos testes da aplicação.

Os nós de teste foram encerrados por ipfs shutdown e os repositórios preservados. O daemon principal0.43.1 foi reposto com a identidade original. Configurações e variáveis persistentes não foram alteradas. Java executado em JDK25, compilado para Java11 com --release 11 -Xlint:all, sem warnings, bytecode55; não se alega execução numa JVM11 instalada.

## 3. Parte A

Os testes não dependentes de rajadas não foram repetidos: 6(a) tem quatro ficheiros5 linhas iguais; 6(b) perdeu6..8, pending10 sem pedido, seq19→pending11, PEDIDO6 8, uma ligação/três registos, FIM e cascata até19; 6(c) perdeu1, last0/pending0, onze posteriores até seq12, PEDIDO1 1, FIM e cascata, quatro ficheiros12 iguais. Evidência original em COMINDIR02-Isolado-2026-10-18-4b5ab38b, COMINDIR02-TesteB-2026-10-18-01 e COMINDIR02-Final-2026-10-18-01/6c. Os componentes desses mecanismos ficaram inalterados.

### 6(d) e 6(f): novas séries, perda 0.2 em r3

| Cenário | Primeira ms | Última ms | Envio ms | Débito reg/s | TCP | RECUPERADO | Last r3 | Deteção da última escrita existente ms | Por escrever | Pending | Convergiu |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 6d-run1 | 1792332197554 | 1792332199369 | 1815 | 426.985 | 162 | 207 | 988 | 1792332199931 | 12 | 9 | Não |
| 6d-run2 | 1792332202703 | 1792332204436 | 1733 | 437.254 | 152 | 186 | 992 | 1792332205016 | 8 | 7 | Não |
| 6d-run3 | 1792332207728 | 1792332209460 | 1732 | 438.982 | 164 | 193 | 987 | 1792332210023 | 13 | 9 | Não |
| 6f-run1 | 1792332507383 | 1792332509163 | 1780 | 420.521 | 163 | 195 | 993 | 1792332509809 | 7 | 6 | Não |
| 6f-run2 | 1792332512608 | 1792332514388 | 1780 | 441.306 | 153 | 194 | 993 | 1792332514917 | 7 | 6 | Não |
| 6f-run3 | 1792332517701 | 1792332519451 | 1750 | 433.839 | 148 | 178 | 989 | 1792332520042 | 11 | 8 | Não |

As seis execuções publicaram1000 leituras; r1/r2 ficaram iguais ao líder. Nenhum dos timestamps da última escrita existente é apresentado como escrita do seq1000: r3 não convergiu nessas amostras. Pending conta retidos, não todas as linhas por escrever. Em 6(d), faltam12/8/13 linhas com9/7/9 retidas:3/1/4 ainda não recebidas. Em 6(f),7/7/11 por escrever e6/6/8 retidas:1/1/3 lacunas. As perdas finais podem deixar pending<=10 para sempre sem novas REG.

A recontagem externa de todos os pedidos de 6(d)/6(f) confirmou pending>10 e intervalo last+1..firstKey-1. Em 6(d)3, o cliente preservado tem164 pedidos e193 linhas RECUPERADO; o líder tem164 respostas e193 registos enviados. O log r3-g1.log permaneceu separado e com o mesmo hash depois de 6(e). A antiga lacuna de rastreabilidade foi eliminada por esta série substituta completa.

6(e): histórico do líder1000; ficheiro de r3 arquivado e apagado, reinício sem perdas. Primeira REG1001; seq1011 faz pending11, PEDIDO1 1000. Uma ligação e1000 registos TCP; cascata1001..1011 e aplicação até1020. Os quatro ficheiros terminaram1020 linhas iguais. Envio117ms; convergência331ms. Evidência em6e/logs/r3-g2.log, metrics.properties e observations.csv.

6(f): r1 usa API5001; r2/r3 API5002; dois processos Kubo com identidades distintas. two-nodes.json, two-node-probe.txt, processes.txt e isolation-checks.log documentam ligação manual e a execução. São dois nós na mesma máquina, não duas máquinas independentes.

## 4. Partições reais S01–S20

| Sensor | N=3 | N=4 |
|---|---: |---: |
| S01 | 0 | 0 |
| S02 | 1 | 1 |
| S03 | 2 | 2 |
| S04 | 0 | 3 |
| S05 | 1 | 0 |
| S06 | 2 | 1 |
| S07 | 0 | 2 |
| S08 | 1 | 3 |
| S09 | 2 | 0 |
| S10 | 0 | 2 |
| S11 | 1 | 3 |
| S12 | 2 | 0 |
| S13 | 0 | 1 |
| S14 | 1 | 2 |
| S15 | 2 | 3 |
| S16 | 0 | 0 |
| S17 | 1 | 1 |
| S18 | 2 | 2 |
| S19 | 0 | 3 |
| S20 | 1 | 1 |

Calculado pela implementação Java real (`partition-map.csv`): N=3=7/7/6 sensores; mudam 13/20=65%. O hash negativo de `polygenelubricants` é -2147483648; floorMod(..., 3)=1, sem índice negativo.

## 5. Parte B e resultados funcionais

12(a), não repetido por não usar rajada: S01..S06, dois registos por partição, cópias p0→r1/r3,p1→r1/r2,p2→r2/r3; checkerPASS. Evidência anterior em COMINDIR02-Final-2026-10-18-01/12a-1.

| Série | p0 | p1 | p2 | Envio ms | Convergência ms | Débito reg/s | Máximo/média | S01 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 12b-run-1 | 1032 | 1045 | 923 | 4939 | 4955 | 612.12 | 1.045 | 155 |
| 12b-run-2 | 1033 | 1084 | 883 | 4937 | 4960 | 610.874 | 1.084 | 147 |
| 12b-run-3 | 1048 | 1038 | 914 | 4936 | 4943 | 612.62 | 1.048 | 154 |
| 12c-run-1 | 2613 | 217 | 170 | 4099 | 4952 | 612.996 | 2.613 | 2414 |
| 12c-run-2 | 2561 | 256 | 183 | 4326 | 5060 | 596.659 | 2.561 | 2367 |
| 12c-run-3 | 2624 | 216 | 160 | 4541 | 5264 | 575.595 | 2.624 | 2451 |
| 12f-run-1 | 3000 | — | — | 5116 | 6953 | 434.405 | 1 | 144 |
| 12f-run-2 | 3000 | — | — | 4510 | 6345 | 476.493 | 1 | 174 |
| 12f-run-3 | 3000 | — | — | 3963 | 5868 | 515.464 | 1 | 145 |

12(b)/12(c): cópias iguais e checkerPASS nas seis execuções, sem faltas, pending ou TCP. A quente usa probabilidade0.8 para S01, não quota fixa. p0 também recebe outros sensores; o desequilíbrio é calculado sobre registos por partição, não sobre sensores distintos.

12(d), também nova evidência da falha7: líder p1 parado no seq504; Producer publicou p0/p1/p2=1097/1012/891, sem erro. Foram aceites1097/504/891; perderam-se508 entradas p1. O observador recebeu todas as1012 entradas p1, o ficheiro tem504; 12d-lost-inputs.txt lista exatamente as508 não aceites. TCP7001 recusou enquanto parado; p0/p2 completaram. Depois do fim da rajada, p1 reiniciou sem replay. As cópias dos2492 registos aceites são iguais; checkerPASS não descobre entradas sem seq. Envio5183ms; convergência dos aceites5201ms.

12(e): r3 com perda 0.2, r1 parada em p0=328/p1=342 e reiniciada antes do fim. r1 recuperou950 registos em2 ligações; r1/r2 convergiram. Por servidor: p0=159 ligações/681 registos; p1=1/459; p2=154/203. r3/p0: last1012/1028,pending10; r3/p2:last939/951,pending9. São28 linhas por escrever=19retidas+9não recebidas. CheckerPASS porque são prefixos; não se afirma convergência de r3.

12(f): as três execuções N1 têm um líder e três réplicas de p0, todos os ficheiros3000 iguais. O checker da Parte B continua a exigir R2 e escreve FAIL exclusivamente por observar R3, a configuração deliberada pedida para este benchmark. Não se ocultou esse FAIL nem se mudou o checker. A comparação de débito não isola o número de líderes: CPU, memória, disco, loopback e Kubo são partilhados e N1 tem três cópias, contra duas em N3.

## 6. §4.3, PartitionCheck e nove falhas

A verificação original continua válida para os componentes inalterados: netstat LISTENING7000/7001/7002; seis tópicos PubSub oficiais; observação passiva de REG em rep02-p0; PEDIDO1 5 respondeu cinco linhas e FIM; BAD, PEDIDO5 1 e PEDIDOx y deram ERRO sem terminar o servidor. Evidência em COMINDIR02-Final-2026-10-18-01/verification-4.3.

O checker foi novamente confrontado com ficheiros finais e fixtures. Verifica sensor em k, seq1..n sem saltos/repetições, R2, igualdade/prefixo, faltas, contagens/sensores e total. Igualdade/prefixo passam; salto, duplicado, R3, divergência, líder ausente, partição fora de N, sensor errado e linha mal formada falham. Prefixo aceite não significa convergência; total de ficheiros não verifica leituras perdidas antes do líder.

| Falha | Lado/exceção | Divergência/recuperação/conclusão |
|---|---|---|
| 1 Réplica parada durante1000 | r3, nenhuma exceção de recuperação | Nova execução: parou332, voltou com líder941; PEDIDO333 934, uma ligação/602 registos; final1000 igual. Convergência2486ms desde primeira publicação, não tempo TCP isolado |
| 2 Descartar último | r3, nenhuma | Seq1 perdido, last0/pending0/TCP0; com11 posteriores pede1 1, FIM e cascata, final12 igual. Sem posteriores não deteta |
| 3 Endpoint10.255.255.1 | r1/p0, Connect timed out | Last0/pending11 preservados; p1 aplicou12 em69ms durante espera de p0; nova REG com endpoint correto permitiu recuperar. Threads independentes não resolvem alcance TCP |
| 4 Porto7000 ocupado | Líder, Address already in use: bind capturada | Não subscreveu nem escreveu; publicação sem líder perdeu-se; libertado porto, reinício e nova leitura aceite. API não garante consumidor |
| 5 EOF antes de FIM | r3, Resposta interrompida antes de FIM | Fixture externo enviou1..9 de1..19; last9/pending11 mantidos; próxima REG pede10..19; duas ligações/19 registos; final31 igual. Não alterou o código final |
| 6 Apagar histórico do líder | Líder reinicia em1, nenhuma exceção | Réplicaslast19 ignoram duplicado1; sem reparação automática. Backup manual reposto e nova20 convergiu. Restauração não é mecanismo implementado |
| 7 Líderp1 parado | TCP Connection refused, Producer sem erro | Nova execução12(d):508 entradas p1 perdidas; outros líderes corretos; reinício não recupera entradas que nunca tiveram seq |
| 8 ProducerN4/líderesN3 | Líderes, oito PARTIÇÃO ERRADA, nenhuma exceção |20 leituras:7aceites,8rejeitadas,5para p3 sem líder; checkerPASS sobre aceites. N tem de coincidir |
| 9 Duas JVMs de r2 na mesma pasta | Ficheiros r2, nenhuma exceção de escrita | Seq1,1,2,2 em p1/p2 e checkerFAIL; synchronized não coordena processos diferentes |

Falhas2–6,8,9 usam as evidências originais COMINDIR02-Final-2026-10-18-01/failure*, porque a alteração de output nas rajadas não afeta esses ensaios individuais. Não se reutilizam tempos de rajadas antigas: falhas1/7 foram substituídas pelas novas execuções. A primeira tentativa antiga failure7, invalidada por corrida de leitura do harness, permanece excluída. Ensaios negativos e casos N1/R3 mantêm os FAIL esperados visíveis.

## 7. Dez construções

| Construção | Código | Garantia | Limite |
|---|---|---|---|
| Endpoint em REG | Wire.reg; Replica.receive | Aprende/atualiza destino em cada REG | Não autentica nem garante alcance |
| ServerSocket/accept | Leader:58/73 | Serviço TCP sequencial | Não atende ilimitadamente/em paralelo |
| LIMITE_RETIDOS | Wire:4; Replica:155 | Recupera acima de10 | Não deteta caudas sem retidos suficientes |
| PEDIDO/registos/FIM | Leader.recover; Replica.recover | Intervalo e fim distinguível de EOF | Sem transação integral/autenticação |
| connect/setSoTimeout | Replica:196/197; Leader:74 | Timeout ligação/inatividade | Sem prazo global da recuperação ou de escrita |
| RecordFile.range | RecordFile:36 líder/réplica | Intervalo inclusivo na ordem do ficheiro | Percorre todo o histórico; sem índice |
| floorMod/hashCode | Partitioner.of | Determinismo em[0,N[ | Não equilibra frequência nem preserva mapa ao mudarN |
| Seq por partição | Leader.lastSeq/receive | Ordem local | Não estabelece ordem global |
| R2 | CLI e PartitionCheck | Duas cópias por partição emN3 | Não cria líder alternativo |
| synchronized seq+append | Leader:115/157; RecordFile | Coerência contador/ficheiro entre threads | Não coordena JVMs nem força fsync/quórum |

## 8. Novas médias §4.4

São médias aritméticas das três execuções de cada configuração. ms para tempos, registos/s para débito, contagens para TCP/faltas/pending. Nenhuma célula usa a média anterior à correção.

| Métrica | COMINDIR01 | Sem perdas1nó | Perda1nó | Perda2nós |
|---|---|---:|---:|---:|
| Envio ms | N/A | 1767.667 | 1760 | 1770 |
| Convergência ms | N/A | 2275 | N/A | N/A |
| Débito reg/s | N/A | 450.175 | 434.407 | 431.889 |
| Ligações TCP | N/A | 0 | 159.333 | 154.667 |
| Registos TCP | N/A | 0 | 195.333 | 189 |
| Pending total | N/A | 0 | 8.333 | 6.667 |
| Por escrever r1/r2/r3 | N/A |0/0/0|0/0/11|0/0/8.333|

COMINDIR01: série comparável indisponível, explicitamente N/A. Para a obter seria preciso medir essa implementação nas mesmas condições; não se preencheram valores inventados. Convergência da série com perdas é N/A porque nenhuma das três cópias r3 convergiu, não zero. Diferenças entre um e dois nós incluem variação local e não demonstram um ganho causal por acrescentar nó.

| Métrica | N1/R3 | N3 uniforme/R2 | N3 quente/R2 |
|---|---:|---:|---:|
| p0/p1/p2 |3000/—/—|1037.667/1055.667/906.667|2599.333/229.667/171|
| Máximo/média | 1 | 1.059 | 2.599 |
| Envio ms | 4529.667 | 4937.333 | 4322 |
| Débito reg/s | 475.454 | 611.871 | 595.083 |
| Convergência ms | 6388.667 | 4952.667 | 5092 |
| Linhas r1/r2/r3 |3000/3000/3000|2093.333/1962.333/1944.333|2829/400.667/2770.333|
| Líderp1 parado |p1 não existe; perder p0 afeta toda escrita|só escrita/recuperação p1|só escrita/recuperação p1|

Débito N3 uniforme/N1 observado=1.287. É comparação local com recursos partilhados e R diferente, não previsão de ganho em máquinas independentes.

## 9. Reflexão crítica

1. **Custo da recuperação.** Perda0.2 exigiu em média159.333 ligações/195.333 registos por1000 num nó e154.667/189 em dois. A propagação publicou1000 REG; TCP é tráfego adicional unicast. Bytes e tráfego interno completo do PubSub não foram medidos. Líder suporta leitura/envio; requerente suporta receção/aplicação; outras réplicas partilham recursos e podem aguardar atendimento.
2. **Critério.** Limiar1 desencadeia no segundo retido;1000 no1001.º. Limiar baixo aumenta pedidos por atrasos transitórios; alto aumenta memória/latência e deixa séries curtas sem recuperar. Em6(c), perda1 deixou pending0; só seq12 criou11 retidos. Anúncio periódico do máximo seq revelaria caudas, mas é heartbeat fora do âmbito e não foi implementado.
3. **300 réplicas.** Se todas ultrapassarem o limiar, podem produzir300 pedidos iniciais. Com duração ideal t por atendimento sequencial, a última começa perto de299t e termina em300t. Backlog/timeouts/retries alteram esse ideal. Threads por ligação permitiriam simultaneidade dentro das ferramentas da ficha, mas não eliminariam carga crescente no líder. Não se mediram300 processos.
4. **NAT/firewall.** Deve ser anunciado endpoint TCP alcançável, eventualmente público e encaminhado. 127.0.0.1 só serve o mesmo computador. Na falha3, PubSub funcionou mas o TCP ao endereço errado expirou. Sem destino alcançável não há recuperação.
5. **Histórico.** A medição6(e),331ms, inclui1000 históricos,20novas leituras e overhead. Extrapolar linearmente dá331s, aproximadamente5.517min, para um milhão. É estimativa grosseira, não medição nem tempo TCP isolado; I/O, memória, GC e rede podem invalidá-la. Outros pedidos aguardam o servidor sequencial. Índice de offsets reduziria procura; snapshots/compactação evitariam transferir todo o histórico, mas são fora do âmbito.
6. **Chave quente.** Médias p0/p1/p2=2599.333/229.667/171, máximo/média 2.599, contra1.059 uniforme. S01 é indivisível pela chave atual. Sensor+subchave variável distribuiria leituras desse sensor, mas perderia a sua ordem única; é discussão conceptual, não alteração à chave exigida.
7. **N3→N4.** Mudam13/20 sensores,65%. Históricos teriam de ser redistribuídos e configuração coordenada. Falha8 perdeu/rejeitou13/20 quando só mudou o produtor. Hashing consistente reduz remapeamentos em geral, mas está excluído e não foi implementado.
8. **Disponibilidade.** Um líder único da COMINDIR01 limita toda a escrita; aqui a falha p1 mantém p0/p2, mas impede escrita/recuperação p1. Em12(d),508 entradas perderam-se e TCP recusou. Particionar limita alcance, não substitui líder. Assunção automática exigiria deteção, eleição e estado coerente, fora do âmbito.
9. **Produtor→líder.** Publicaram-se1012 entradas p1, só504 receberam seq e508 perderam-se. O produtor viu sucesso HTTP; o checker aceitou os ficheiros dos registos aceites. Recuperação nesse troço exigiria identificar/guardar publicações no produtor, reconhecer lacunas e disponibilizar protocolo de pedido, responsabilidades não acrescentadas.
10. **Segurança.** REG forjada pode contaminar dados e substituir destino TCP. Servidor sequencial sem autenticação pode ser ocupado por ligações repetidas/lentas. Verificação da origem teria de anteceder aceitação de REG e serviço TCP. from identifica peer, não autorização para liderar; vários processos podem partilhar o daemon. IpfsPubSub extrai data e não autoriza from. Não se alegam autenticação/confidencialidade/resistência a DoS.

## 10. Material técnico CA1–CA5

Cada pergunta é respondida com conceito, código e evidência. Não é um guião oral nem certificação de compreensão do aluno. N/A identifica o que não foi medido.

| Pergunta | Resposta, código e teste |
|---|---|
| CA1.1 Perdeu10/último | São necessários11 posteriores efetivamente retidos na partição; sem posteriores não há pedido. Replica.receive/apply;6(b)/6(c): 10 retidos sem pedido; 11 com recuperação |
| CA1.2 Endpoint em cada REG/reinício noutra máquina | Réplica pode arrancar tarde/perder primeiras mensagens; recebe destino na próxima REG. Reinício com outro endpoint é aprendido numa nova REG, sem descoberta autónoma. Wire.reg/Replica.receive;falha3. Mudança física não foi ensaiada |
| CA1.3 Bloqueio/PubSub entretanto | A thread dessa partição bloqueia em connect/readLine e deixa de consumir HTTP; buffers podem reter dados, sem durabilidade/ausência de perda garantidas. Outra partição tem thread própria. Replica.main/recover;falha 3: p1 aplicou 12 enquanto p0 estava bloqueada |
| CA1.4 Persistir antes de propagar | range serve o ficheiro; sem persistir, uma REG anunciada não teria fonte. Leader.receive persiste sob monitor antes de publish;6(e)/falha6 |
| CA2.1 Parada durante500 | Uma única lacuna contígua500, com posteriores suficientes e TCP sem falha, pode ser recuperada numa ligação/500 registos. Não se mediu exatamente500. Medição nova: falha1,602 registos/1TCP. Replica.recover/Leader.range |
| CA2.2 Duas vias/duplicados | TCP e PubSub podem trazer o mesmo seq; apply ignora seq<=last ou chave já pending, escreve só o próximo e faz cascata. Recuperações sem duplicados e falha6 ignorando nova seq1; falha9 é duas JVMs e outro problema |
| CA2.3 Máximo de pending | Até10sem pedido;11é trigger transitório, não limite rígido. Com falhas prolongadas pending pode crescer sem limite. Replica.receive/recover;6(b)/6(c)/falha3 |
| CA2.4 Porquê não0/efeito1000 |0 pede na primeira entrega fora de ordem;1000 espera1001 retidos. Compromisso entre pedidos, memória e atraso. Wire.LIMITE_RETIDOS/Replica.receive;6(c); outros valores não foram implementados/medidos |
| CA3.1 floorMod/% | Hash negativo pode dar resto negativo: -2147483648%3=-2;floorMod=1. Partitioner.of;mapa real e jshell |
| CA3.2 Mesmo mapa em processos distintos | String.hashCode definido pela especificação Java e mesmo N/função. Partitioner;12(a)/12(b),falha 8 mostra N incoerente |
| CA3.3 Seq global | Exigiria atribuidor/coordenação entre líderes, contrariando independência pedida. lastSeq/ficheiro próprios;12(a): seq 1 em cada partição |
| CA3.4 Ordem | Leituras aceites do mesmo sensor seguem a ordem atribuída pelo seu líder, não necessariamente ordem de produção concorrente; não há ordem total entre partições. Partitioner/Leader.receive;12(a)/12(b) |
| CA4.1 Duas atividades/estado | Entrada bloqueia no stream HTTP; principal em accept/readLine TCP. Partilham file/lastSeq sob synchronized(file). Leader.main/receive/recover;6(d)/falha4 |
| CA4.2 Sem secção comum/leitura intercalar | Pode haver incoerência contador/ficheiro. Métodos RecordFile synchronized protegem cada leitura contra append; bloco externo dá coerência com lastSeq. Um escritor não implica automaticamente duplicação por corrida entre escritores. Leader.receive/recover;6(e) coerente; falha 9 mostra ausência de coordenação entre JVMs |
| CA4.3 Líderp1 parado | Entradas p1 podem perder-se sem erro do produtor; TCP p1 recusa; p0/p2 continuam. Wire/Leader porpartição;novo12(d) |
| CA4.4 Falha não recuperada | Histórico do líder apagado não é reconstruído; caudas sem posteriores e entradas antes do líder também não têm reparação garantida. Leader.lastSeq/Replica.trigger;falhas2/6/7/8 |
| CA5.1 Custo da réplica parada em1000 | Nova falha1:602 registos/1ligação e2486ms desde primeira publicação até convergência; não duraçãoTCPisolada. Replica.recover/observador externo |
| CA5.2 80%S01 | Distribuição média2599.333/229.667/171,desequilíbrio2.599; chave por sensor não divide S01. Producer.publishBurst/Partitioner;novo12(c) |
| CA5.3 N3→4/histórico |13/20mudam; é necessária redistribuição/configuração coerente. Partitioner.of;mapa real/falha8; sem migração implementada |
| CA5.4 Perda antes do líder | Produtor sabe a leitura, mas não guarda histórico/seq; líder não sabe que existiu. Estado/protocolo adicional seriam necessários. Producer/Leader.receive;novo12(d),508 entradas perdidas apesar de checkerPASS |

A série quantitativa COMINDIR01 continua N/A por autorização do pedido; comparação arquitetural e limitações estão presentes. Explicação oral e compreensão do aluno não são certificadas por estes testes.

## 11. Auditoria, conclusão e higiene

Os pontos anteriores da auditoria PARTIAL foram tratados: output por leitura acrescentado; seis logs novos de perda preservados sem sobrescrita, incluindo receção recontável de6(d)3; relatório substitui todas as métricas de rajada afetadas e distingue medições novas de evidências funcionais não repetidas. A nova auditoria independente confirmou PASS técnico com estas evidências; as limitações e N/A indicados continuam a aplicar-se.

Classes, argumentos, tópicos rep02-in-p<k>/rep02-p<k>, mensagens IN/REG/PEDIDO/FIM/ERRO, portos7000+k e ficheiros oficiais mantêm-se. Configuração normal N3/R2: r1=0,1;r2=1,2;r3=2,0. N1/R3 é só benchmark deliberado. Código usa biblioteca padrão e Kubo; TCP apenas recuperação; sem mecanismos proibidos.

Limitações mantidas e demonstradas: perdas antes do líder, caudas sem retidos suficientes, líder/endpoint inacessível, sem eleição/reconstrução do histórico, leitura integral de range, sem exclusão entre JVMs ou autenticação. Séries locais partilham recursos e polling tem incerteza. N/A e FAIL negativos deliberados permanecem visíveis; não foram substituídos por números ou garantias inventados.

Entrega:19Java obrigatórios e este relatório. Dados, logs, métricas, repositórios Kubo, harnesses e fixtures ficam fora do repositório e não devem ser commitados. out/.class gerados são removidos no encerramento. COMINDIR01, HEAD e referências Git preservados, sem commit/push. O relatório da nova auditoria e final-state.json nas evidências documentam o estado final verificado.
