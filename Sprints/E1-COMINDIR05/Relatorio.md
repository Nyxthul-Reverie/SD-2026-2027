# COMINDIR05 — implementação, ensaios e reflexão

## Estado e ambiente

Implementação dos três projetos `raft04-*`, construída a partir da COMINDIR04
8360284329b0b3072ba9a3b9ed7cce8cf322e484, preservada.
Validada no Windows com JDK 25.0.1, `javac --release 11 -encoding UTF-8`,
Kubo 0.43.1 e API isolada 5005. Os bytecodes gerados têm major version 55.
Não foram instaladas bibliotecas nem alteradas configurações globais.
Python não está instalado neste ambiente; os ensaios usam Java e PowerShell.

Foram usados **21 ensaios corretos**, **cinco execuções distribuídas com
mutações**, dois testes dirigidos das classes mutadas e a bateria ProtocolTest,
incluindo TCP por socket local com mudança de termo durante a recuperação.
Os verificadores finais foram novamente executados sobre os 21 ensaios corretos:
todos deram PASS e as comparações integrais das cópias feitas pelo harness passaram.

As fontes funcionais da COMINDIR04 foram aproveitadas para input, partição,
vetores, transporte e alarmes. PartitionNode integra o antigo líder e réplica.
RaftState segue o código da ficha. Foram acrescentados validação de eleitor,
falha segura em erro persistente de escrita e repetição limitada de rename
quando Windows devolve AccessDenied; não se publica antes da gravação.

A nomenclatura `raft04` segue a especificação, apesar da numeração COMINDIR05.
A introdução, o cabeçalho da tabela e o rodapé da ficha têm referências
inconsistentes à numeração anterior; não foram usados para alterar o protocolo.

## 1. Resultados dos ensaios corretos

Unidade de indisponibilidade: segundos. "Perdidas intervalo" cruza os IN
enviados entre a falha e a primeira publicação de termo superior com os que
não estão nos históricos finais. "Truncadas normais perdidas" deduplica o
mesmo registo descartado em cópias diferentes e cruza-o com os IN ausentes.
"Registos truncados" soma eventos nas cópias, incluindo alarmes.

| Ensaio | IN registados | Indisponibilidade (s) | Perdidas intervalo | Perdidas totais | Truncadas normais perdidas | Registos truncados | RaftCheck / CausalCheck |
|---|---:|---:|---:|---:|---:|---:|---|
| a-figura | 5 | 8.672 | 1 | 2 | 1 | 1 | PASS / PASS |
| c-falha-1 | 300 | 6.388 | 61 | 62 | 1 | 1 | PASS / PASS |
| c-falha-2 | 300 | 6.263 | 64 | 68 | 4 | 4 | PASS / PASS |
| c-falha-3 | 300 | 5.828 | 50 | 60 | 10 | 10 | PASS / PASS |
| d-isolamentos | 300 | ver isolamento | — | 88 | 88 | 102 | PASS / PASS |
| e-dois-nos | 300 | 37.696 | 81 | 81 | 0 | 0 | PASS / PASS |

No ensaio a houve uma leitura perdida durante a eleição e outra perdida por
truncagem. A réplica antiga regressou com seq=3 no termo antigo; o novo líder
tinha criado outro seq=3 num termo superior. A cauda foi substituída.

No ensaio c cada repetição enviou 300 leituras com o comando `rajada 300 10`.
O 10 representa sensores, e a pausa entre envios é a propriedade
`raft04.intervalo=10` ms. Tempos HTTP e agendamento acrescem a essa pausa.
As três indisponibilidades ficaram no intervalo simplificado ]4;12,2] s.
Uma leitura pode ser aceite perto da primeira publicação; por isso,
enviadas no intervalo e efetivamente perdidas no intervalo não são sempre iguais.

### Arranque aleatório e fixo

| Configuração | Execuções / grupos | Primeiro líder min / médio / máx (s) | Termos por grupo, média | Termos sem líder |
|---|---:|---:|---:|---:|
| Aleatório 6000–12000 ms | 10 / 30 | 6.198 / 7.679 / 9.723 | 1.000 | 0 |
| Fixo 6000 ms, barreira de arranque | 5 / 15 | 6.559 / 9.717 / 12.413 | 1.533 | 8 |

A barreira é apenas instrumentação de arranque: as JVMs esperam pelo mesmo
instante antes de construir os nós. Não altera o protocolo de eleição.
Os grupos com prazo fixo precisaram por vezes de dois termos. A janela de
observação permite registar NA se nenhum líder surgir; nesta execução todos surgiram.
A simultaneidade física absoluta não existe no agendador; o efeito observado
depende também dos tempos de arranque e entrega.

### Tabela pedida em 4.3 — ensaios a–c

| Métrica | Medição |
|---|---|
| Tempo até ao primeiro líder, mínimo/médio/máximo | 6,198 / 8,175 / 12,413 s; 57 observações de grupos em a–c |
| Termos até primeiro líder, aleatório/fixo | 1.000 / 1.533 por grupo no ensaio b |
| Votos divididos, aleatório/fixo | 0 / 8 termos sem líder na janela |
| Indisponibilidade após falha, mínimo/médio/máximo | 5,828 / 6,788 / 8,672 s; quatro falhas em a/c |
| Leituras perdidas, eleição/truncagem | a: 1/1; c1: 61/1; c2: 64/4; c3: 50/10 |
| Truncagem no antigo líder | a: 1; c1: 1; c2: 4; c3: 10 registos |
| Termos com mais de um líder | 0 nos 21 ensaios corretos |
| seq divergentes na coincidência final | 0; comprimentos também iguais nos 21 ensaios |

Média por falha em a/c: 44 IN perdidos durante o intervalo e quatro por
truncagem, 48 no total. Considerando só as três repetições de c: 58,333 /
5,000, total 63,333 IN perdidos por falha. Estas médias usam os cruzamentos
com o histórico final, não apenas a quantidade de envios durante o timeout.

O máximo inicial de 12,413 s inclui arranque/agendamento e, no caso fixo,
uma segunda eleição; o intervalo após falha de um líder não é uma garantia
rígida do tempo de arranque com votos divididos.

### Isolamento e falta de maioria

No ensaio d coexistiram o antigo líder n2/termo 1 e o novo n3/termo 2.
O primeiro continuou a aceitar IN enquanto isolado, mas as escritas não
confirmadas foram descartadas na reparação. Na segunda fase, o seguidor n2
isolado passou do termo 2 para o 8. O regresso provocou adoção de termo superior
e nova coordenação de liderança. Houve coincidência integral no final.

No antigo líder foram atribuídos 102 registos durante o isolamento e
truncados os mesmos 102, incluindo alarmes e 88 IN normais. Não reapareceu
nenhum desses registos com outro seq no histórico final. O processador
registou SUSPEITA de p1, depois NOVO_LIDER no termo 2 e, após o regresso do
seguidor, no termo 9. Os timestamps e mensagens estão nos logs selecionados.

No ensaio e não surgiu líder novo durante os 30 s com apenas um nó vivo.
Após o regresso de um segundo nó, a eleição demorou 7,650 s. A indisponibilidade
total de 37,696 s inclui o período imposto sem maioria; não está sujeita ao
limite simplificado para uma falha com maioria sobrevivente.

| Cenário | Maioria aceita? | Minoria aceita? | Escritas da minoria após reparar | Termos | Interpretação C/A |
|---|---|---|---|---|---|
| Falha de líder (c) | Após eleição | Nó parado não aceita | Cauda antiga pode desaparecer | Avança após falha | Indisponibilidade transitória; sem garantia de commit |
| Líder isolado (d) | Após eleição de novo líder | Antigo líder aceita IN | Truncadas se não constam do novo histórico | 1 e 2 simultaneamente observados | Aceitação nos dois lados; não é CP rigoroso |
| Seguidor isolado (d) | Líder conectado continua | Seguidor não aceita como líder | Recupera histórico ao regressar | 2→8 no isolado | Mantém serviço no lado conectado; regresso causa perturbação |
| Dois nós parados (e) | Não há maioria inicialmente | Sobrevivente candidato não cria registos | IN não aceites perdem-se | Candidaturas sucessivas | Disponibilidade sacrificada enquanto não há maioria |

## 2. Cinco falhas deliberadas

As mutações foram compiladas em diretórios próprios, sem substituir os fontes
corretos. Estão excluídas do classpath normal e do Git.

| Falha | Experiência e observação | RaftCheck | CausalCheck | Conclusão |
|---|---|---|---|---|
| Maioria incorreta | Teste dirigido: repetir voto próprio elegeu com um voto distinto. Arranque distribuído: não apareceu eleição inválida, porque o handler ignora mensagens próprias e a primeira resposta externa já totaliza dois votos | FAIL dirigido; PASS na rede | PASS | A falha da API existe, mas o cenário de rede executado não a manifestou |
| Termo/voto apenas em memória | Eleitor reiniciado esqueceu o voto; na rede dois líderes foram eleitos no termo 1; duas regressões de termo e nove estados persistentes ausentes/inválidos | FAIL | PASS | Sem persistência perde-se segurança da eleição |
| Sem upToDate | Candidato atrasado eleito; dois IN desapareceram do histórico e quatro cópias de registos foram truncadas | PASS | PASS | Os verificadores de segurança causal/eleição não provam preservação de dados; a asserção do ensaio confirmou perda do histórico atualizado |
| Aceitar termo inferior | Após isolamento/reparação reproduziu-se um cabeçalho antigo para tornar a entrega atrasada determinística; dois nós registaram aceitação obsoleta | FAIL, dois lotes obsoletos aceites | PASS | É preciso rejeitar a autoridade antiga antes de tratar o lote |
| Novo líder com L/V zero | O próximo IN foi rejeitado por V[k] diferente de seq; ficaram três IN ausentes, um deles o novo evento que devia ser aceite | PASS | PASS | A verificação impede gravar o evento inválido; os verificadores finais não veem esse evento inexistente |

A mensagem antiga reproduzida no quarto ensaio é instrumentação explícita de
entrega atrasada, guardada em `replay-lote.txt`; não se afirma que o agendamento
natural tenha produzido sozinho esse atraso.
O primeiro e o segundo teste dirigido executam a classe alterada, sem Kubo.
O segundo tem também demonstração com processos e Kubo. Não se confundem estes níveis.

## 3. Problemas encontrados e tratados

1. A execução de scripts estava desativada: os comandos usam ExecutionPolicy
   Bypass apenas na instância PowerShell invocada.
2. O classpath inicial dos testes não incluía HeartbeatMonitor: acrescentado
   o projeto produtor, e recompilação/testes aprovados.
3. A configuração Kubo não tinha a propriedade Enabled exposta: o script
   cria-a na configuração isolada. Foram verificados API 5005 e daemon pronto.
4. O primeiro script de arranque passava -Dfile.encoding sem aspas no PowerShell:
   corrigida a passagem literal do argumento.
5. As rajadas não eram inicialmente gravadas em produtor.txt: corrigido.
   Acrescentada asserção que compara as linhas com os envios publicados.
6. O Windows devolveu AccessDenied durante um ATOMIC_MOVE de estado: o nó
   terminou de forma segura e o ensaio não convergiu. Acrescentadas até 20
   tentativas de rename com espera de 10 ms apenas nesse erro; se persistir,
   a escrita falha e não se publica estado não persistido. A causa externa
   concreta do bloqueio não foi identificada.
7. A primeira consolidação por ConvertTo-Json ficou a consumir CPU/memória:
   foram terminados apenas os dois processos de agregação identificados como
   nossos; a recolha passou a usar linhas simples. Não afetou o protocolo.

A primeira passagem c, `suite-1793902739248`, foi excluída: não tinha contagem
de envios válida e a terceira repetição terminou com erro de persistência.
Os cinco arranques fixos sem barreira da primeira passagem b também não são
os usados na comparação final; foram substituídos pelos cinco sincronizados.
Os dados antigos foram conservados, não reclassificados como sucesso.

## 4. Reflexão crítica — secção 4.3

**Eleição no arranque.** Prazos aleatórios tendem a permitir que um candidato
receba maioria antes de outro expirar. Nesta medição houve zero termos sem líder
com aleatoriedade e oito com prazo fixo. O arranque não define líder: todos
começam seguidores. Um prazo de eleição inferior ao período dos heartbeats
favoreceria eleições mesmo com um líder saudável. Os tempos dependem também
do agendamento real; prazos fixos não garantem bloqueio permanente nem eleição rápida.
Nesta configuração Δ=2 s, ELEICAO_MIN=3Δ=6 s e ELEICAO_MAX=6Δ=12 s:
o prazo mínimo precisa de folga sobre Δ e os atrasos esperados, e o intervalo
entre mínimo e máximo deve permitir dispersão das candidaturas. Δ não é
um limite superior de entrega da rede.

**O termo.** Duas maiorias de três nós intersetam-se. Um eleitor persistente não
vota em dois candidatos no mesmo termo, logo não se elegem dois líderes nesse
termo sob as hipóteses do modelo. Isto não impede que um líder isolado continue
a julgar-se líder de um termo anterior. Ao regressar adota o termo superior e
demite-se. Termo e Lamport são contadores lógicos, mas têm responsabilidades
diferentes: época de liderança versus ordem dos eventos aceites.

**CAP e particionamento.** Partição de dados é a distribuição por sensor;
partição de rede é perda de comunicação entre nós. Só a maioria pode eleger um
novo líder. Nesta ficha o antigo líder isolado ainda aceita IN, pelo que não
há consistência linearizável de escritas aceites. A confirmação por maioria
antes de declarar uma escrita definitiva é um mecanismo do Raft completo
que está expressamente excluído; teria custo de latência e indisponibilidade
sem quórum. Não foi acrescentado ao protocolo.

**Restrição de voto e truncagem.** A comparação do último termo e seq evita
eleger um candidato menos atualizado do que um eleitor que precisa para a
maioria. Não garante a sobrevivência de uma entrada que só o líder antigo
tinha. No ensaio a essa entrada foi substituída. A mutação sem upToDate fez
desaparecer também o histórico existente num nó atualizado. Não há ID de pedido
para demonstrar identidade perfeita entre leituras iguais reenviadas; os
reaparecimentos são comparações de sensor/temperatura, com essa limitação.

**Persistência.** O voto e termo têm de sobreviver ao reinício antes de responder
ou pedir votos. Caso contrário, um eleitor pode conceder uma segunda maioria
no mesmo termo, como se observou. O histórico persistido permite ao novo líder
continuar seq e relógios. inc identificava a execução do líder fixo; o termo
identifica liderança coordenada no grupo. ATOMIC_MOVE não equivale a fsync
nem oferece, por si só, durabilidade absoluta após corte de energia.

**Detetar, suspeitar e eleger.** Na COMINDIR04 a suspeita era essencialmente
informativa. Agora pode custar uma nova eleição, interrupção e truncagem.
O seguidor isolado elevou o termo de 2 para 8 e o seu regresso perturbou o
líder conectado. Uma fase de Pre-Vote pode reduzir essa perturbação, mas não
foi implementada porque está excluída. Um timeout nunca prova morte.

**Limites.** Durante a eleição ou sem maioria, as leituras podem perder-se.
Garantir proteção de todas as escritas aceites exigiria confirmação, e o
reenvio precisaria de identidade/idempotência para não duplicar operações.
Esses mecanismos estão fora desta ficha. Também não há rollback dos alarmes
já emitidos a partir de históricos posteriormente truncados, entrega causal,
leases, leituras linearizáveis, alteração de membros ou compactação.

## 5. Concorrência e revisão final

O monitor do nó coordena papel, termo, revisão do histórico e relógios.
A ordem é nó→RaftState→ficheiro/log; nenhum callback percorre a ordem inversa.
A atribuição de seq não pode acontecer depois da adoção de um novo termo sob
esse monitor. O lote captura o termo antes de sair para HTTP. Uma publicação
já em trânsito pode chegar tarde; o recetor rejeita o termo inferior.

A recuperação TCP não mantém o monitor do nó enquanto espera pela rede.
Antes de instalar a resposta, confirma termo, líder, revisão e isolamento.
Um teste dirigido mudou o termo durante o TCP e verificou que a resposta antiga
não alterou o histórico. Outro percorreu blocos locais 5→3→1 e substituiu só a
cauda a partir de seq=3, preservando o prefixo.

Foram revistos por leitura os pontos de sincronização e executados os testes
dirigidos e distribuídos. Isto não é uma prova formal de todos os interleavings,
nem uma revisão externa por um segundo humano.

## 6. Evidências e reprodução

Ver `Evidencias.md` para caminhos e resultados selecionados e
`Conformidade.md` para a matriz. Os logs completos, ficheiros de estado,
históricos e cópias mutadas estão apenas em `execucoes/`, ignorados pelo Git.
As configurações privadas/chaves do Kubo não foram publicadas.
Os scripts permitem novas execuções com diretórios novos.

Interpretação dos contadores:
- "voto dividido" inclui termos observados sem líder no fim da janela;
- perdas usam multiconjuntos de sensor/temperatura, sem identidade de pedido;
- truncagens contam cópias descartadas, além da contagem de leituras normais;
- RaftCheck compara até ao menor comprimento e reporta comprimentos diferentes;
- o harness exige também igualdade integral nos ensaios corretos;
- CausalCheck verifica invariantes dos ficheiros e relações entre históricos
  finais, não a entrega de todo IN nem o desfazer de efeitos de ramos truncados.

## 7. Conclusão

A implementação correta compila para Java 11, passou nos 21 ensaios principais,
nos testes dirigidos e na reanálise final. As cinco falhas foram exercitadas
em cópias isoladas, com observações distintas e honestas dos verificadores.
Está preparada para demonstração técnica no ambiente ensaiado, com os limites
da ficha explicitados. Não é Raft completo nem um serviço CP rigoroso.
A preparação oral individual deve usar o guia do README e os logs reais.

Não foi executada uma JVM Java 11: a JVM disponível era Java 25. A
compatibilidade de compilação/API foi verificada por --release 11 e major
version 55. A configuração de arranque do VS Code foi verificada por leitura
e parsing; os nove processos foram efetivamente ensaiados pelo harness Java,
sem automatizar os cliques no IDE.
