# COMINDIR04: deteção de falhas e envio em lote

Este trabalho segue a [ficha do professor, issue 17](https://github.com/cunhaestgv/SD-2026-2027/issues/17). Os testes foram realizados em 03/11/2026, no Windows, com JDK23 a compilar para Java11 e Kubo 0.43.1. Os quatro programas Java não usam bibliotecas externas.

## Implementação

Os quatro projetos rep04 partiram do código da COMINDIR03. A principal mudança está no envio: o líder continua a guardar cada leitura imediatamente no ficheiro, mas junta os registos numa lista antes de os publicar. Envia essa lista numa mensagem LOTE no arranque e depois em intervalos regulares. Mesmo quando a lista está vazia, envia o lote para mostrar que continua ativo. Esse sinal de vida chama-se heartbeat.

A classe HeartbeatMonitor foi fornecida pelo professor. A classe Batch trata do formato do lote e verifica se a mensagem recebida é válida. No líder, a troca da lista de registos pendentes e a leitura de ultimoSeq ficam protegidas pelo mesmo bloqueio usado para aceitar uma entrada IN. Assim, não se mistura a lista de um momento com o número de sequência de outro.

As réplicas acompanham as partições que lhes foram atribuídas. Se o seu último seq for menor do que o ultimoSeq anunciado pelo líder, pedem os registos em falta por TCP. Isto também funciona quando recebem um lote vazio. O produtor e o processador acompanham as três partições e mostram um aviso quando suspeitam de um líder, mas continuam a enviar. O processador mantém as regras de ordem causal da COMINDIR03.

Depois de cada teste, HeartbeatCheck analisa os ficheiros. Para medir a latência de um lote, usa a primeira receção de cada registo por cada participante e junta os resultados por partição. Esta medição não inclui registos recuperados por TCP nem a espera anterior à aceitação de IN pelo líder.

O monitor mede o tempo decorrido com nanoTime, dentro do próprio processo. Os ficheiros usam currentTimeMillis para permitir comparar os instantes registados pelos vários processos na mesma máquina.

O trabalho não inclui a escolha de outro líder, consenso, substituição automática do líder em falha (failover) ou reenvio das entradas do produtor.

## Preparação dos testes

- Existem três partições, N=3. A réplica 1 acompanha p0 e p1; a réplica 2 acompanha p1 e p2; a réplica 3 acompanha p0 e p2. A recuperação por TCP usa as portas 7000, 7001 e 7002.
- Os programas arrancam pela seguinte ordem: réplicas, líderes, processador e produtor. O fator do timeout é 3 e o estado dos líderes é verificado a cada 1000 ms, exceto no teste que altera esse fator.
- Os testes usam um Kubo separado, com a API na porta 5003. O bootstrap e o MDNS estão desligados e as ligações entre nós (swarm) estão limitadas à própria máquina, em loopback. Um único Kubo serve todos os programas locais. Uma primeira tentativa na rede pública recebeu mensagens de outros nós com os mesmos tópicos e ficou de fora dos resultados.
- No comando `rajada 300 10`, o 10 continua a representar dez sensores, como no código anterior. A pausa entre envios é definida pela propriedade `rep04.intervalo`, com valor inicial de 10 ms, para cumprir a pausa pedida em CA3. O intervalo real é um pouco maior, porque também inclui a chamada HTTP e o tempo até o sistema voltar a executar a thread.
- SensorRecord passou a guardar os milissegundos no campo de data e hora, mantendo o formato ISO. Antes, guardava apenas até ao segundo, o que era pouco preciso para medir estes atrasos.
- As temperaturas e os sensores das rajadas são aleatórios, por isso o número de alarmes muda entre testes. Os valores apresentados correspondem às execuções realizadas e podem variar numa nova execução.
- Depois de cada rajada, o teste espera dois períodos mais dois segundos para dar tempo à entrega das leituras e dos alarmes. No teste sem leituras, observa-se depois mais um minuto de funcionamento.
- A contagem de bytes inclui apenas o texto da mensagem em UTF-8. Não inclui os dados adicionais de HTTP ou IPFS. O custo de REG é calculado para os mesmos registos, usando o endereço 127.0.0.1 e a porta 7000+k. Não foi feita uma nova execução da COMINDIR03 para esta comparação.
- PASS significa que o teste obteve o resultado esperado. Nos testes em que se introduz um erro de propósito, PASS significa que esse erro foi observado.

Os ficheiros completos dos testes e as saídas dos dois verificadores ficam apenas no PC, em `resultados/`. Esta pasta é ignorada pelo Git. O repositório contém os programas, os scripts dos testes e este relatório com os resultados.

Também existem testes com dados preparados para verificar casos específicos, separados dos testes com os programas em execução. O código Java usado nos ensaios corresponde ao commit d70596b. Os commits seguintes acrescentam configurações, scripts de teste, resultados e documentação; os ficheiros gerados foram depois retirados da versão atual do repositório para ficarem apenas locais.

## 8. Testes de funcionamento

| Teste | O que aconteceu |
|---|---|
| 8(a) (`resultados/8a-falha-ultimo/HeartbeatCheck.txt`) | Antes da falha, o registo 3 estava no ficheiro do líder, mas faltava nas réplicas 1 e 3. Ao reiniciar, o líder enviou um lote vazio com ultimoSeq=3. As duas réplicas recuperaram o registo por TCP. A falha foi detetada entre 15,186 e 15,686 s. |
| 8(b), rajada (`resultados/8b-rajada-5000/HeartbeatCheck.txt`) | As 300 leituras deram origem a 325 registos, contando com 25 alarmes. Foram publicados 15 lotes, dos quais nove estavam vazios. As seis cópias das partições nas réplicas ficaram iguais às dos líderes. |
| 8(b), sem leituras (`resultados/8b-parado-5000/HeartbeatCheck.txt`) | Todos os lotes estavam vazios. Durante um minuto, cada partição publicou aproximadamente 12 lotes. O ficheiro completo também inclui o arranque dos programas. |
| 8(c), repetições 1 (`resultados/8c-falha-5000-1/HeartbeatCheck.txt`), 2 (`resultados/8c-falha-5000-2/HeartbeatCheck.txt`) e 3 (`resultados/8c-falha-5000-3/HeartbeatCheck.txt`) | O processo do líder de p1 foi terminado durante a rajada e reiniciado 30 s depois. Os quatro participantes que acompanham p1 detetaram a falha nas três repetições. As réplicas recuperaram os registos guardados pelo líder. As entradas que o líder nunca chegou a aceitar perderam-se. |
| 8(d) (`resultados/8d-suspensao/HeartbeatCheck.txt`) | O processo do líder de p2 foi suspenso durante 20 s com NtSuspendProcess/NtResumeProcess, no Windows. Surgiram quatro falsas suspeitas. O processo retomou sem reiniciar e as duas leituras enviadas durante a pausa chegaram depois. |
| 8(e) | A rajada, o minuto sem leituras e a falha de p1 foram testados com períodos de 1, 5 e 10 s. Para o período de 5 s, usam-se os resultados de 8(b) e da primeira repetição de 8(c). Os valores estão nas tabelas seguintes. |

Em todos os testes de funcionamento, CausalCheck encontrou zero violações de V[k]=seq e zero violações da condição de Lamport. Os scripts também compararam os ficheiros das réplicas com os dos líderes para confirmar que tinham os mesmos registos. CausalCheck, por si só, não faz essa comparação.

No teste 8(d), o maior intervalo entre publicações de p2 foi de 25005 ms. Quando o processo retomou, enviou os heartbeats que estavam atrasados, com intervalos de 13, 14, 15 e 11 ms. O campo inc manteve o mesmo valor, confirmando que era a mesma execução do líder. Este campo identifica a execução, também chamada incarnação, e muda quando o líder reinicia.

A data e hora de cada registo só são atribuídas quando o líder aceita IN. Neste teste, isso aconteceu depois da pausa. Por isso, a latência de lote não inclui os 20 s em que as mensagens ficaram à espera nos buffers, ou seja, na memória usada para guardar dados ainda por tratar.

### 8(c): tempo até cada participante detetar a falha

| Repetição | Processador (s) | Produtor (s) | Réplica 1 (s) | Réplica 2 (s) |
|---|---:|---:|---:|---:|
| 1 | 14.811 | 14.291 | 14.829 | 14.829 |
| 2 | 14.865 | 14.476 | 14.868 | 14.868 |
| 3 | 14.828 | 14.331 | 14.830 | 14.830 |

Todos os tempos ficaram no intervalo teórico ]10,16] s, isto é, acima de 10 s e até 16 s. Os valores diferem porque cada participante recebe as mensagens e verifica o timeout em momentos ligeiramente diferentes.

## 9. Testes com erros introduzidos de propósito

Cada alteração foi compilada numa cópia em `ensaios/mutacao-*`. O código principal manteve-se correto. As cópias dos líderes alterados ficaram guardadas localmente junto dos ficheiros de cada teste. No caso do fator 1, bastou mudar a propriedade de arranque dos participantes.

| Alteração | Onde se notou | Resultado | O que mostra |
|---|---|---|---|
| Enviar apenas lotes com dados (`resultados/9-sem-vazios/HeartbeatCheck.txt`) | Nos participantes, quando deixaram de chegar leituras aos líderes | HeartbeatCheck registou 12 suspeitas, quatro por partição. CausalCheck não encontrou violações. | Sem lotes vazios, um líder sem dados novos deixa de dar sinal de vida. O verificador classificou as suspeitas como falhas reais porque não viu uma retoma durante o teste, seguindo a regra da ficha. Os processos continuavam vivos. |
| Incrementar L e V[k] em cada heartbeat (`resultados/9-relogios/lider0-console.txt`) | No líder, ao aceitar a entrada IN seguinte | O líder rejeitou o registo porque V[0]=3 era diferente de seq=1. Os verificadores não encontraram violações nos ficheiros, pois o registo inválido não chegou a ser escrito. | Se V[k] aumenta sem existir um novo registo, deixa de acompanhar seq. Neste caso, a verificação feita antes da escrita impediu que o erro chegasse ao ficheiro. |
| Ler ultimoSeq fora do bloqueio (`resultados/9-fora-lock/HeartbeatCheck.txt`) | Nos cabeçalhos dos lotes e nos participantes | HeartbeatCheck registou seis avisos de cabeçalho incoerente e quatro recuperações. CausalCheck não encontrou violações. | O líder pode anunciar um ultimoSeq que já inclui registos da lista seguinte. Na cópia de teste, foi acrescentada uma pausa de 150 ms fora do bloqueio para facilitar a ocorrência deste problema. |
| Enviar ultimoSeq=0 e descartar um em cada dez lotes (`resultados/9-ultimo-zero/HeartbeatCheck.txt`) | Nas réplicas, às quais faltou a última leitura de cada partição | HeartbeatCheck contou 12 HB_PERDIDO e zero recuperações. CausalCheck não encontrou violações nos três registos dos líderes. As seis comparações entre réplicas e líderes falharam, como se esperava. | O salto no número hb permite perceber que faltou um heartbeat, mas não indica até que seq existem registos. Sem ultimoSeq correto nem um registo posterior, as réplicas não recuperaram o último registo perdido. |
| Usar fator 1 nos participantes (`resultados/9-fator1/HeartbeatCheck.txt`) | Nos detetores, com timeout de 5 s | Não houve suspeitas nesta execução e os ficheiros ficaram iguais. | Um timeout mais curto aumenta o risco de falsas suspeitas, mas não significa que vão acontecer em todos os testes. As falsas suspeitas foram observadas no teste de suspensão 8(d). |

Na versão final, o fator continua a ser 3, os líderes enviam lotes vazios, ultimoSeq é lido dentro do bloqueio e os heartbeats não alteram os relógios lógicos.

## 10. Medições

Na tabela seguinte, os valores de lotes, bytes e latência vêm da rajada de 8(b), com um período de 5000 ms. A percentagem sem leituras vem do minuto parado desse teste. Os tempos de deteção juntam as 12 observações das três repetições de 8(c): quatro participantes por repetição.

| Métrica | Valor medido |
|---|---:|
| Lotes publicados p0 / p1 / p2 na rajada | 5 / 5 / 5 |
| Registos por lote, média / máximo | 21.667 / 100 |
| Lotes vazios, rajada / sem leituras | 60.00% / 100% |
| Intervalo entre heartbeats, média / máximo (ms) | 4998.083 / 5010 |
| Latência de lote, média / máximo (ms) | 2390.072 / 5000 |
| Deteção, mínimo / média / máximo (s) | 14.291 / 14.721 / 14.868 |
| Falsas suspeitas em 8(b/c) | 0 |
| Mensagens de dados, REG equivalente / LOTE | 325 / 15 |
| Bytes por registo, REG equivalente / LOTE incluindo vazios | 78.397 / 62.255 |
| Bytes por registo, só lotes com dados | 61.160 |

A contagem inclui os alarmes, além das 300 leituras enviadas pelo produtor. O custo total em bytes também inclui os lotes vazios.

A tabela seguinte compara os três períodos. O símbolo Δ representa o intervalo definido entre envios de lotes. Para a deteção, usa-se uma execução de 8(c) por período; no caso de 5 s, é a primeira repetição.

| Δ (ms) | Lotes/minuto/partição, sem leituras | Vazios na rajada / sem leituras | Latência média da rajada (ms) | Deteção em 8(c), média [min; max] (s) | Falsas suspeitas em b/c |
|---|---:|---:|---:|---:|---:|
| 1000 | 60.00 | 57.89% / 100% | 501.190 | 3.684 [3.374; 3.896] | 0 |
| 5000 | 12.00 | 60.00% / 100% | 2390.072 | 14.690 [14.291; 14.829] | 0 |
| 10000 | 6.00 | 66.67% / 100% | 6573.150 | 29.390 [28.939; 29.618] | 0 |

Os intervalos teóricos de deteção são ]2,4], ]10,16] e ]20,31] s, respetivamente. Com Δ=10 s, a latência média foi de 6,57 s, acima de Δ/2. Isso pode acontecer porque a rajada é curta e as leituras não chegam de forma uniforme ao longo de todo o período.

Para voltar a executar os testes, usar `./compilar.ps1` e depois `python testes/ensaios.py all --saida ensaios/nova-execucao`. O script inicia o Kubo isolado, se for necessário, e no fim termina apenas os processos que criou. O script `python testes/resumir.py` reúne os resultados guardados na pasta `resultados/`. O teste de suspensão usa a API do Windows para parar temporariamente o processo, sem o reiniciar.

## 11. Reflexão crítica

### Lotes e tempo de espera

Na rajada com Δ=5 s, enviar os 325 registos individualmente exigiria 325 mensagens REG. Com os lotes, foram feitas 15 publicações, incluindo as vazias. São menos 310 publicações, uma redução de 95,38%.

O custo passou de 78,397 bytes por registo no REG equivalente para 62,255 no LOTE, cerca de 20,59% menos. Se forem contados apenas os lotes com dados, o valor é de 61,160 bytes por registo.

Esta redução tem um custo: os registos ficam à espera do próximo envio. Com Δ=5 s, a latência média foi de 2,390 s, perto de metade do período, Δ/2=2,5 s. Com Δ=1 s foi de 0,501 s e com Δ=10 s foi de 6,573 s. A média depende do momento em que as leituras chegam em relação ao próximo envio, da duração da rajada e dos alarmes gerados.

Um sistema de travagem ou um alarme urgente não deve esperar até cinco segundos só para juntar dados. Nesse caso, faria sentido enviar os dados logo à chegada, ou quando a lista atingisse um certo tamanho, e enviar os heartbeats separadamente. Neste trabalho, o lote serve também como heartbeat, tal como pede a ficha.

### Escolha do timeout

O timeout é o tempo sem um novo heartbeat a partir do qual se passa a suspeitar do líder. Com fator 3 e um período de 5 s, esse limite é de 15 s. O momento em que a suspeita aparece também depende de quando o participante faz a verificação.

Na comparação entre períodos de 1, 5 e 10 s, as médias de deteção foram 3,684 s, 14,690 s e 29,390 s, usando uma falha por configuração. Juntando as três repetições com período de 5 s, a média foi de 14,721 s. Um período e um timeout maiores permitem tolerar atrasos maiores, mas também fazem demorar mais a deteção de uma falha.

No teste em que p2 ficou suspenso durante 20 s, surgiram quatro falsas suspeitas, mesmo com fator 3 e Δ=5 s. O processo estava parado temporariamente e depois retomou a mesma execução. Este caso ajuda a distinguir duas propriedades do detetor:

- Exatidão: não suspeitar de processos corretos. Durante a suspensão, esta propriedade não se verificou.
- Completude: acabar por suspeitar de um processo que falhou. Não significa que a deteção seja imediata.

O teste com fator 1 não produziu falsas suspeitas, mas a margem para atrasos é menor: o timeout é igual ao intervalo previsto entre heartbeats. Num sistema assíncrono, em que não há um limite conhecido para os atrasos, o silêncio pode resultar de uma falha ou apenas de uma demora. Por isso, não se consegue garantir que todas as suspeitas estejam certas.

### ultimoSeq e registos em falta

No teste 8(a), a réplica tinha recebido até seq=2. O registo 3 não chegou e também não apareceu um registo 4 que permitisse notar o salto. Quando o líder reiniciou, enviou um lote vazio com ultimoSeq=3. A réplica percebeu que lhe faltava esse registo e pediu-o por TCP. O registo já estava no ficheiro do líder, apesar de nunca ter sido publicado.

No teste com ultimoSeq=0, o décimo lote de cada partição foi descartado com um registo dentro. Os líderes conservaram esses registos, mas as seis cópias nas réplicas ficaram incompletas. Ao chegar o heartbeat seguinte, foi registado HB_PERDIDO, mas não houve RECUPERACAO. CausalCheck não encontrou violações porque os registos dos líderes continuavam a respeitar as regras causais.

Isto mostra para que serve ultimoSeq: mesmo sem trazer dados, um lote pode avisar a réplica de que há registos em falta. A recuperação exige que esses registos estejam guardados e que o líder esteja acessível para responder ao pedido.

### Heartbeats e relógios lógicos

Quando o líder aceita uma entrada IN, cria um registo, atribui-lhe seq e incrementa a sua componente V[k] do relógio vetorial. Um heartbeat apenas anuncia o estado do líder e transporta registos já criados. Por isso, não deve voltar a incrementar os relógios.

No teste que alterava os relógios em cada heartbeat, V[0] chegou a 3 quando o próximo seq ainda era 1. O líder detetou a diferença e rejeitou o registo antes de o escrever. Na versão correta, CausalCheck confirmou V[k]=seq e a condição de Lamport em todos os testes de funcionamento.

Os relógios lógicos ajudam a ordenar os acontecimentos e a representar relações de causa e efeito. Não medem segundos. O envio em lote mantém as regras causais, embora a ordem de chegada dos registos ao processador possa mudar os valores dos relógios entre execuções.

### Detetar uma falha e recuperar os dados

Nas três repetições de 8(c), o produtor publicou 81, 78 e 90 leituras normais para p1. O líder aceitou apenas 8, 4 e 11, respetivamente. Perderam-se, portanto, 73, 74 e 79 entradas nessa partição, sem contar os alarmes.

As réplicas recuperaram os registos que estavam no ficheiro do líder. As leituras que nunca foram aceites não tinham seq nem estavam guardadas nesse ficheiro, pelo que não podiam ser recuperadas por este mecanismo.

Para outro processo substituir o líder, seria preciso decidir quem assume essa função, que histórico deve usar e como impedir que dois líderes atribuam sequências ao mesmo tempo. Isso exigiria mecanismos de coordenação, como eleição e consenso, além de acesso aos dados guardados. Essa parte fica fora desta ficha.

Neste trabalho, SUSPEITO é apenas um aviso. Não muda o destino das mensagens do produtor, não impede o tratamento dos dados recebidos e não escolhe um novo líder.

### Medição do tempo

HeartbeatCheck compara datas e horas registadas por processos Java diferentes. Nos testes, todos correram na mesma máquina e no mesmo fuso horário, usando o mesmo relógio do sistema. SensorRecord guarda os milissegundos para permitir medições mais precisas. Se os programas estivessem em máquinas com relógios diferentes, essa diferença poderia alterar as latências calculadas.

O detetor de falhas usa outra forma de medir o tempo: compara duas leituras de nanoTime feitas dentro do mesmo processo Java, ou JVM. Assim, mede quanto tempo passou desde o último heartbeat aceite. O valor absoluto de nanoTime não é uma data que se possa enviar a outro processo para comparar diretamente.

Por fim, a classificação feita por HeartbeatCheck depende do que aparece nos ficheiros. Segundo a regra da ficha, se não houver uma retoma com a mesma incarnação durante o período observado, a suspeita é classificada como falha real. No teste sem lotes vazios, isso aconteceu mesmo com os líderes ainda vivos. Para interpretar o resultado, é preciso saber o que aconteceu durante o teste: a ausência de mensagens, por si só, não prova que o processo terminou.
