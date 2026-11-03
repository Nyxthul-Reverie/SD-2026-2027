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

Os logs completos e as saídas dos dois verificadores ficam apenas no PC, nas subpastas de `resultados/`, ignoradas pelo Git. O repositório guarda o código dos testes e este relatório com as medições; os ficheiros gerados podem ser reproduzidos localmente. Os testes dirigidos usam dados sintéticos identificados separadamente. O código Java usado nos ensaios corresponde a d70596b; os commits seguintes acrescentam configurações, automatização, evidência e documentação.

## 8. Ensaios normais

| Ensaio | Observação |
|---|---|
| 8(a) (`resultados/8a-falha-ultimo/HeartbeatCheck.txt`) | O registo 3 existia no líder mas não nas réplicas 1/3 antes da falha. O reinício anunciou ultimoSeq=3 num lote vazio. Ambas recuperaram 3 por TCP. Deteção entre 15,186 e 15,686 s. |
| 8(b), rajada (`resultados/8b-rajada-5000/HeartbeatCheck.txt`) | 300 leituras originaram 325 registos incluindo 25 alarmes. Publicaram-se 15 lotes, nove vazios. As seis cópias das partições nas réplicas coincidiram com os líderes. |
| 8(b), parado (`resultados/8b-parado-5000/HeartbeatCheck.txt`) | Todos os lotes eram vazios. Na janela de um minuto observaram-se aproximadamente 12 lotes por partição. O ficheiro completo inclui também o arranque. |
| 8(c), 1 (`resultados/8c-falha-5000-1/HeartbeatCheck.txt`), 2 (`resultados/8c-falha-5000-2/HeartbeatCheck.txt`), 3 (`resultados/8c-falha-5000-3/HeartbeatCheck.txt`) | p1 foi terminado durante a rajada e reiniciado 30 s depois. Os quatro participantes de p1 detetaram cada falha. As réplicas recuperaram os dados persistidos, sem recuperar as entradas nunca aceites. |
| 8(d) (`resultados/8d-suspensao/HeartbeatCheck.txt`) | Suspensão real do processo p2 por 20 s, com NtSuspendProcess/NtResumeProcess no Windows. Quatro falsas suspeitas; retoma na mesma incarnação. As duas leituras durante a pausa chegaram depois. |
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

## 9. Falhas deliberadas

Cada alteração foi compilada numa cópia em ensaios/mutacao-*, sem substituir o código correto. A cópia exata do líder alterado está junto aos logs de cada ensaio. O fator 1 foi aplicado apenas aos participantes por propriedade de arranque.

| Falha introduzida | Lado onde surgiu | Deteção observada | Conclusão |
|---|---|---|---|
| Publicar só lotes não vazios (`resultados/9-sem-vazios/HeartbeatCheck.txt`) | Participantes, após os líderes ficarem sem leituras | HeartbeatCheck registou 12 suspeitas, quatro por partição; CausalCheck não encontrou violações | Líderes vivos podem ficar suspeitos sem heartbeats vazios. Pela regra operacional da ficha, o checker classificou estas suspeitas como reais porque não houve retoma na janela; os processos estavam vivos. O log sozinho não prova morte. |
| [Incrementar L e V[k] por heartbeat](resultados/9-relogios/lider0-console.txt) | Líder ao aceitar o IN seguinte | A verificação do líder rejeitou o registo: V[0]=3 diferente de seq=1. Os verificadores não encontraram violações em ficheiros, porque o registo inválido não foi escrito | Alterar relógios sem criar registo quebra a correspondência V[k]=seq. Uma verificação na escrita pode impedir que CausalCheck venha a observar a violação. |
| Ler ultimoSeq fora do lock (`resultados/9-fora-lock/HeartbeatCheck.txt`) | Cabeçalhos publicados e participantes | HeartbeatCheck registou seis avisos de cabeçalho incoerente e quatro recuperações. CausalCheck manteve zero violações | É possível anunciar dados que ficaram na lista seguinte. Foi introduzida uma pausa de 150 ms na janela fora do lock, só na cópia de teste, para tornar a corrida observável. |
| ultimoSeq=0 e descartar um em dez lotes (`resultados/9-ultimo-zero/HeartbeatCheck.txt`) | Réplicas sem a última leitura de cada partição | HeartbeatCheck contou 12 HB_PERDIDO e zero recuperações. CausalCheck não encontrou violações nos três registos dos líderes. As seis comparações réplica/líder falharam, como esperado | O salto de hb denuncia sinal perdido, mas não fornece o limite de seq. Sem ultimoSeq correto e sem um registo posterior, a perda final permanece. |
| Fator 1 nos participantes (`resultados/9-fator1/HeartbeatCheck.txt`) | Detetores com timeout de 5 s | Nesta execução não houve suspeitas e os ficheiros coincidiram | Aumentar o risco não obriga a que a falha ocorra em toda execução. Não se alterou o resultado para mostrar uma falsa suspeita; a suspensão de 8(d) demonstra esse fenómeno. |

O código de produção mantém o fator 3, o envio de lotes vazios, ultimoSeq dentro da secção sincronizada e relógios inalterados nos heartbeats.

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

## 11. Reflexão crítica

### Lote e latência

Na rajada com Δ=5 s, os mesmos 325 registos precisariam de 325 mensagens REG. Foram publicados 15 LOTE, incluindo os vazios: menos 310 publicações, uma redução de 95,38%. O custo total medido passou de 78,397 bytes por registo no REG equivalente para 62,255 no LOTE, cerca de 20,59% menos. Considerando apenas lotes com dados, o custo foi 61,160 bytes por registo.

A latência média de 2,390 s ficou próxima de Δ/2=2,5 s, mas isso não é uma garantia. Com Δ=1 s foi 0,501 s; com Δ=10 s foi 6,573 s. A fase do temporizador, a duração curta da rajada e os alarmes alteram a distribuição das chegadas.

Um sistema de travagem ou um alarme que exija resposta imediata não deve aguardar até cinco segundos só para agrupar dados. Seria possível publicar dados logo à chegada ou por um limite de tamanho e manter heartbeats independentes. Essa separação seria uma extensão: a ficha exige que o LOTE seja também o heartbeat.

### Escolha do TIMEOUT

Na comparação de períodos, a deteção média foi 3,684 s, 14,690 s e 29,390 s para Δ=1,5,10 s, usando uma falha em cada configuração. Com as três repetições de Δ=5 s, a média foi calculada na tabela anterior. Aumentar o período e o timeout permite tolerar mais silêncio, mas atrasa a suspeita.

A suspensão de p2 por 20 s provocou quatro falsas suspeitas com fator 3 e Δ=5 s. O processo retomou com a mesma incarnação e as leituras chegaram. A exatidão deixou de se verificar durante essa pausa: os participantes suspeitaram de um processo que retomou a execução. A completude diz respeito a uma falha acabar por ser suspeita e não implica deteção imediata.

Com fator 1, a execução sem suspensão não produziu falsas suspeitas. O risco é maior porque o timeout coincide com o período nominal; uma pequena demora pode bastar. Não se pode garantir exatidão num sistema assíncrono sem um limite conhecido para os atrasos.

### ultimoSeq e lacunas

Em 8(a), a réplica tinha seq=2 e não chegou nenhum seq=4 que denunciasse a perda do 3. O líder reiniciado anunciou ultimoSeq=3 num lote vazio, permitindo pedir 3 por TCP. O dado estava no ficheiro, apesar de nunca ter sido publicado.

Na alteração com ultimoSeq=0, o décimo lote de cada partição foi descartado com um registo. Os líderes conservaram esses registos, mas as seis cópias nas réplicas ficaram incompletas. Houve HB_PERDIDO ao chegar o heartbeat seguinte, mas zero RECUPERACAO. CausalCheck continuou sem violações porque os históricos dos líderes eram causalmente válidos.

Basta que um lote vazio anuncie o limite correto para revelar a perda final. A ficha inclui o campo em todos os lotes. A recuperação só obtém registos efetivamente persistidos por um líder que esteja acessível.

### Heartbeats e relógios lógicos

Nesta aplicação, a aceitação de um IN cria um registo e incrementa a componente local V[k]. Um heartbeat apenas anuncia estado e publica registos já criados. Incrementar V[k] sem criar um seq faz os dois valores deixarem de coincidir.

A alteração deliberada produziu V[0]=3 quando o próximo seq era 1. A verificação no líder rejeitou o registo antes da escrita. Na implementação correta, CausalCheck confirmou V[k]=seq e a condição de Lamport em todos os ensaios normais.

Os relógios lógicos representam relações entre eventos, não segundos. A emissão dos lotes não altera as regras causais, mas a ordem em que os registos chegam ao processador pode mudar os valores concretos entre execuções.

### Detetar não é recuperar

Nas três repetições de 8(c), o produtor publicou para p1 81, 78 e 90 leituras normais. Os líderes aceitaram apenas 8, 4 e 11, respetivamente: perderam-se 73, 74 e 79 entradas nessa partição. Estas contagens excluem os alarmes. As réplicas recuperaram o que existia no ficheiro, mas não puderam recuperar leituras que nunca receberam seq.

Para outro processo assumir a liderança seria necessário acordar quem tem essa autoridade, determinar o histórico válido e impedir que o líder antigo e o novo atribuíssem sequências ao mesmo tempo. Seria necessário um mecanismo de coordenação, como eleição e consenso, além de acesso ao estado persistido. Esses mecanismos estão expressamente fora do âmbito.

O estado SUSPEITO apenas avisa. Não muda o destino do produtor, não bloqueia a aplicação de dados e não escolhe um substituto.

### Medição e relógios físicos

HeartbeatCheck subtrai instantes produzidos por JVMs diferentes. Os processos correram na mesma máquina e no mesmo fuso, utilizando o mesmo relógio de parede. A precisão em milissegundos foi preservada no SensorRecord. A comparação ficaria contaminada por diferenças de relógio se os processos estivessem em máquinas diferentes.

O detetor não depende dessa comparação: subtrai duas leituras locais de nanoTime na mesma JVM. O valor absoluto de nanoTime não deve ser enviado para outra JVM como se fosse uma data universal.

Mesmo na mesma máquina, a classificação operacional do checker tem limites: se não se observar retoma na mesma incarnação, a ficha manda classificá-la como falha real. A alteração que suprimiu lotes vazios produziu essa classificação apesar de os líderes estarem vivos. É necessário relacionar os logs com o ensaio realizado e não interpretar uma suspeita como prova de morte.
