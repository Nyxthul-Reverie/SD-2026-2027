# Evidências selecionadas — COMINDIR05

Resultados reais. Prefixo dos caminhos abaixo: `execucoes/`. Diretórios completos e configurações privadas do Kubo não são enviados para o GitHub. Uma primeira passagem inválida de c está excluída; a seleção é explícita.

Build final: raft04-no, raft04-produtor e raft04-processador compilados com
--release 11 -encoding UTF-8. Bateria dirigida final:

```text
RecoveryTest PASS: recuo 5->3->1, preservacao do prefixo e resposta TCP obsoleta rejeitada
ProtocolTest PASS: maioria, votos persistentes, upToDate, demissao, formatos, vazio, truncagem e monitor
```

Os 143 ficheiros protegidos da COMINDIR01–04 e os quatro txt incluídos nessa
contagem mantiveram os hashes SHA-256 iniciais. As sete configurações JSON e
os quatro scripts PowerShell foram parseados sem erros.

## a-figura

Pasta: `suite-1793902448077/a-figura`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=8255
PRIMEIRO_LIDER;p1;tempo_ms=7046
PRIMEIRO_LIDER;p2;tempo_ms=6259
FALHA;p1;no=n1;instante=1793902461942;indisponibilidade_ms=8672;enviadas_intervalo=1;perdidas_intervalo=1
RESUMO;termos=4;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=5;perdidas=2;truncados=1;reaparecidos_outro_seq=0;eventos_monitor=72
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=1
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=3;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-aleatorio-0

Pasta: `suite-1793902523735/b-aleatorio-0`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=8272
PRIMEIRO_LIDER;p1;tempo_ms=8465
PRIMEIRO_LIDER;p2;tempo_ms=7052
RESUMO;termos=3;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-aleatorio-1

Pasta: `suite-1793902523735/b-aleatorio-1`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=9428
PRIMEIRO_LIDER;p1;tempo_ms=6409
PRIMEIRO_LIDER;p2;tempo_ms=7191
RESUMO;termos=3;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-aleatorio-2

Pasta: `suite-1793902523735/b-aleatorio-2`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=7454
PRIMEIRO_LIDER;p1;tempo_ms=7618
PRIMEIRO_LIDER;p2;tempo_ms=6866
RESUMO;termos=3;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-aleatorio-3

Pasta: `suite-1793902523735/b-aleatorio-3`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=8326
PRIMEIRO_LIDER;p1;tempo_ms=6828
PRIMEIRO_LIDER;p2;tempo_ms=8246
RESUMO;termos=3;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-aleatorio-4

Pasta: `suite-1793902523735/b-aleatorio-4`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=8797
PRIMEIRO_LIDER;p1;tempo_ms=6704
PRIMEIRO_LIDER;p2;tempo_ms=9026
RESUMO;termos=3;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-aleatorio-5

Pasta: `suite-1793902523735/b-aleatorio-5`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=6615
PRIMEIRO_LIDER;p1;tempo_ms=6488
PRIMEIRO_LIDER;p2;tempo_ms=9723
RESUMO;termos=3;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-aleatorio-6

Pasta: `suite-1793902523735/b-aleatorio-6`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=8061
PRIMEIRO_LIDER;p1;tempo_ms=8092
PRIMEIRO_LIDER;p2;tempo_ms=6762
RESUMO;termos=3;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-aleatorio-7

Pasta: `suite-1793902523735/b-aleatorio-7`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=7370
PRIMEIRO_LIDER;p1;tempo_ms=6820
PRIMEIRO_LIDER;p2;tempo_ms=8116
RESUMO;termos=3;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-aleatorio-8

Pasta: `suite-1793902523735/b-aleatorio-8`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=8549
PRIMEIRO_LIDER;p1;tempo_ms=8378
PRIMEIRO_LIDER;p2;tempo_ms=7451
RESUMO;termos=3;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-aleatorio-9

Pasta: `suite-1793902523735/b-aleatorio-9`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=6198
PRIMEIRO_LIDER;p1;tempo_ms=7424
PRIMEIRO_LIDER;p2;tempo_ms=7646
RESUMO;termos=3;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## c-falha-1

Pasta: `suite-1793902978656/c-falha-1`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=6657
PRIMEIRO_LIDER;p1;tempo_ms=6909
PRIMEIRO_LIDER;p2;tempo_ms=7357
FALHA;p1;no=n2;instante=1793902990355;indisponibilidade_ms=6388;enviadas_intervalo=62;perdidas_intervalo=61
RESUMO;termos=4;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=300;perdidas=62;truncados=1;reaparecidos_outro_seq=0;eventos_monitor=130
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=1
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=260;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## c-falha-2

Pasta: `suite-1793902978656/c-falha-2`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=8221
PRIMEIRO_LIDER;p1;tempo_ms=6820
PRIMEIRO_LIDER;p2;tempo_ms=7195
FALHA;p1;no=n1;instante=1793903040923;indisponibilidade_ms=6263;enviadas_intervalo=64;perdidas_intervalo=64
RESUMO;termos=4;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=300;perdidas=68;truncados=4;reaparecidos_outro_seq=0;eventos_monitor=130
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=4
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=261;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## c-falha-3

Pasta: `suite-1793902978656/c-falha-3`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=7420
PRIMEIRO_LIDER;p1;tempo_ms=7885
PRIMEIRO_LIDER;p2;tempo_ms=9799
FALHA;p1;no=n3;instante=1793903092727;indisponibilidade_ms=5828;enviadas_intervalo=50;perdidas_intervalo=50
RESUMO;termos=4;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=300;perdidas=60;truncados=10;reaparecidos_outro_seq=0;eventos_monitor=126
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=10
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=269;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## mutacao-maioria

Pasta: `suite-1793903081853/mutacao-maioria`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=NA
PRIMEIRO_LIDER;p1;tempo_ms=NA
PRIMEIRO_LIDER;p2;tempo_ms=NA
RESUMO;termos=1;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=1
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck FAIL
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## mutacao-volatil

Pasta: `suite-1793903081853/mutacao-volatil`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=NA
PRIMEIRO_LIDER;p1;tempo_ms=NA
PRIMEIRO_LIDER;p2;tempo_ms=NA
RESUMO;termos=1;votos_divididos=0;termos_multiplos_lideres=1;regressoes_termo=0;estados_invalidos=3;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck FAIL
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## d-isolamentos

Pasta: `suite-1793903165685/d-isolamentos`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=8016
PRIMEIRO_LIDER;p1;tempo_ms=11594
PRIMEIRO_LIDER;p2;tempo_ms=7011
RESUMO;termos=11;votos_divididos=6;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=300;perdidas=88;truncados=102;reaparecidos_outro_seq=0;eventos_monitor=362
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=88
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=222;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## e-dois-nos

Pasta: `suite-1793903337223/e-dois-nos`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=8679
PRIMEIRO_LIDER;p1;tempo_ms=8265
PRIMEIRO_LIDER;p2;tempo_ms=7759
FALHA;p1;no=n1;instante=1793903349178;indisponibilidade_ms=37696;enviadas_intervalo=81;perdidas_intervalo=81
RESUMO;termos=7;votos_divididos=3;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=300;perdidas=81;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=116
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=231;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-fixo-sincronizado-0

Pasta: `suite-1793903796551/b-fixo-sincronizado-0`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=12298
PRIMEIRO_LIDER;p1;tempo_ms=6722
PRIMEIRO_LIDER;p2;tempo_ms=12306
RESUMO;termos=5;votos_divididos=2;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-fixo-sincronizado-1

Pasta: `suite-1793903796551/b-fixo-sincronizado-1`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=12413
PRIMEIRO_LIDER;p1;tempo_ms=6756
PRIMEIRO_LIDER;p2;tempo_ms=6775
RESUMO;termos=4;votos_divididos=1;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-fixo-sincronizado-2

Pasta: `suite-1793903796551/b-fixo-sincronizado-2`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=12369
PRIMEIRO_LIDER;p1;tempo_ms=6559
PRIMEIRO_LIDER;p2;tempo_ms=6668
RESUMO;termos=4;votos_divididos=1;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-fixo-sincronizado-3

Pasta: `suite-1793903796551/b-fixo-sincronizado-3`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=12401
PRIMEIRO_LIDER;p1;tempo_ms=12358
PRIMEIRO_LIDER;p2;tempo_ms=6629
RESUMO;termos=5;votos_divididos=2;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## b-fixo-sincronizado-4

Pasta: `suite-1793903796551/b-fixo-sincronizado-4`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=12391
PRIMEIRO_LIDER;p1;tempo_ms=6740
PRIMEIRO_LIDER;p2;tempo_ms=12375
RESUMO;termos=5;votos_divididos=2;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## mutacao-atualizacao

Pasta: `suite-1793903936651/mutacao-atualizacao`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=6989
PRIMEIRO_LIDER;p1;tempo_ms=6292
PRIMEIRO_LIDER;p2;tempo_ms=7389
FALHA;p1;no=n1;instante=1793903986796;indisponibilidade_ms=22336;enviadas_intervalo=0;perdidas_intervalo=0
RESUMO;termos=5;votos_divididos=1;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=2;perdidas=2;truncados=4;reaparecidos_outro_seq=0;eventos_monitor=92
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=2
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## mutacao-maioria-rede

Pasta: `suite-1793903936651/mutacao-maioria-rede`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=9910
PRIMEIRO_LIDER;p1;tempo_ms=11558
PRIMEIRO_LIDER;p2;tempo_ms=7398
RESUMO;termos=3;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## mutacao-obsoleto

Pasta: `suite-1793903936651/mutacao-obsoleto`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=9200
PRIMEIRO_LIDER;p1;tempo_ms=7582
PRIMEIRO_LIDER;p2;tempo_ms=7176
RESUMO;termos=4;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=300;perdidas=92;truncados=108;reaparecidos_outro_seq=7;eventos_monitor=125
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=92
TERMOS;lotes_obsoletos_aceites=2
RaftCheck FAIL
CAUSAL;registos=222;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## mutacao-volatil-rede

Pasta: `suite-1793903936651/mutacao-volatil-rede`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=6634
PRIMEIRO_LIDER;p1;tempo_ms=6300
PRIMEIRO_LIDER;p2;tempo_ms=8375
RESUMO;termos=3;votos_divididos=0;termos_multiplos_lideres=1;regressoes_termo=2;estados_invalidos=9;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=0;perdidas=0;truncados=0;reaparecidos_outro_seq=0;eventos_monitor=0
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=0
TERMOS;lotes_obsoletos_aceites=0
RaftCheck FAIL
CAUSAL;registos=0;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```

## mutacao-zero

Pasta: `suite-1793903936651/mutacao-zero`.

```text
PRIMEIRO_LIDER;p0;tempo_ms=8695
PRIMEIRO_LIDER;p1;tempo_ms=8994
PRIMEIRO_LIDER;p2;tempo_ms=7452
FALHA;p1;no=n3;instante=1793904087016;indisponibilidade_ms=6348;enviadas_intervalo=1;perdidas_intervalo=1
RESUMO;termos=4;votos_divididos=0;termos_multiplos_lideres=0;regressoes_termo=0;estados_invalidos=0;ficheiros_ausentes=0;seq_divergentes=0;comprimentos_diferentes=0;enviadas=5;perdidas=3;truncados=1;reaparecidos_outro_seq=0;eventos_monitor=70
MAIORIA;eleicoes_invalidas=0
PERDAS;leituras_normais_truncadas_ausentes=1
TERMOS;lotes_obsoletos_aceites=0
RaftCheck PASS
CAUSAL;registos=2;violacoes_locais=0;violacoes_Lamport=0
CausalCheck PASS
```
