# Matriz de rastreabilidade COMINDIR05

Fonte: issue #18 / enunciado fornecido pelo utilizador. Estado inicial desta matriz: implementação existente, validação distribuída em curso. `IMPLEMENTADO` não significa ensaio aprovado. As conclusões de execução serão atualizadas após terminar a bateria.

| ID | Origem | Requisito | Componente/método | Evidência prevista | Estado |
|---|---|---|---|---|---|
| G01 | Material | Três projetos raft04-* | estrutura | inventário | IMPLEMENTADO; VALIDACAO EM CURSO |
| G02 | Material | Pacote e tópicos raft04 | Wire | compilação / ensaios | IMPLEMENTADO; VALIDACAO EM CURSO |
| G03 | Material | N=3 e G=3, nove nós | PartitionNode.main; Experiments.Run.boot | b | IMPLEMENTADO; VALIDACAO EM CURSO |
| S01 | 4.1/1 | Arranque como seguidor | RaftState construtor | ProtocolTest | IMPLEMENTADO; VALIDACAO EM CURSO |
| S02 | 4.1/1 | Carregar termo persistente | RaftState construtor | ProtocolTest | IMPLEMENTADO; VALIDACAO EM CURSO |
| S03 | 4.1/1 | Carregar voto persistente | RaftState construtor | ProtocolTest | IMPLEMENTADO; VALIDACAO EM CURSO |
| S04 | 4.1/1 | Ficheiro temporário e ATOMIC_MOVE | RaftState.persist | ProtocolTest | IMPLEMENTADO; VALIDACAO EM CURSO |
| S05 | 4.1/1 | Termo/voto gravados antes das mensagens | RaftState.tick/onVoteRequest; PartitionNode.election | ProtocolTest; a | IMPLEMENTADO; VALIDACAO EM CURSO |
| S06 | 4.1/1 | Termo crescente | RaftState.tick/adoptTerm | RaftCheck; a-e | IMPLEMENTADO; VALIDACAO EM CURSO |
| S07 | 4.1/1 | Prazo aleatório local monotónico | RaftState.resetTimer | b | IMPLEMENTADO; VALIDACAO EM CURSO |
| S08 | 4.1/1 | Nova eleição após expirar sem maioria | RaftState.tick | e | IMPLEMENTADO; VALIDACAO EM CURSO |
| S09 | 4.1/1 | Voto em si e contagem distinta | RaftState.tick/onVote | ProtocolTest | IMPLEMENTADO; VALIDACAO EM CURSO |
| S10 | 4.1/1 | Maioria estrita | RaftState.onVote | ProtocolTest; RaftCheck | IMPLEMENTADO; VALIDACAO EM CURSO |
| S11 | 4.1/1 | Um candidato por eleitor/termo | RaftState.onVoteRequest | ProtocolTest | IMPLEMENTADO; VALIDACAO EM CURSO |
| S12 | 4.1/1 | Restrição último termo, depois seq | RaftState.onVoteRequest | ProtocolTest | IMPLEMENTADO; VALIDACAO EM CURSO |
| S13 | 4.1/1 | Adotar termo superior e demitir | RaftState.adoptTerm | ProtocolTest; a,d | IMPLEMENTADO; VALIDACAO EM CURSO |
| S14 | 4.1/1 | Ignorar lote de termo inferior | RaftState.onBatch | ProtocolTest; d | IMPLEMENTADO; VALIDACAO EM CURSO |
| S15 | 4.1/1 | Candidato reconhece lote do termo | RaftState.onBatch | a,b | IMPLEMENTADO; VALIDACAO EM CURSO |
| S16 | 4.1/1 | Renovar prazo no voto e lote válido | RaftState.onVoteRequest/onBatch | a,b | IMPLEMENTADO; VALIDACAO EM CURSO |
| M01 | 4.1/2 | LOTE com termo/líder/últimoTermo | Batch | ProtocolTest; a | IMPLEMENTADO; VALIDACAO EM CURSO |
| M02 | 4.1/2 | Registo de sete campos | SensorRecord | ProtocolTest; a-e | IMPLEMENTADO; VALIDACAO EM CURSO |
| M03 | 4.1/2 | Codificação/descodificação PEDIDO_VOTO | VoteRequest | ProtocolTest | IMPLEMENTADO; VALIDACAO EM CURSO |
| M04 | 4.1/2 | Codificação/descodificação VOTO | VoteReply | ProtocolTest | IMPLEMENTADO; VALIDACAO EM CURSO |
| M05 | 4.1/2 | Constantes 2s, 6-12s, verificação 200ms | Wire | leitura; b | IMPLEMENTADO; VALIDACAO EM CURSO |
| M06 | 4.1/2 | Propriedades de sistema da ficha | Wire | b / testes | IMPLEMENTADO; VALIDACAO EM CURSO |
| M07 | 4.1/2 | Porto distinto por nó e partição | Wire.nodePort | a-e | IMPLEMENTADO; VALIDACAO EM CURSO |
| M08 | 4.1/2 | Marcador isolar-pk-ni | Wire.isolated | d | IMPLEMENTADO; VALIDACAO EM CURSO |
| N01 | 4.1/3 | Ler histórico e metadados locais | RecordFile; PartitionNode construtor | a | IMPLEMENTADO; VALIDACAO EM CURSO |
| N02 | 4.1/3 | Servidor TCP em qualquer nó | PartitionNode.serve/start | a,d | IMPLEMENTADO; VALIDACAO EM CURSO |
| N03 | 4.1/3 | Três subscrições por nó | PartitionNode.start | a-e | IMPLEMENTADO; VALIDACAO EM CURSO |
| N04 | 4.1/3 | Ignorar publicações próprias | PartitionNode.election/batch | a-e | IMPLEMENTADO; VALIDACAO EM CURSO |
| N05 | 4.1/3 | Verificação periódica e pedido de voto | PartitionNode.electionTick | b,e | IMPLEMENTADO; VALIDACAO EM CURSO |
| N06 | 4.1/3 | Responder a voto concedido/recusado | PartitionNode.election | ProtocolTest; b | IMPLEMENTADO; VALIDACAO EM CURSO |
| N07 | 4.1/3 | Eventos temporizados por nó | MonitorLog; RaftState | RaftCheck | IMPLEMENTADO; VALIDACAO EM CURSO |
| L01 | 4.1/4 | Restaurar seq, L e V na promoção | PartitionNode.restoreClocks/election | a,c,d | IMPLEMENTADO; VALIDACAO EM CURSO |
| L02 | 4.1/4 | Reiniciar hb e lista de pendentes | PartitionNode.election | a,c | IMPLEMENTADO; VALIDACAO EM CURSO |
| L03 | 4.1/4 | Primeiro lote imediato | PartitionNode.election/sendBatch | a,b | IMPLEMENTADO; VALIDACAO EM CURSO |
| L04 | 4.1/4 | Validar liderança dentro do monitor | PartitionNode.input | leitura; ensaios | IMPLEMENTADO; VALIDACAO EM CURSO |
| L05 | 4.1/4 | L=max(local,msg)+1 | PartitionNode.input | CausalCheck | IMPLEMENTADO; VALIDACAO EM CURSO |
| L06 | 4.1/4 | Merge V e tick da partição | PartitionNode.input | CausalCheck | IMPLEMENTADO; VALIDACAO EM CURSO |
| L07 | 4.1/4 | V[k]=seq | PartitionNode.input | ProtocolTest; CausalCheck | IMPLEMENTADO; VALIDACAO EM CURSO |
| L08 | 4.1/4 | Persistir antes de pendentes/publicação | PartitionNode.input | a | IMPLEMENTADO; VALIDACAO EM CURSO |
| L09 | 4.1/4 | Fecho consistente sob mesmo monitor | PartitionNode.sendBatch | leitura | IMPLEMENTADO; VALIDACAO EM CURSO |
| L10 | 4.1/4 | HTTP fora do bloqueio | PartitionNode.sendBatch/publish | leitura; ensaios | IMPLEMENTADO; VALIDACAO EM CURSO |
| L11 | 4.1/4 | Cancelar tarefa após demissão | PartitionNode.demotionCheck | a,d | IMPLEMENTADO; VALIDACAO EM CURSO |
| L12 | 4.1/4 | Heartbeat e eleição não alteram L/V | PartitionNode; RaftState | CausalCheck | IMPLEMENTADO; VALIDACAO EM CURSO |
| F01 | 4.1/5 | Filtrar liderança obsoleta antes de aplicar | PartitionNode.batch | d; ProtocolTest | IMPLEMENTADO; VALIDACAO EM CURSO |
| F02 | 4.1/5 | Duplicados por seq e termo | PartitionNode.batch | leitura; a | IMPLEMENTADO; VALIDACAO EM CURSO |
| F03 | 4.1/5 | Aplicar registo seguinte | PartitionNode.batch | a-e | IMPLEMENTADO; VALIDACAO EM CURSO |
| F04 | 4.1/5 | Recuperar lacuna TCP | PartitionNode.request/recover | a,c | IMPLEMENTADO; VALIDACAO EM CURSO |
| F05 | 4.1/5 | Comparar comprimento e último termo | PartitionNode.batch | a,d | IMPLEMENTADO; VALIDACAO EM CURSO |
| F06 | 4.1/5 | Recuar por blocos de termo | PartitionNode.recover | a,d | IMPLEMENTADO; VALIDACAO EM CURSO |
| F07 | 4.1/5 | Localizar primeira divergência | PartitionNode.recover | a | IMPLEMENTADO; VALIDACAO EM CURSO |
| F08 | 4.1/5 | Reescrita atómica sem cauda divergente | RecordFile.replaceFrom | ProtocolTest; a | IMPLEMENTADO; VALIDACAO EM CURSO |
| F09 | 4.1/5 | TRUNCADO com intervalo/quantidade/termos | PartitionNode.recover | a; RaftCheck | IMPLEMENTADO; VALIDACAO EM CURSO |
| F10 | Concorrência | Invalidar resposta TCP por revisão/termo | PartitionNode.recover | leitura; cenários de falha | IMPLEMENTADO; VALIDACAO EM CURSO |
| I01 | 4.1/2 | Bloquear publicação de dados/eleição no isolamento | PartitionNode.publish/sendBatch | d | IMPLEMENTADO; VALIDACAO EM CURSO |
| I02 | 4.1/2 | Bloquear processamento de dados/eleição | PartitionNode.batch/election | d | IMPLEMENTADO; VALIDACAO EM CURSO |
| I03 | 4.1/2 | Bloquear TCP cliente/servidor | PartitionNode.request/serve | d | IMPLEMENTADO; VALIDACAO EM CURSO |
| I04 | 4.1/2 | Continuar receção IN durante isolamento | PartitionNode.input | d | IMPLEMENTADO; VALIDACAO EM CURSO |
| A01 | 4.1/6 | Processador subscreve todas as partições | AlarmProcessor.start | a,c,d | IMPLEMENTADO; VALIDACAO EM CURSO |
| A02 | 4.1/6 | Processador rejeita termos inferiores | AlarmProcessor.receive; HeartbeatMonitor | ProtocolTest; d | IMPLEMENTADO; VALIDACAO EM CURSO |
| A03 | 4.1/6 | Deduplicação p/seq/termo | AlarmProcessor.process | leitura; c,d | IMPLEMENTADO; VALIDACAO EM CURSO |
| A04 | 4.1/6 | Preservar carimbos e alarmes | AlarmProcessor.process | CausalCheck; c | IMPLEMENTADO; VALIDACAO EM CURSO |
| A05 | 4.1/6 | Aviso no destino suspeito e envio | AlarmProcessor.process | leitura; c,d | IMPLEMENTADO; VALIDACAO EM CURSO |
| P01 | 4.1/7 | Produtor interpreta cabeçalho e monitora | Producer.main | a-e | IMPLEMENTADO; VALIDACAO EM CURSO |
| P02 | 4.1/7 | Uma linha por IN em produtor.txt | Producer.sent | a,c,d,e | IMPLEMENTADO; VALIDACAO EM CURSO |
| P03 | 4.1/7 | Aviso e envio mesmo suspeito | Producer.warn | c,e | IMPLEMENTADO; VALIDACAO EM CURSO |
| C01 | 4.1/8 | Sequência de termos/candidatos/votos/líderes | RaftCheck.analyse | b | IMPLEMENTADO; VALIDACAO EM CURSO |
| C02 | 4.1/8 | Votos divididos e duração eleições | RaftCheck.analyse | b | IMPLEMENTADO; VALIDACAO EM CURSO |
| C03 | 4.1/8 | Segurança de eleição e termo monotónico | RaftCheck.analyse | a-e; FaultTest | IMPLEMENTADO; VALIDACAO EM CURSO |
| C04 | 4.1/8 | Tempo até primeiro líder | RaftCheck.analyse | b | IMPLEMENTADO; VALIDACAO EM CURSO |
| C05 | 4.1/8 | Indisponibilidade com falhas anotadas | RaftCheck.analyse | a,c,e | IMPLEMENTADO; VALIDACAO EM CURSO |
| C06 | 4.1/8 | Leituras no intervalo sem líder | RaftCheck.analyse | a,c,e | IMPLEMENTADO; VALIDACAO EM CURSO |
| C07 | 4.1/8 | Perdas no histórico final | RaftCheck.analyse | a,c | IMPLEMENTADO; VALIDACAO EM CURSO |
| C08 | 4.1/8 | Truncados e reaparecidos com outro seq | RaftCheck.analyse | a,d | IMPLEMENTADO; VALIDACAO EM CURSO |
| C09 | 4.1/8 | Comparação três cópias até mínimo | RaftCheck.analyse | a-e | IMPLEMENTADO; VALIDACAO EM CURSO |
| C10 | CA3 | CausalCheck novo formato | CausalCheck.main | a-e | IMPLEMENTADO; VALIDACAO EM CURSO |
| T01 | 4.2/9a | Figura 1 integral | Experiments.figure | a | IMPLEMENTADO; VALIDACAO EM CURSO |
| T02 | 4.2/9b | Dez arranques aleatórios | Experiments.main | b | IMPLEMENTADO; VALIDACAO EM CURSO |
| T03 | 4.2/9b | Cinco arranques com prazo fixo | Experiments.main | b | IMPLEMENTADO; VALIDACAO EM CURSO |
| T04 | 4.2/9c | Três falhas durante rajada e reinício 30s | Experiments.failure | c | IMPLEMENTADO; VALIDACAO EM CURSO |
| T05 | 4.2/9d | Isolar líder 30s e reparar | Experiments.partition | d | IMPLEMENTADO; VALIDACAO EM CURSO |
| T06 | 4.2/9d | Isolar seguidor 60s e reparar | Experiments.partition | d | IMPLEMENTADO; VALIDACAO EM CURSO |
| T07 | 4.2/9e | Dois nós parados, 30s de leituras, regresso | Experiments.quorum | e | IMPLEMENTADO; VALIDACAO EM CURSO |
| X01 | 4.2/10 | Mutação maioria incorreta | Experiments.mutate; FaultTest | faults | IMPLEMENTADO; VALIDACAO EM CURSO |
| X02 | 4.2/10 | Mutação termo/voto voláteis e reinício | Experiments.mutate; FaultTest | faults | IMPLEMENTADO; VALIDACAO EM CURSO |
| X03 | 4.2/10 | Mutação sem upToDate, candidato atrasado | Experiments.outdatedCandidate | faults | IMPLEMENTADO; VALIDACAO EM CURSO |
| X04 | 4.2/10 | Mutação aceita lotes obsoletos | Experiments.partition | faults | IMPLEMENTADO; VALIDACAO EM CURSO |
| X05 | 4.2/10 | Mutação relógios zero na promoção | Experiments.figure | faults | IMPLEMENTADO; VALIDACAO EM CURSO |
| D01 | 4.3/11 | Tabela métricas medidas | Relatorio.md | agregação | POR CONCLUIR |
| D02 | 4.3/11 | Tabela CAP por cenário | Relatorio.md | a-e | POR CONCLUIR |
| D03 | 4.3/12 | Reflexão e respostas CA1-CA4 | Relatorio.md; README.md | revisão | POR CONCLUIR |
| O01 | CA1-CA4 | Referências reais para defesa oral | README.md | revisão | POR CONCLUIR |
| B01 | Âmbito | Sem commit, Pre-Vote, leases, snapshots | fontes Java | revisão | IMPLEMENTADO; VALIDACAO EM CURSO |
| B02 | Proteção | COMINDIR01-04 e quatro txt intactos | hashes e git diff | verificação final | IMPLEMENTADO; VALIDACAO EM CURSO |
