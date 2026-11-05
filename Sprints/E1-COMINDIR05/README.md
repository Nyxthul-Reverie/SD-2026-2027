# COMINDIR05 — eleição do líder de partição

## Execução no Windows

Requisitos: JDK 11 ou superior no PATH (validado com JDK 25), Kubo no PATH
(validado com Kubo 0.43.1), PowerShell. Não é necessário Python nem bibliotecas Java externas.
A ficha mantém intencionalmente o pacote, projetos e tópicos `raft04`.
A COMINDIR04 original não é alterada.

Na pasta `Sprints/E1-COMINDIR05`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\compilar.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\testes\testar.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\iniciar-kubo.ps1
```

O último comando fica aberto. Usa exclusivamente `execucoes/kubo-isolado`,
API 5005, swarm local 4005 e gateway 8085, sem bootstrap nem MDNS.
Não usa nem altera `C:\Users\admin\.ipfs-comindir02-039`.
A exceção de ExecutionPolicy aplica-se apenas ao processo invocado.

Abrir `COMINDIR05.code-workspace` no VS Code. O comando de compilação cria
`execucoes/manual`. Iniciar o compound **Nove nos**, esperar pelas eleições,
depois iniciar **Processador** e **Produtor**. Usar terminais integrados.
As configurações não contêm um caminho absoluto para o JDK de outro computador.

Os nove processos são `PartitionNode k i 3`, com k=0,1,2 e i=1,2,3.
Porto TCP: 7100 + 3*k + i-1. Todos os processos da demonstração usam a mesma
pasta de trabalho, mas cada nó tem os seus próprios ficheiros.

## Reproduzir os ensaios

Manter o Kubo isolado aberto. Terminar previamente os nove nós da execução
manual para libertar os portos. Os scripts de ensaio só terminam os processos
que eles próprios criaram; não encerram Kubo nem processos de outros trabalhos.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\testes\ensaios.ps1 -Suite a
powershell -NoProfile -ExecutionPolicy Bypass -File .\testes\ensaios.ps1 -Suite b
powershell -NoProfile -ExecutionPolicy Bypass -File .\testes\ensaios.ps1 -Suite c
powershell -NoProfile -ExecutionPolicy Bypass -File .\testes\ensaios.ps1 -Suite d
powershell -NoProfile -ExecutionPolicy Bypass -File .\testes\ensaios.ps1 -Suite e
powershell -NoProfile -ExecutionPolicy Bypass -File .\testes\ensaios.ps1 -Suite faults
```

`-Suite all` executa a sequência completa. `fixed` repete apenas os cinco
arranques fixos com barreira; `statefaults` executa os dois testes dirigidos de
estado; `networkfaults` executa as mutações com processos e Kubo.
Os ensaios são sequenciais porque usam os mesmos tópicos e portos.
Cada invocação cria uma pasta nova `execucoes/suite-<instante>`; não apaga dados
de execuções anteriores. O ensaio d inclui uma espera real de 60 segundos.
As cópias com mutações estão nessas pastas ignoradas e não substituem os fontes corretos.

Para executar os verificadores manualmente:

```powershell
java -cp raft04-no/out raft04.RaftCheck 3 <pasta-do-ensaio> <pasta-do-ensaio>/falhas.txt
java -cp raft04-no/out raft04.CausalCheck 3 <pasta-do-ensaio>
```

O argumento de falhas é opcional. Ler os avisos e as métricas: PASS do
RaftCheck não é uma garantia de commit por maioria nem de igualdade de comprimentos.
O harness verifica adicionalmente igualdade integral das três cópias nos ensaios corretos.

## Arquitetura e classes

```text
Producer -- IN --> raft04-in-pk --> n1 / n2 / n3
                                      |
                            só o LIDER cria registos
                                      |
                           LOTE em raft04-pk
                          /          |          \
                     seguidores  processador  produtor
                          |
                   recuperação TCP ao líder

n1/n2/n3 <--> raft04-eleicao-pk : PEDIDO_VOTO / VOTO
Cada grupo pk tem eleições independentes; são nove JVMs no total.
```

| Classe | Papel |
|---|---|
| RaftState | Termo/voto persistentes, papéis, prazos, votos e maioria |
| PartitionNode | Integra entrada, eleição, liderança, seguidor e TCP |
| RecordFile | Histórico contíguo, consultas, append e substituição atómica |
| SensorRecord | Sete campos, incluindo termo e relógios causais |
| Batch | Cabeçalho da liderança e registos; validação do corpo |
| VoteRequest / VoteReply | Mensagens da eleição |
| Wire | Tópicos, temporizações, portos e marcador de isolamento |
| Producer | Input, rajadas, monitorização e produtor.txt |
| AlarmProcessor | Alarmes, conhecimento causal e deduplicação p/seq/termo |
| HeartbeatMonitor | Observação local do termo/líder e suspeitas nos clientes |
| MonitorLog | Eventos com timestamp físico e exclusão mútua |
| RaftCheck | Segurança observada, eleições, perdas e coincidência final |
| CausalCheck | Invariantes locais e condição de Lamport entre partições |

Os ficheiros comuns são copiados entre os três projetos para respeitar a
organização da ficha. Compilar e executar cada projeto com o seu classpath.

## Guia para a defesa oral

Referências abaixo relativas à pasta desta ficha. Os números indicam o código
na versão de implementação auditada; usar o nome do método como referência duradoura.

| Conceito / pergunta | Resposta e referência |
|---|---|
| Porque deixou de existir líder fixo? | Cada PartitionNode pode mudar de papel. `PartitionNode.election`, linhas 78–99. |
| Como começa a eleição? | `RaftState.tick` (103–115) avança termo, vota em si, persiste e renova prazo; `electionTick` publica depois. |
| Porque dois votos? | Em G=3 a maioria é estritamente maior que G/2. `RaftState.onVote`, 137–147. |
| Porque dois candidatos não vencem o mesmo termo? | As maiorias intersetam-se e cada eleitor persiste um único candidato nesse termo. |
| Termo é Lamport? | Não. Termo identifica a época da eleição; Lamport carimba eventos aceites. |
| Porque persistir antes de enviar? | Evita esquecer um voto após crash e conceder outro no mesmo termo. `persist`, 66–89. |
| ATOMIC_MOVE garante corte de energia? | Garante substituição atómica; sem forçar escrita não há garantia absoluta de durabilidade física. |
| Porque conceder novamente ao mesmo candidato? | Um pedido pode repetir-se; a resposta continua idempotente para o mesmo termo/candidato. |
| Porque votedFor volta a zero? | O voto anterior pertence ao termo anterior. `adoptTerm`, 92–100. |
| Restrição de histórico? | Compara primeiro último termo e depois último seq. `onVoteRequest`, 118–134. |
| Porque renovar o prazo várias vezes? | Evita candidaturas enquanto o nó reconhece um líder ou já apoia uma eleição. |
| Como o líder se demite? | Mensagens de termo superior passam por RaftState sob o monitor do nó; a tarefa de heartbeat é cancelada. |
| IN durante demissão? | A verificação de papel/termo e o append estão sob o mesmo monitor. `PartitionNode.input`, 108–118. |
| E um lote já em HTTP quando o líder se demite? | Conserva o termo capturado; os nós em termo superior rejeitam-no. Não se pode retirar uma mensagem já em trânsito. |
| Porque restaurar relógios? | O novo evento continua o histórico da partição; começar em zero quebra V[k]=seq. `restoreClocks`, 40–44. |
| Porque heartbeat não faz tick de L/V? | Não cria um novo registo de sensor; só anuncia estado e transporta eventos existentes. |
| Onde fecha o lote? | `sendBatch`, 123–136: captura termo, lista e metadados sob lock; faz HTTP fora dele. |
| Ordem dos locks? | Monitor do nó, depois RaftState/RecordFile/log. Nenhuma callback volta ao monitor do nó pela ordem inversa. |
| TCP durante uma nova eleição? | A resposta só é instalada se termo, líder e revisão local continuarem iguais. `recover`, 216–221. |
| Como resolve divergência? | Recua pelos blocos de termo, encontra âncora, identifica primeira diferença e substitui só a cauda. `recover`, 202–236. |
| Mesmo seq em outro termo é duplicado? | Não. Pode ser outro evento. A identidade inclui termo; o processador usa p/seq/termo. |
| Dois líderes simultâneos? | Podem julgar-se líderes em termos distintos. A maioria impede dois eleitos no mesmo termo sob as hipóteses da ficha. |
| Porque não CP rigoroso? | Não há confirmação de cada escrita por maioria. O líder antigo isolado ainda aceita IN e essas escritas podem desaparecer. |
| Seguidor isolado regressa com termo alto? | Pode demitir um líder saudável e provocar eleição; Pre-Vote ajudaria, mas está excluído. |
| Porque se perdem leituras na eleição? | Não há líder que as aceite nem retenção/reenvio do produtor. |
| Reenviar resolveria tudo? | Exigiria confirmação e identidade/idempotência de pedidos para não duplicar; está fora do âmbito. |
| Sem maioria, há líder novo? | Não. Um sobrevivente pode candidatar-se repetidamente, sem chegar a dois votos. |
| Duas, três ou quatro cópias? | Maiorias 2,2,3: toleram respetivamente 0,1,1 falhas para uma nova eleição. |
| n1 falha antes de votar no exemplo? | n3 sozinho não obtém maioria se n2 já falhou. A partição fica indisponível para nova liderança. |
| Antigo líder regressa antes de eleição? | Arranca seguidor e só será líder por eleição; o seu histórico pode influenciar a restrição de voto. |
| Causalidade de um ramo truncado? | Os alarmes já emitidos não são desfeitos. A ficha não implementa transações entre partições nem entrega causal. |
| O verificador prova ausência de perda? | Não: mede o histórico final e envios registados. Repetições idênticas sem ID são ambíguas. |
| O que significa C, A e P? | C: operações observadas como uma ordem única consistente; A: cada pedido a um nó não falhado obtém resposta, mesmo sob partição; P: o modelo admite perda de comunicação. Aceitar um IN localmente não demonstra, por si só, A ou C do serviço completo. |
| Quando onBatch escreveria ERRO? | Um nó que ainda é LIDER recebe um lote de outro líder no mesmo termo. Um termo superior já o teria demitido. Com maioria e voto persistente único, dois líderes eleitos no mesmo termo são impossíveis nas hipóteses da ficha. `RaftState.onBatch`, 150–165. |
| Crash entre persistir e publicar PEDIDO_VOTO? | Reinicia SEGUIDOR com termo e voto já guardados; não desfaz esse voto nem assume liderança. Espera um novo prazo e, se necessário, candidata-se no termo seguinte. |
| Porque responder quando recusa voto? | A resposta transporta o termo atual; um candidato atrasado pode adotar esse termo e demitir-se. `election`, 81–87. |
| Quando surge o primeiro LOTE? | Sem voto dividido e com entrega/agendamento rápidos, a primeira candidatura ocorre entre 6 e 12 s, mais até 200 ms de verificação; seguem-se voto, persistência e publicação imediata. Não há limite absoluto de 12,2 s em presença de atrasos ou eleições repetidas. |
| Repetir IN recebido mas não publicado no termo anterior? | É um novo evento para o líder atual e recebe o próximo seq e termo atual; não há ID de pedido para reconhecer semanticamente o reenvio. A cauda antiga pode ser truncada. |
| Mesmo seq, novo termo: repete alarme? | Sim, se justificar: `(seq,termo)` identifica outro evento. Pode aumentar a contagem de alarmes e não desfaz os já emitidos pelo ramo antigo. `AlarmProcessor.process`. |
| Termo de p1 depende de p0? | Não. Cada grupo mantém RaftState, eleitorado e tópico de eleição próprios; os relógios causais entre partições não fundem os termos. |

### Verificação dos critérios de aceitação

O funcionamento está ensaiado; a compreensão do aluno só pode ser avaliada na
defesa. Esta tabela responde às perguntas da ficha e indica o que demonstrar.

| Critério | Verificação e resposta baseada na execução |
|---|---|
| CA1: n1 falha em t=15 da Figura 1 | Com n2 também parado, n3 tem apenas um voto e não se elege. p1 perde disponibilidade para nova liderança. |
| CA1: n2 regressa com seq=3 | Reinicia seguidor, não recupera automaticamente o cargo. Se regressasse antes da eleição, poderia candidatar-se no termo 2 e o histórico atualizado favoreceria os votos; não há garantia de que ganharia. |
| CA1: dados e rede | p1 agrupa sensores; isolamento de rede separa comunicações do mesmo grupo. Os termos de p0/p1 são independentes. |
| CA2: persistência | `RaftState.persist` precede pedidos/respostas. Um eleitor que esquece o voto após reboot pode formar duas maiorias; a mutação volátil reproduziu dois líderes no termo 1. |
| CA2: IN na demissão | `input`, `batch` e `election` usam o mesmo monitor do nó ao consultar/alterar RaftState. Depois de adotar termo superior, termIfLeader devolve −1. |
| CA2: novo sorteio | Evita manter candidatos sincronizados e concede uma nova janela ao candidato apoiado; o prazo também é renovado por lote válido. |
| CA3: intervalo das falhas | c1/c2/c3: 6,388 / 6,263 / 5,828 s, dentro de ]4;12,2] s nas hipóteses simplificadas. Perderam-se 62/68/60 IN: 61/64/50 no intervalo e 1/4/10 por truncagem. A taxa de envio e a cauda pendente explicam a diferença. |
| CA3: prazo fixo | Houve 23 termos em 15 grupos, incluindo oito sem líder. Uma sequência de CANDIDATO/VOTO sem LIDER no termo, seguida de nova candidatura, caracteriza aqui o voto dividido observado. Ver as sequências completas no RaftCheck. |
| CA3: truncagem no isolamento | n2 descartou 102 registos, dos quais 88 IN normais ausentes no final; nenhum descartado reapareceu com outro seq nesta execução. A rajada terminou antes da nova liderança e o antigo líder não podia publicar. |
| CA4: escrita definitiva | Um protocolo de commit por maioria, com confirmação ao cliente, impediria considerar definitiva a escrita apenas local; sem quórum sacrifica disponibilidade. Está excluído da ficha. |
| CA4: termo do isolado | O prazo expira repetidamente e incrementa o termo; no regresso, o termo superior demite o líder. Pre-Vote reduziria o efeito, mas não pertence ao âmbito. |
| CA4: reenvio | Confirmação e repetição podem duplicar operações. É necessário um ID estável de pedido e deduplicação persistente, além da proteção por maioria. |

Exemplos reais para a oral: Figura 1 em `suite-1793902448077/a-figura`,
isolamentos em `suite-1793903165685/d-isolamentos` e falta de maioria em
`suite-1793903337223/e-dois-nos`, todos dentro de `execucoes/`.

## Demonstração prática

1. Mostrar os nove nós e um líder em cada partição; abrir os ficheiros estado/hb/eventos.
2. Enviar `S02 20.1` e `S05 23.7`, observar p1 e as três cópias.
3. Para Figura 1, preferir `-Suite a`: coordena a janela entre append e publicação.
4. Mostrar no resultado a candidatura, o novo termo, o regresso e `TRUNCADO`.
5. Para CAP, executar `-Suite d` e mostrar `observacoes.txt`, `isolamentos.txt` e os logs reais.
6. Mostrar `RaftCheck.txt`, `CausalCheck.txt`, métricas e limitações.
7. Não apresentar estes programas como Raft completo.

A análise de resultados e pendências encontra-se em `Relatorio.md`; os
requisitos e evidências encontram-se em `Conformidade.md`.
