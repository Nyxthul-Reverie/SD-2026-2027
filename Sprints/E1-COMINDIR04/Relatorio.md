# COMINDIR04: deteção de falhas e envio em lote

Fonte: [ficha do professor, issue 17](https://github.com/cunhaestgv/SD-2026-2027/issues/17). Ensaios executados em 03/11/2026, no Windows, com JDK23 (compilação para Java11) e Kubo 0.43.1. Os quatro programas Java não usam bibliotecas externas.

## Implementação

Foram criados os quatro projetos rep04 a partir da COMINDIR03. HeartbeatMonitor é o código fornecido pelo professor. Batch representa e valida LOTE; os líderes publicam um lote no arranque e periodicamente, incluindo lotes vazios. A escrita dos registos continua imediata. O fecho da lista e a obtenção de ultimoSeq usam o mesmo monitor Java que a aceitação de IN.

As réplicas monitorizam as suas partições e recuperam por TCP quando o último seq aplicado é inferior a ultimoSeq, incluindo lotes vazios. O produtor e o processador monitorizam as três partições; avisam quando o destino é suspeito e continuam a enviar. O processador mantém as regras causais anteriores.

HeartbeatCheck lê os ficheiros depois de cada ensaio. As latências usam a primeira receção de cada registo por participante, agregadas por partição. Não incluem registos obtidos por TCP nem o tempo anterior à aceitação de IN pelo líder. Os eventos do monitor usam nanoTime para durações locais; os ficheiros usam currentTimeMillis para comparar processos desta máquina.

Não foram implementados eleição, consenso, failover ou reenvio das entradas do produtor.

## Condições dos ensaios

- N=3, réplicas 1:(p0,p1), 2:(p1,p2), 3:(p0,p2); recuperação TCP em 7000,7001,7002.
- Arranque: réplicas, líderes, processador e produtor. Fator=3, verificação=1000 ms, salvo a alteração deliberada do fator.
- Kubo separado na API5003, sem bootstrap/MDNS e com swarm apenas em loopback. Um Kubo serve os programas locais. Uma tentativa inicial na rede pública recebeu dados de outros nós com os mesmos tópicos e foi excluída.
- Em `rajada 300 10`, o 10 mantém o significado de dez sensores da base. Acrescentou-se a propriedade `rep04.intervalo`, por omissão 10 ms, para explicitar a pausa mencionada em CA3. É uma pausa entre chamadas; o intervalo real inclui HTTP e escalonamento.
- SensorRecord preserva milissegundos no mesmo campo ISO de instante. A base truncava ao segundo, o que prejudicava a medição da latência.
- As rajadas têm temperaturas e sensores aleatórios: o número de alarmes varia. As comparações são observações destes ensaios, não médias de uma experiência estatística extensa.
- Depois da rajada, aguardaram-se dois períodos mais dois segundos para drenar leituras e alarmes. O ensaio parado usa uma janela adicional de 60 segundos sem enviar leituras.
- Os bytes contam a mensagem UTF-8, sem HTTP/IPFS. O REG equivalente é calculado para os mesmos registos com endereço 127.0.0.1 e porto 7000+k; não corresponde a uma execução nova da COMINDIR03.
- Um resultado PASS na automatização significa que as verificações previstas para aquele cenário passaram. Num cenário de falha deliberada, significa que o efeito errado foi observado; não que a versão alterada esteja correta.

Os logs completos e os dois verificadores estão nas subpastas de [resultados](resultados). Os testes dirigidos usam dados sintéticos identificados separadamente. O código Java usado nos ensaios corresponde a d70596b; os commits seguintes acrescentam configurações, automatização, evidência e documentação.

## 8. Ensaios normais

| Ensaio | Observação |
|---|---|
| [8(a)](resultados/8a-falha-ultimo/HeartbeatCheck.txt) | O registo 3 existia no líder mas não nas réplicas 1/3 antes da falha. O reinício anunciou ultimoSeq=3 num lote vazio. Ambas recuperaram 3 por TCP. Deteção entre 15,186 e 15,686 s. |
| [8(b), rajada](resultados/8b-rajada-5000/HeartbeatCheck.txt) | 300 leituras originaram 325 registos incluindo 25 alarmes. Publicaram-se 15 lotes, nove vazios. As seis cópias das partições nas réplicas coincidiram com os líderes. |
| [8(b), parado](resultados/8b-parado-5000/HeartbeatCheck.txt) | Todos os lotes eram vazios. Na janela de um minuto observaram-se aproximadamente 12 lotes por partição. O ficheiro completo inclui também o arranque. |
| 8(c), [1](resultados/8c-falha-5000-1/HeartbeatCheck.txt), [2](resultados/8c-falha-5000-2/HeartbeatCheck.txt), [3](resultados/8c-falha-5000-3/HeartbeatCheck.txt) | p1 foi terminado durante a rajada e reiniciado 30 s depois. Os quatro participantes de p1 detetaram cada falha. As réplicas recuperaram os dados persistidos, sem recuperar as entradas nunca aceites. |
| [8(d)](resultados/8d-suspensao/HeartbeatCheck.txt) | Suspensão real do processo p2 por 20 s, com NtSuspendProcess/NtResumeProcess no Windows. Quatro falsas suspeitas; retoma na mesma incarnação. As duas leituras durante a pausa chegaram depois. |
| 8(e) | Repetidos rajada, minuto parado e falha de p1 para 1, 5 e 10 s. Para 5 s reutilizam-se 8(b) e a primeira repetição de 8(c). Valores abaixo. |

Nos ensaios normais, CausalCheck apresentou zero violações de V[k]=seq e zero violações da condição de Lamport. A automatização também comparou os ficheiros das réplicas com os líderes; esta comparação verifica algo que CausalCheck, sozinho, não verifica.

No ensaio 8(d), o intervalo máximo entre publicações de p2 foi 25005 ms. Após retomar, observaram-se intervalos de 13, 14, 15 e 11 ms entre heartbeats atrasados. Não ocorreu reinício: inc manteve-se. O instante dos registos é atribuído ao aceitar IN, depois da pausa; a latência de lote não mede os 20 s de espera anterior em buffers.

### 8(c): deteção por participante

| Repetição | Processador (s) | Produtor (s) | Réplica 1 (s) | Réplica 2 (s) |
|---|---:|---:|---:|---:|
| 1 | 14.811 | 14.291 | 14.829 | 14.829 |
| 2 | 14.865 | 14.476 | 14.868 | 14.868 |
| 3 | 14.828 | 14.331 | 14.830 | 14.830 |

Todos estes valores cabem no intervalo ideal ]10,16] s. As diferenças entre participantes resultam das suas receções e verificações locais.

## 10. Medições

A primeira tabela usa a rajada 8(b) com Δ=5000 ms para carga/bytes/latência, o minuto parado de 8(b) para a comparação sem carga e as 12 observações das três repetições de 8(c) para deteção. Não se misturam as janelas de carga e de falha numa média sem significado.

| Métrica | Valor medido |
|---|---:|
| Lotes publicados p0 / p1 / p2 na rajada | 5 / 5 / 5 |
| Registos por lote, média / máximo | 21.667 / 100 |
| Lotes vazios, rajada / parado | 60.00% / 100% |
| Intervalo entre heartbeats, média / máximo (ms) | 4998.083 / 5010 |
| Latência de lote, média / máximo (ms) | 2390.072 / 5000 |
| Deteção, mínimo / média / máximo (s) | 14.291 / 14.721 / 14.868 |
| Falsas suspeitas em 8(b/c) | 0 |
| Mensagens de dados, REG equivalente / LOTE | 325 / 15 |
| Bytes por registo, REG equivalente / LOTE incluindo vazios | 78.397 / 62.255 |
| Bytes por registo, só lotes com dados | 61.160 |

A contagem de mensagens inclui os alarmes como registos, não apenas as 300 leituras originais. Os lotes vazios custam bytes e foram incluídos no custo total.

| Δ (ms) | Lotes/minuto/partição, janela parada | Vazios na rajada / parado | Latência média da rajada (ms) | Deteção em 8(c), média [min; max] (s) | Falsas suspeitas em b/c |
|---|---:|---:|---:|---:|---:|
| 1000 | 60.00 | 57.89% / 100% | 501.190 | 3.684 [3.374; 3.896] | 0 |
| 5000 | 12.00 | 60.00% / 100% | 2390.072 | 14.690 [14.291; 14.829] | 0 |
| 10000 | 6.00 | 66.67% / 100% | 6573.150 | 29.390 [28.939; 29.618] | 0 |

Os intervalos ideais para deteção são ]2,4], ]10,16] e ]20,31] s, respetivamente. A média de 6,57 s com Δ=10 s não contradiz Δ/2: a rajada é curta e as chegadas não se distribuem uniformemente por todo o período.

Para reproduzir, compilar com `./compilar.ps1` e executar `python testes/ensaios.py all --saida ensaios/nova-execucao`. A automatização inicia o Kubo isolado se necessário e termina apenas os processos que criou. Para agregar os dados: `python testes/resumir.py`. A suspensão usa a API do Windows e não é simulada por reinício.
