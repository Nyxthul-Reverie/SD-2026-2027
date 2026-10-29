# COMINDIR03 - Testes da secção 4.2

**Data:** 29/10/2026  
**Versão testada:** branch tarefa/COMINDIR03, commit b3c1de5  
**Ambiente:** Windows, Java 25.0.2, Kubo 0.39.0, N=3. PubSub ativo.

## 7. Ensaios

### a) Figura 1

Com o processador ligado, foram introduzidas as leituras S02 29.3, S01 21.0 e S04 22.5.

| Registo        | Parte/seq |   L | V       |
| -------------- | --------: | --: | ------- |
| S02 29.3       |      p1#1 |   1 | (0,1,0) |
| S01 21.0       |      p0#1 |   1 | (1,0,0) |
| ALARM-S02 29.3 |      p2#1 |   2 | (0,1,1) |
| S04 22.5       |      p0#2 |   2 | (2,0,0) |

Os carimbos coincidiram com os da figura. O CausalCheck leu quatro registos, contou um par causal e quatro pares concorrentes entre partições. Não encontrou violações de V[k]=seq nem da condição de Lamport.

### b) Rajada com o processador parado

Foi executado rajada 300 10. Os 300 registos ficaram distribuídos por p0/p1/p2: 139/74/87.

O CausalCheck contou zero pares causais e 28 817 pares concorrentes entre partições. Não encontrou violações de V[k]=seq nem de Lamport.

### c) Três rajadas com o processador ligado

Cada execução usou ficheiros novos e o comando rajada 300 10. As temperaturas são aleatórias, por isso os resultados variam.

| Execução | Alarmes | Registos p0/p1/p2 |  Total | Pares causais | Pares concorrentes | Concorrentes com L distinto |
| -------- | ------: | ----------------: | -----: | ------------: | -----------------: | --------------------------: |
| 1        |      31 |        135/107/89 |    331 |        27 914 |              8 069 |              7 791 (96,55%) |
| 2        |      38 |        144/104/90 |    338 |        32 554 |              4 742 |              4 485 (94,58%) |
| 3        |      43 |       126/100/117 |    343 |        33 566 |              5 476 |              5 203 (95,01%) |
| Média    |   37,33 |  135/103,67/98,67 | 337,33 |     31 344,67 |           6 095,67 |           5 826,33 (95,58%) |

Nas três execuções, o CausalCheck não encontrou violações de V[k]=seq nem da condição de Lamport.

### d) Reinício do líder p2

Antes do reinício, p2 tinha S03#1, L=1, V=(0,0,1). Depois de arrancar novamente, registou S03#2, L=2, V=(0,0,2). O líder retomou o estado a partir do ficheiro. O CausalCheck leu os dois registos sem assinalar violações.

### e) Ordem de escrita na réplica 1

A réplica 1 guarda p0 e p1. Para observar a ordem, simulou-se a perda de p0#1. O alarme ALARM-S01 foi aplicado em p1 enquanto os registos seguintes de p0 ficavam pendentes. Quando p0 acumulou 11 registos pendentes, foi iniciada a recuperação TCP. Nos logs, a ordem foi:

1. p0#1 descartado;
2. ALARM-S01 aplicado em p1;
3. p0#1 recuperado e p0#2–p0#12 aplicados.

O CausalCheck leu 13 registos: um par causal e 11 concorrentes, sem violações de V[k]=seq ou Lamport.

O timestamp de S01 e de ALARM-S01 foi igual (2026-10-29T09:57:43). Esse timestamp é atribuído pelo líder e tem precisão de segundos; não regista a hora em que a réplica escreveu a linha. A ordem foi confirmada pelos logs de aplicação e recuperação.

## 8. Falhas deliberadas

As falhas foram introduzidas uma de cada vez em cópias do código.

| Falha                                           | Resultado                                                                                                                                            | Detetada pelo CausalCheck?                             |
| ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------ |
| O líder incrementa V[k] sem juntar V_msg        | O vetor do alarme perdeu a dependência de p0. O par entre S01 e ALARM-S01 passou a aparecer como concorrente.                                        | Não. Contou zero pares causais e um concorrente.       |
| O líder atualiza L sem considerar L_msg         | S01 e o alarme dependente ficaram ambos com L=1.                                                                                                     | Sim. Assinalou uma violação de Lamport em p0#1 → p1#1. |
| O líder reinicia L e V a zero                   | Ao receber seq=2, tentou gravar V[2]=1 e terminou a thread com V[2]=1 diferente de seq=2. Só ficou o primeiro registo.                               | Não. Leu apenas o registo que já estava no ficheiro.   |
| O processador usa o vetor partilhado sem copy() | Foi introduzida uma pausa de 500 ms para outra thread alterar o vetor. O alarme ficou com V=(1,1,2), incluindo p2#2, mas manteve o Lamport anterior. | Sim. Assinalou uma violação em p2#2 → p1#1.            |
| O produtor usa N=4 e os restantes processos N=3 | O líder p0 rejeitou a mensagem porque o vetor tinha quatro componentes. Não foi gravado nenhum registo.                                              | Não. Sem registos, não houve violações a assinalar.    |
