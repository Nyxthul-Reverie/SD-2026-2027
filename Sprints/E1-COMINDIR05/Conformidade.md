# Matriz integral de conformidade COMINDIR05

Fonte: [enunciado, issue #18](https://github.com/cunhaestgv/SD-2026-2027/issues/18) e missão fornecida pelo utilizador. **135 itens** rastreados. Os caminhos são relativos a esta pasta. Os métodos identificam a implementação, mesmo quando os números de linha mudam.

As classes comuns estão copiadas nos três projetos; HeartbeatMonitor existe nos dois clientes. As referências abaixo apontam uma cópia representativa. “VALIDADO” designa a evidência executada ou a revisão identificada, não uma prova formal. A ficha exige compreensão pessoal na oral, que não pode ser certificada por estes testes.

Evidências: **a** = suite-1793902448077/a-figura; **b aleatório** = suite-1793902523735/b-aleatorio-0..9; **b fixo** = suite-1793903796551/b-fixo-sincronizado-0..4; **c** = suite-1793902978656/c-falha-1..3; **d** = suite-1793903165685/d-isolamentos; **e** = suite-1793903337223/e-dois-nos. Todas sob execucoes/. **faults** distribuídas = suite-1793903936651; dirigidas = suite-1793903081853. Ver [Evidencias.md](Evidencias.md) e [Relatorio.md](Relatorio.md) para resultados, limitações e exclusão de tentativas inválidas.

**ProtocolTest** foi novamente executado após o build final e deu PASS, incluindo o recuo TCP por blocos e a rejeição de resposta obsoleta. Os 21 ensaios corretos deram PASS nos dois verificadores na reanálise final. “CausalCheck PASS” numa mutação não significa que essa mutação esteja correta.

| ID | Secção / origem | Requisito | Ficheiro | Método / ponto | Evidência | Estado |
|---|---|---|---|---|---|---|
| G01 | Material | Três projetos raft04-* | COMINDIR05.code-workspace; três pastas raft04-* | inventário | inventário | VALIDADO — ensaios / revisão |
| G02 | Material | Pacote e tópicos raft04 | raft04-no/src/raft04/Wire.java | constantes; topicElection/nodePort/isolated | compilação / ensaios | VALIDADO — ensaios / revisão |
| G03 | Material | N=3 e G=3, nove nós | raft04-no/src/raft04/PartitionNode.java; testes/Experiments.java | main; Run | b | VALIDADO — ensaios / revisão |
| S01 | 4.1/1 | Arranque como seguidor | raft04-no/src/raft04/RaftState.java | construtor; tick/onVoteRequest/onVote/onBatch | ProtocolTest | VALIDADO — teste dirigido |
| S02 | 4.1/1 | Carregar termo persistente | raft04-no/src/raft04/RaftState.java | construtor; tick/onVoteRequest/onVote/onBatch | ProtocolTest | VALIDADO — teste dirigido |
| S03 | 4.1/1 | Carregar voto persistente | raft04-no/src/raft04/RaftState.java | construtor; tick/onVoteRequest/onVote/onBatch | ProtocolTest | VALIDADO — teste dirigido |
| S04 | 4.1/1 | Ficheiro temporário e ATOMIC_MOVE | raft04-no/src/raft04/RaftState.java | persist | ProtocolTest | VALIDADO — teste dirigido |
| S05 | 4.1/1 | Termo/voto gravados antes das mensagens | raft04-no/src/raft04/RaftState.java; raft04-no/src/raft04/PartitionNode.java; raft04-no/src/raft04/VoteRequest.java | tick/onVoteRequest; election; encode/decode | ProtocolTest; a | VALIDADO — teste dirigido |
| S06 | 4.1/1 | Termo crescente | raft04-no/src/raft04/RaftState.java | tick/adoptTerm | RaftCheck; a-e | VALIDADO — ensaios / revisão |
| S07 | 4.1/1 | Prazo aleatório local monotónico | raft04-no/src/raft04/RaftState.java | resetTimer | b | VALIDADO — ensaios / revisão |
| S08 | 4.1/1 | Nova eleição após expirar sem maioria | raft04-no/src/raft04/RaftState.java | tick | e | VALIDADO — ensaios / revisão |
| S09 | 4.1/1 | Voto em si e contagem distinta | raft04-no/src/raft04/RaftState.java | tick/onVote | ProtocolTest | VALIDADO — teste dirigido |
| S10 | 4.1/1 | Maioria estrita | raft04-no/src/raft04/RaftState.java | onVote | ProtocolTest; RaftCheck | VALIDADO — teste dirigido |
| S11 | 4.1/1 | Um candidato por eleitor/termo | raft04-no/src/raft04/RaftState.java; raft04-no/src/raft04/VoteRequest.java | onVoteRequest; encode/decode | ProtocolTest | VALIDADO — teste dirigido |
| S12 | 4.1/1 | Restrição último termo, depois seq | raft04-no/src/raft04/RaftState.java; raft04-no/src/raft04/VoteRequest.java | onVoteRequest; encode/decode | ProtocolTest | VALIDADO — teste dirigido |
| S13 | 4.1/1 | Adotar termo superior e demitir | raft04-no/src/raft04/RaftState.java | adoptTerm | ProtocolTest; a,d | VALIDADO — teste dirigido |
| S14 | 4.1/1 | Ignorar lote de termo inferior | raft04-no/src/raft04/RaftState.java; raft04-no/src/raft04/Batch.java | onBatch; construtor; encode/decode | ProtocolTest; d | VALIDADO — teste dirigido |
| S15 | 4.1/1 | Candidato reconhece lote do termo | raft04-no/src/raft04/RaftState.java; raft04-no/src/raft04/Batch.java | onBatch; construtor; encode/decode | a,b | VALIDADO — ensaios / revisão |
| S16 | 4.1/1 | Renovar prazo no voto e lote válido | raft04-no/src/raft04/RaftState.java; raft04-no/src/raft04/Batch.java; raft04-no/src/raft04/VoteRequest.java | onVoteRequest/onBatch; construtor; encode/decode; encode/decode | a,b | VALIDADO — ensaios / revisão |
| M01 | 4.1/2 | LOTE com termo/líder/últimoTermo | raft04-no/src/raft04/Batch.java | construtor; encode/decode | ProtocolTest; a | VALIDADO — teste dirigido |
| M02 | 4.1/2 | Registo de sete campos | raft04-no/src/raft04/SensorRecord.java | construtor; parse/toLine | ProtocolTest; a-e | VALIDADO — teste dirigido |
| M03 | 4.1/2 | Codificação/descodificação PEDIDO_VOTO | raft04-no/src/raft04/VoteRequest.java | encode/decode | ProtocolTest | VALIDADO — teste dirigido |
| M04 | 4.1/2 | Codificação/descodificação VOTO | raft04-no/src/raft04/VoteReply.java | encode/decode | ProtocolTest | VALIDADO — teste dirigido |
| M05 | 4.1/2 | Constantes 2s, 6-12s, verificação 200ms | raft04-no/src/raft04/Wire.java | constantes; topicElection/nodePort/isolated | leitura; b | VALIDADO — ensaios / revisão |
| M06 | 4.1/2 | Propriedades de sistema da ficha | raft04-no/src/raft04/Wire.java | constantes; topicElection/nodePort/isolated | b / testes | VALIDADO POR LEITURA E ENSAIOS; sem prova formal |
| M07 | 4.1/2 | Porto distinto por nó e partição | raft04-no/src/raft04/Wire.java | nodePort | a-e | VALIDADO — ensaios / revisão |
| M08 | 4.1/2 | Marcador isolar-pk-ni | raft04-no/src/raft04/Wire.java | isolated | d | VALIDADO — ensaios / revisão |
| N01 | 4.1/3 | Ler histórico e metadados locais | raft04-no/src/raft04/PartitionNode.java; raft04-no/src/raft04/RecordFile.java | construtor; input/election/batch/sendBatch/recover; construtor; append/range/replaceFrom | a | VALIDADO — ensaios / revisão |
| N02 | 4.1/3 | Servidor TCP em qualquer nó | raft04-no/src/raft04/PartitionNode.java | serve/start | a,d | VALIDADO — ensaios / revisão |
| N03 | 4.1/3 | Três subscrições por nó | raft04-no/src/raft04/PartitionNode.java | start | a-e | VALIDADO — ensaios / revisão |
| N04 | 4.1/3 | Ignorar publicações próprias | raft04-no/src/raft04/PartitionNode.java | election/batch | a-e | VALIDADO — ensaios / revisão |
| N05 | 4.1/3 | Verificação periódica e pedido de voto | raft04-no/src/raft04/PartitionNode.java | electionTick | b,e | VALIDADO — ensaios / revisão |
| N06 | 4.1/3 | Responder a voto concedido/recusado | raft04-no/src/raft04/PartitionNode.java | election | ProtocolTest; b | VALIDADO — teste dirigido |
| N07 | 4.1/3 | Eventos temporizados por nó | raft04-no/src/raft04/RaftState.java; raft04-no/src/raft04/MonitorLog.java | construtor; tick/onVoteRequest/onVote/onBatch; accept | RaftCheck | VALIDADO — ensaios / revisão |
| L01 | 4.1/4 | Restaurar seq, L e V na promoção | raft04-no/src/raft04/PartitionNode.java | restoreClocks/election | a,c,d | VALIDADO — ensaios / revisão |
| L02 | 4.1/4 | Reiniciar hb e lista de pendentes | raft04-no/src/raft04/PartitionNode.java | election | a,c | VALIDADO — ensaios / revisão |
| L03 | 4.1/4 | Primeiro lote imediato | raft04-no/src/raft04/PartitionNode.java; raft04-no/src/raft04/Batch.java | election/sendBatch; construtor; encode/decode | a,b | VALIDADO — ensaios / revisão |
| L04 | 4.1/4 | Validar liderança dentro do monitor | raft04-no/src/raft04/PartitionNode.java | input | leitura; ensaios | VALIDADO POR LEITURA E ENSAIOS; sem prova formal |
| L05 | 4.1/4 | L=max(local,msg)+1 | raft04-no/src/raft04/PartitionNode.java | input | CausalCheck | VALIDADO — ensaios / revisão |
| L06 | 4.1/4 | Merge V e tick da partição | raft04-no/src/raft04/PartitionNode.java | input | CausalCheck | VALIDADO — ensaios / revisão |
| L07 | 4.1/4 | V[k]=seq | raft04-no/src/raft04/PartitionNode.java | input | ProtocolTest; CausalCheck | VALIDADO — teste dirigido |
| L08 | 4.1/4 | Persistir antes de pendentes/publicação | raft04-no/src/raft04/PartitionNode.java | input | a | VALIDADO — ensaios / revisão |
| L09 | 4.1/4 | Fecho consistente sob mesmo monitor | raft04-no/src/raft04/PartitionNode.java; raft04-no/src/raft04/Batch.java | sendBatch; construtor; encode/decode | leitura | VALIDADO POR LEITURA E ENSAIOS; sem prova formal |
| L10 | 4.1/4 | HTTP fora do bloqueio | raft04-no/src/raft04/PartitionNode.java; raft04-no/src/raft04/Batch.java | sendBatch/publish; construtor; encode/decode | leitura; ensaios | VALIDADO POR LEITURA E ENSAIOS; sem prova formal |
| L11 | 4.1/4 | Cancelar tarefa após demissão | raft04-no/src/raft04/PartitionNode.java | demotionCheck | a,d | VALIDADO — ensaios / revisão |
| L12 | 4.1/4 | Heartbeat e eleição não alteram L/V | raft04-no/src/raft04/RaftState.java; raft04-no/src/raft04/PartitionNode.java | construtor; tick/onVoteRequest/onVote/onBatch; construtor; input/election/batch/sendBatch/recover | CausalCheck | VALIDADO — ensaios / revisão |
| F01 | 4.1/5 | Filtrar liderança obsoleta antes de aplicar | raft04-no/src/raft04/PartitionNode.java | batch | d; ProtocolTest | VALIDADO — teste dirigido |
| F02 | 4.1/5 | Duplicados por seq e termo | raft04-no/src/raft04/PartitionNode.java | batch | leitura; a | VALIDADO POR LEITURA E ENSAIOS; sem prova formal |
| F03 | 4.1/5 | Aplicar registo seguinte | raft04-no/src/raft04/PartitionNode.java | batch | a-e | VALIDADO — ensaios / revisão |
| F04 | 4.1/5 | Recuperar lacuna TCP | raft04-no/src/raft04/PartitionNode.java | request/recover | a,c | VALIDADO — ensaios / revisão |
| F05 | 4.1/5 | Comparar comprimento e último termo | raft04-no/src/raft04/PartitionNode.java | batch | a,d | VALIDADO — ensaios / revisão |
| F06 | 4.1/5 | Recuar por blocos de termo | raft04-no/src/raft04/PartitionNode.java | recover | a,d | VALIDADO — ensaios / revisão |
| F07 | 4.1/5 | Localizar primeira divergência | raft04-no/src/raft04/PartitionNode.java | recover | a | VALIDADO — ensaios / revisão |
| F08 | 4.1/5 | Reescrita atómica sem cauda divergente | raft04-no/src/raft04/RecordFile.java | replaceFrom | ProtocolTest; a | VALIDADO — teste dirigido |
| F09 | 4.1/5 | TRUNCADO com intervalo/quantidade/termos | raft04-no/src/raft04/PartitionNode.java | recover | a; RaftCheck | VALIDADO — ensaios / revisão |
| F10 | Concorrência | Invalidar resposta TCP por revisão/termo | raft04-no/src/raft04/PartitionNode.java | recover | leitura; cenários de falha | VALIDADO — ensaios / revisão |
| I01 | 4.1/2 | Bloquear publicação de dados/eleição no isolamento | raft04-no/src/raft04/PartitionNode.java; raft04-no/src/raft04/Batch.java | publish/sendBatch; construtor; encode/decode | d | VALIDADO — ensaios / revisão |
| I02 | 4.1/2 | Bloquear processamento de dados/eleição | raft04-no/src/raft04/PartitionNode.java | batch/election | d | VALIDADO — ensaios / revisão |
| I03 | 4.1/2 | Bloquear TCP cliente/servidor | raft04-no/src/raft04/PartitionNode.java | request/serve | d | VALIDADO — ensaios / revisão |
| I04 | 4.1/2 | Continuar receção IN durante isolamento | raft04-no/src/raft04/PartitionNode.java | input | d | VALIDADO POR LEITURA E ENSAIOS; sem prova formal |
| A01 | 4.1/6 | Processador subscreve todas as partições | raft04-processador/src/raft04/AlarmProcessor.java | start | a,c,d | VALIDADO — ensaios / revisão |
| A02 | 4.1/6 | Processador rejeita termos inferiores | raft04-produtor/src/raft04/HeartbeatMonitor.java; raft04-processador/src/raft04/AlarmProcessor.java | onHeartbeat/check; receive | ProtocolTest; d | VALIDADO — teste dirigido |
| A03 | 4.1/6 | Deduplicação p/seq/termo | raft04-processador/src/raft04/AlarmProcessor.java | process | leitura; c,d | VALIDADO POR LEITURA E ENSAIOS; sem prova formal |
| A04 | 4.1/6 | Preservar carimbos e alarmes | raft04-processador/src/raft04/AlarmProcessor.java | process | CausalCheck; c | VALIDADO — ensaios / revisão |
| A05 | 4.1/6 | Aviso no destino suspeito e envio | raft04-processador/src/raft04/AlarmProcessor.java | process | leitura; c,d | VALIDADO — ensaios / revisão |
| P01 | 4.1/7 | Produtor interpreta cabeçalho e monitora | raft04-produtor/src/raft04/Producer.java | main | a-e | VALIDADO — ensaios / revisão |
| P02 | 4.1/7 | Uma linha por IN em produtor.txt | raft04-produtor/src/raft04/Producer.java | sent | a,c,d,e | VALIDADO — ensaios / revisão |
| P03 | 4.1/7 | Aviso e envio mesmo suspeito | raft04-produtor/src/raft04/Producer.java | warn | c,e | VALIDADO — ensaios / revisão |
| C01 | 4.1/8 | Sequência de termos/candidatos/votos/líderes | raft04-no/src/raft04/RaftCheck.java | analyse | b | VALIDADO — ensaios / revisão |
| C02 | 4.1/8 | Votos divididos e duração eleições | raft04-no/src/raft04/RaftCheck.java | analyse | b | VALIDADO — ensaios / revisão |
| C03 | 4.1/8 | Segurança de eleição e termo monotónico | raft04-no/src/raft04/RaftCheck.java | analyse | a-e; FaultTest | VALIDADO — ensaios / revisão |
| C04 | 4.1/8 | Tempo até primeiro líder | raft04-no/src/raft04/RaftCheck.java | analyse | b | VALIDADO — ensaios / revisão |
| C05 | 4.1/8 | Indisponibilidade com falhas anotadas | raft04-no/src/raft04/RaftCheck.java | analyse | a,c,e | VALIDADO — ensaios / revisão |
| C06 | 4.1/8 | Leituras no intervalo sem líder | raft04-no/src/raft04/RaftCheck.java | analyse | a,c,e | VALIDADO — ensaios / revisão |
| C07 | 4.1/8 | Perdas no histórico final | raft04-no/src/raft04/RaftCheck.java | analyse | a,c | VALIDADO — ensaios / revisão |
| C08 | 4.1/8 | Truncados e reaparecidos com outro seq | raft04-no/src/raft04/RaftCheck.java | analyse | a,d | VALIDADO — ensaios / revisão |
| C09 | 4.1/8 | Comparação três cópias até mínimo | raft04-no/src/raft04/RaftCheck.java | analyse | a-e | VALIDADO — ensaios / revisão |
| C10 | CA3 | CausalCheck novo formato | raft04-no/src/raft04/CausalCheck.java | main | a-e | VALIDADO — ensaios / revisão |
| T01 | 4.2/9a | Figura 1 integral | testes/Experiments.java | figure | a | VALIDADO — ensaios / revisão |
| T02 | 4.2/9b | Dez arranques aleatórios | testes/Experiments.java | main | b | VALIDADO — ensaios / revisão |
| T03 | 4.2/9b | Cinco arranques com prazo fixo | testes/Experiments.java | main | b | VALIDADO — ensaios / revisão |
| T04 | 4.2/9c | Três falhas durante rajada e reinício 30s | testes/Experiments.java | failure | c | VALIDADO — ensaios / revisão |
| T05 | 4.2/9d | Isolar líder 30s e reparar | testes/Experiments.java | partition | d | VALIDADO — ensaios / revisão |
| T06 | 4.2/9d | Isolar seguidor 60s e reparar | testes/Experiments.java | partition | d | VALIDADO — ensaios / revisão |
| T07 | 4.2/9e | Dois nós parados, 30s de leituras, regresso | testes/Experiments.java | quorum | e | VALIDADO — ensaios / revisão |
| X01 | 4.2/10 | Mutação maioria incorreta | testes/Experiments.java; testes/FaultTest.java | mutate; main | faults | EXECUTADO: FAIL dirigido; rede não manifestou falha |
| X02 | 4.2/10 | Mutação termo/voto voláteis e reinício | testes/Experiments.java; testes/FaultTest.java | mutate; main | faults | EXECUTADO: dois líderes/termo; RaftCheck FAIL |
| X03 | 4.2/10 | Mutação sem upToDate, candidato atrasado | testes/Experiments.java | outdatedCandidate | faults | EXECUTADO: histórico perdido; verificadores PASS |
| X04 | 4.2/10 | Mutação aceita lotes obsoletos | testes/Experiments.java | partition | faults | EXECUTADO: dois lotes obsoletos; RaftCheck FAIL |
| X05 | 4.2/10 | Mutação relógios zero na promoção | testes/Experiments.java | figure | faults | EXECUTADO: IN rejeitado; verificadores PASS |
| D01 | 4.3/11 | Tabela métricas medidas | Relatorio.md | documentação / tabelas | agregação | DOCUMENTADO; compreensão individual por avaliar |
| D02 | 4.3/11 | Tabela CAP por cenário | Relatorio.md | documentação / tabelas | a-e | DOCUMENTADO; compreensão individual por avaliar |
| D03 | 4.3/12 | Reflexão e respostas CA1-CA4 | Relatorio.md; README.md | documentação / tabelas | revisão | DOCUMENTADO; compreensão individual por avaliar |
| O01 | CA1-CA4 | Referências reais para defesa oral | README.md | documentação / tabelas | revisão | DOCUMENTADO; compreensão individual por avaliar |
| B01 | Âmbito | Sem commit, Pre-Vote, leases, snapshots | raft04-no/src/raft04/*.java | revisão de âmbito | revisão | VALIDADO POR LEITURA; exclusões respeitadas |
| B02 | Proteção | COMINDIR01-04 e quatro txt intactos | COMINDIR01–04; quatro txt preservados | SHA-256 + git diff | verificação final | VALIDADO: 143 hashes iguais; sem alterações externas |
| G04 | Material | Batch nos três projetos; HeartbeatMonitor nos dois clientes | raft04-no/src/raft04/Batch.java; raft04-produtor/src/raft04/HeartbeatMonitor.java | construtor; encode/decode; onHeartbeat/check | comparação das cópias e build | VALIDADO — ensaios / revisão |
| G05 | Material | Arrancar nós, processador e produtor | testes/Experiments.java | Run | a,c,d,e | VALIDADO — ensaios / revisão |
| G06 | Material | Todos os processos na mesma máquina | testes/Experiments.java | Run | 21 ensaios / timestamps | VALIDADO — ensaios / revisão |
| Q01 | 4.1/1 — Antes de avançar | Repor voto ao mudar termo e repetir voto no mesmo candidato | raft04-no/src/raft04/RaftState.java; raft04-no/src/raft04/VoteRequest.java | adoptTerm/onVoteRequest; encode/decode | ProtocolTest; guia oral | DOCUMENTADO; compreensão individual por avaliar |
| Q02 | 4.1/1 — Antes de avançar | Condição do ERRO de dois líderes e argumento de impossibilidade | raft04-no/src/raft04/RaftState.java; raft04-no/src/raft04/Batch.java | onBatch; construtor; encode/decode | teste volátil; guia oral | DOCUMENTADO; compreensão individual por avaliar |
| Q03 | 4.1/1 — Antes de avançar | ATOMIC_MOVE e falta de durabilidade física garantida | raft04-no/src/raft04/RaftState.java | persist | leitura; reflexão | DOCUMENTADO; compreensão individual por avaliar |
| Q04 | 4.1/3 — Antes de avançar | Falha entre persistir e publicar candidatura | raft04-no/src/raft04/RaftState.java | construtor; tick/onVoteRequest/onVote/onBatch | ProtocolTest; guia oral | DOCUMENTADO; compreensão individual por avaliar |
| Q05 | 4.1/3 — Antes de avançar | Resposta recusada informa termo; prazo até primeiro lote | raft04-no/src/raft04/PartitionNode.java | election | b; guia oral | DOCUMENTADO; compreensão individual por avaliar |
| Q06 | 4.1/4 — Antes de avançar | Ordem dos monitores e deadlock | raft04-no/src/raft04/PartitionNode.java | input/election/batch | revisão de chamadas; relatório §5 | DOCUMENTADO; compreensão individual por avaliar |
| Q07 | 4.1/4 — Antes de avançar | Restaurar relógios e falha se começar em zero | raft04-no/src/raft04/PartitionNode.java | restoreClocks | mutação zero; ProtocolTest | DOCUMENTADO; compreensão individual por avaliar |
| Q08 | 4.1/4 — Antes de avançar | Reenvio de IN recebido mas não publicado no termo anterior | raft04-no/src/raft04/PartitionNode.java | input | a; guia oral / ausência de ID | DOCUMENTADO; compreensão individual por avaliar |
| Q09 | 4.1/6 — Antes de avançar | Mesmo seq noutro termo e contagem de alarmes | raft04-processador/src/raft04/AlarmProcessor.java | process | leitura; guia oral | DOCUMENTADO; compreensão individual por avaliar |
| AC01 | CA1 | Definir termo, maioria, upToDate, C/A/P e aplicar Figura 1 | README.md; Relatorio.md | documentação / tabelas | guia e ensaio a | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC02 | CA1/verificação 1 | Falha de n1 em t=15 e indisponibilidade de p1 | README.md | documentação / tabelas | tabela CA; e para ausência de quórum | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC03 | CA1/verificação 2 | Regresso de n2 antes/depois da eleição | README.md | documentação / tabelas | tabela CA; a | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC04 | CA1/verificação 3 | Partição de dados/rede e termos independentes | README.md | documentação / tabelas | tabela CA; d | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC05 | CA2 | Localizar persistência, votação, demissão, promoção e locks | raft04-no/src/raft04/RaftState.java; raft04-no/src/raft04/PartitionNode.java | construtor; tick/onVoteRequest/onVote/onBatch; construtor; input/election/batch/sendBatch/recover | ProtocolTest; a-e; revisão | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC06 | CA2/verificação 1 | Persistir antes de emitir e sequência que quebra segurança | raft04-no/src/raft04/RaftState.java | persist | mutação volátil distribuída | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC07 | CA2/verificação 2 | IN simultâneo com adoção de novo termo | raft04-no/src/raft04/PartitionNode.java | input/election/batch | revisão sob monitor comum; ensaios | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC08 | CA2/verificação 3 | Sortear prazo em nova eleição e voto concedido | raft04-no/src/raft04/RaftState.java | resetTimer | b aleatório/fixo; ProtocolTest | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC09 | CA3 | Demonstrar métricas, segurança e relógios através de eleições | raft04-no/src/raft04/RaftCheck.java; raft04-no/src/raft04/CausalCheck.java | analyse/main; main | 21 ensaios e evidências selecionadas | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC10 | CA3/verificação 1 | Intervalo previsto para indisponibilidades e relação com perdas | Relatorio.md; README.md | documentação / tabelas | c1/c2/c3: 6,388/6,263/5,828 s | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC11 | CA3/verificação 2 | Termos no prazo fixo e evento que caracteriza voto dividido | raft04-no/src/raft04/RaftCheck.java | analyse | b fixo: 23 termos/15 grupos; oito sem líder | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC12 | CA3/verificação 3 | Truncagem do antigo líder e reaparecimento | Relatorio.md; README.md | documentação / tabelas | d: 102 descartados; 88 IN ausentes; zero reaparecidos | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC13 | CA4 | Duas limitações e mecanismos para as resolver | Relatorio.md; README.md | documentação / tabelas | sem commit; sem ID/reenvio; sem Pre-Vote | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC14 | CA4/verificação 1 | Commit por maioria, escrita definitiva e CAP | Relatorio.md; README.md | documentação / tabelas | d; tabela CAP e reflexão | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC15 | CA4/verificação 2 | Termo crescente do isolado e Pre-Vote | Relatorio.md; README.md | documentação / tabelas | d: seguidor termo 2→8 | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| AC16 | CA4/verificação 3 | Reenvios, duplicados e identidade de pedido | Relatorio.md; README.md | documentação / tabelas | guia oral; limites explícitos | EVIDÊNCIA TÉCNICA / RESPOSTA DOCUMENTADA; oral por avaliar |
| R01 | 4.3/12 — Eleição | Aleatoriedade, ordem de arranque, Δ e prazos | Relatorio.md | documentação / tabelas | b: 0/8 termos sem líder | DOCUMENTADO; compreensão individual por avaliar |
| R02 | 4.3/12 — Termo | Prova por interseção, líderes em épocas distintas e Lamport | Relatorio.md | documentação / tabelas | d: termos 1/2 coexistem | DOCUMENTADO; compreensão individual por avaliar |
| R03 | 4.3/12 — CAP | Classificar cenários e explicar ausência de CP rigoroso | Relatorio.md | documentação / tabelas | tabela c–e preenchida | DOCUMENTADO; compreensão individual por avaliar |
| R04 | 4.3/12 — Restrição | O que upToDate protege e perdas sem commit | Relatorio.md | documentação / tabelas | a e mutação sem restrição | DOCUMENTADO; compreensão individual por avaliar |
| R05 | 4.3/12 — Persistência | Persistir termo/voto/registos; comparar com inc | Relatorio.md | documentação / tabelas | teste volátil e reinícios | DOCUMENTADO; compreensão individual por avaliar |
| R06 | 4.3/12 — Suspeita | Custo de falsa suspeita, regresso do isolado e Pre-Vote | Relatorio.md | documentação / tabelas | d; seguidor atingiu termo 8 | DOCUMENTADO; compreensão individual por avaliar |
| R07 | 4.3/12 — Limites | Leituras sem líder; confirmação/reenvio sem duplicação | Relatorio.md | documentação / tabelas | c/e e limites do âmbito | DOCUMENTADO; compreensão individual por avaliar |
| V01 | Missão/compilação | Compilar os três projetos para Java 11 | compilar.ps1 | compilação dos três projetos | javac --release 11; todos os bytecodes major 55 | PASS — três projetos |
| V02 | Missão/reprodução | Configurações portáveis de VS Code e scripts válidos | COMINDIR05.code-workspace; */.vscode/*.json; *.ps1 | parsing / configuração | sete JSON e quatro scripts parseados; build/testes | PASS — parsing e comandos |
| V03 | Missão/rigor | Identificar tentativas inválidas e deteções que não ocorreram | Relatorio.md; Evidencias.md | documentação / tabelas | c inicial excluído; mutações com PASS interpretado | DOCUMENTADO SEM RECLASSIFICAR FALHAS |
| V04 | Missão/âmbito | Mutações não substituem código correto nem entram no Git | testes/Experiments.java; .gitignore | mutate / regras de exclusão | cópias dentro de execucoes/; revisão do staging | VALIDADO — cópias isoladas |

A interpretação oral, a apresentação presencial e a avaliação do professor não foram executadas. A implementação e os dados permitem prepará-las. Não houve teste de falha física de disco/corte de energia, prova formal de todos os interleavings, ensaio entre máquinas ou implementação de mecanismos expressamente excluídos.
