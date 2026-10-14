# E1-COMINDIR01 / REP01 — entrega e verificação

Fonte da tarefa: [Sprint E1-COMINDIR01, issue 14](https://github.com/cunhaestgv/SD-2026-2027/issues/14). Ensaios realizados em 14/10/2026, no mesmo computador Windows, com Java 23 e Kubo 0.43.1. Os valores abaixo são observações desta máquina e desta execução.

## 1. O que foi implementado

Há dois projetos Java independentes, ambos no pacote `rep01`:

- `rep01-lider`: `SensorRecord`, `RecordFile`, `MulticastLeader`, `IpfsPubSub`, `PubSubLeader`;
- `rep01-replica`: `SensorRecord`, `RecordFile`, `MulticastReplica`, `IpfsPubSub`, `PubSubReplica`.

`SensorRecord.toLine()` produz `seq;sensor;temperatura;instante`. O líder é o único que cria novos registos e atribui `seq`: lê o último número de `lider.txt`, incrementa-o, escreve o registo no seu ficheiro e só depois o propaga. As réplicas mantêm `replica-<id>.txt`.

O líder multicast envia um datagrama UDP para `230.0.0.1:6789`. O líder PubSub publica a mesma linha de texto no tópico `rep01-temperaturas` através da API HTTP do seu daemon Kubo. Em ambos, `rajada <n>` gera `n` registos `SIM`, com temperaturas aleatórias entre 15 e 30 °C, e propaga um registo de cada vez, sem pausas deliberadas.

As duas réplicas aplicam a mesma regra: `seq == last + 1` vai para o ficheiro; `seq <= last` ou já retido é duplicado; `seq > last + 1` fica numa `TreeMap` até chegarem os anteriores. Após uma escrita, a réplica procura sucessivamente `last + 1` na árvore. Registos textuais inválidos são rejeitados e a réplica continua à escuta. Não há ACK, retransmissão nem recuperação de uma réplica atrasada.

Para `SIM`, cada réplica mostra o instante da primeira e da última receção, o total de mensagens recebidas, o total escrito no ficheiro e quantas estão retidas. Uma receção duplicada aumenta `recebidos`, mas não altera `last`, a árvore ou o ficheiro. Como o protocolo não contém um marcador de fim de rajada, os números apresentados são acumulados durante a execução; cada ensaio abaixo começou com processos e ficheiros novos.

## 2. Execução

Compilar separadamente, dentro de cada projeto. Nesta máquina, `java` no `PATH` aponta para Java 8 e `javac` não está no `PATH`; por isso os comandos abaixo usam o JDK 21 incluído no Android Studio. Noutro computador, adaptar este caminho a um JDK 11 ou superior.

```powershell
New-Item -ItemType Directory -Path out -Force | Out-Null
& 'C:\Program Files\Android\Android Studio\jbr\bin\javac.exe' --release 11 -encoding UTF-8 -d out src/rep01/*.java
```

Para multicast, iniciar primeiro três réplicas em consolas distintas, no projeto `rep01-replica`:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -cp out rep01.MulticastReplica 1
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -cp out rep01.MulticastReplica 2
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -cp out rep01.MulticastReplica 3
```

Numa quarta consola, no projeto `rep01-lider`:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -cp out rep01.MulticastLeader
```

Para PubSub, instalar Kubo e confirmar primeiro, pela CLI, que uma publicação chega a uma subscrição:

```powershell
ipfs init
ipfs config --json Pubsub.Enabled true
ipfs daemon
ipfs pubsub sub rep01-temperaturas
'teste' | ipfs pubsub pub rep01-temperaturas
```

O `daemon` e o `sub` ficam em consolas separadas. Depois, no projeto das réplicas, iniciar três consolas:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -cp out rep01.PubSubReplica 1 5001
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -cp out rep01.PubSubReplica 2 5001
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -cp out rep01.PubSubReplica 3 5001
```

No projeto do líder:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -cp out rep01.PubSubLeader
```

O segundo argumento da réplica é o porto da API do Kubo. No cenário de dois nós, a réplica 1 usa `5001`, e as réplicas 2 e 3 usam `5002`. O líder usa por omissão `http://127.0.0.1:5001/api/v0`; também aceita outro endereço de API como argumento.

O segundo nó foi inicializado num repositório IPFS separado (`.ipfs-no2`), com PubSub ativo, API `5002`, gateway `8081` e Swarm `4002`. Foi ligado ao primeiro com `ipfs swarm connect /ip4/127.0.0.1/tcp/4002/p2p/<id-do-segundo-nó>`. `ipfs pubsub peers rep01-temperaturas` no primeiro nó mostrou o segundo como interessado no tópico. Num computador novo, é preciso instalar/configurar Kubo novamente.

## 3. Testes funcionais

No multicast, a sequência de chegada `1, 3, 3, 2` produziu quatro receções, três escritas, uma mensagem `EM ESPERA`, uma `DUPLICADO` e a aplicação em cascata de `3` depois de `2`. O ficheiro final continha `1, 2, 3` pela ordem correta. Uma mensagem inválida produziu `REJEITADO` e não terminou a escuta.

Com um só nó Kubo, um registo normal seguido de `rajada 10` produziu 11 linhas no líder e em cada uma das três réplicas. Os quatro ficheiros tinham o mesmo SHA-256. Com dois nós ligados, um ensaio curto confirmou a passagem de mensagens para as réplicas do segundo nó, mas essas réplicas só receberam cinco dos 11 registos. `seq 6` a `10` não chegaram, enquanto `11` chegou e ficou retido. Isto demonstra que a ligação entre nós funciona, mas não garante entrega completa.

Quando a réplica 2 foi parada após os primeiros cinco registos e reiniciada depois de mais três publicações, os registos `9` e `10` ficaram `EM ESPERA (falta o registo 6)`. O líder e as réplicas 1 e 3 tinham dez linhas; a réplica 2 ficou com cinco. O mesmo resultado foi observado com multicast e com PubSub num só nó.

## 4. Rajadas de 1000 registos

Cada ensaio usou um diretório vazio, um líder, três réplicas e uma nova execução de `rajada 1000`. O tempo de envio é `último envio - primeiro envio`. O débito do líder é `1000 / tempo de envio em segundos`. A latência é `última receção da réplica mais lenta - primeiro envio do líder`. Os relógios são comparáveis porque tudo correu na mesma máquina.

| Cenário | Ensaio | Envio do líder (ms) | Débito (registos/s) | Latência até à réplica mais lenta (ms) | Resultado |
|---|---:|---:|---:|---:|---|
| Multicast | 1 | 1231 | 812,35 | 1559 | 1000 recebidos/escritos por réplica; 0 pendentes |
| Multicast | 2 | 1173 | 852,51 | 1510 | 1000 recebidos/escritos por réplica; 0 pendentes |
| Multicast | 3 | 1333 | 750,19 | 1633 | 1000 recebidos/escritos por réplica; 0 pendentes |
| PubSub, um nó | 1 | 10005 | 99,95 | 10005 | 1000 recebidos/escritos por réplica; 0 pendentes |
| PubSub, um nó | 2 | 9061 | 110,36 | 9061 | 1000 recebidos/escritos por réplica; 0 pendentes |
| PubSub, um nó | 3 | 8600 | 116,28 | 8600 | 1000 recebidos/escritos por réplica; 0 pendentes |
| PubSub, dois nós | 1 | 19780 | 50,56 | 19798 | Réplica 1: 1000/1000; réplicas 2 e 3: 995 recebidos, 31 escritos, 964 pendentes |
| PubSub, dois nós | 2 | 21973 | 45,51 | 21994 | Réplica 1: 1000/1000; réplicas 2 e 3: 995 recebidos, 124 escritos, 871 pendentes |
| PubSub, dois nós | 3 | 25091 | 39,85 | 25098 | Réplica 1: 1000/1000; réplicas 2 e 3: 995 recebidos, 121 escritos, 874 pendentes |

Nos três ensaios com dois nós, as réplicas 2 e 3 perderam os mesmos cinco `seq` em cada ensaio. No primeiro faltaram `32, 34, 714, 731, 947`; no segundo, `125, 509, 510, 517, 518`; no terceiro, `122, 324, 325, 327, 328`. As duas réplicas ligadas ao segundo nó tinham ficheiros idênticos entre si e divergentes do líder. Não se observaram duplicados nestes ensaios. Como o líder e a réplica 1 tinham os 1000 registos, e as duas réplicas do segundo nó falharam exatamente os mesmos números, a perda ocorreu no caminho até à distribuição local do segundo nó. Os logs da aplicação não permitem afirmar se a causa exata foi a rede, o encaminhamento entre nós ou o próprio daemon.

### Tabela comparativa do ponto 16

| Métrica | Multicast | PubSub — um nó | PubSub — dois nós |
|---|---:|---:|---:|
| Tempo médio de envio da rajada no líder | 1245,67 ms | 9222,00 ms | 22281,33 ms |
| Latência média até à réplica mais lenta | 1567,33 ms | 9222,00 ms | 22296,67 ms |
| Débito médio no líder | 805,02 registos/s | 108,86 registos/s | 45,31 registos/s |
| Registos perdidos, por réplica | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 5 / 5 em cada ensaio |
| Duplicados detetados; pendentes no fim | 0; 0 / 0 / 0 | 0; 0 / 0 / 0 | 0; réplica 1: 0; réplicas 2 e 3: 964, 871 e 874 nos três ensaios |
| Além da JVM | Nada | Um daemon Kubo | Dois daemons Kubo ligados |
| Configuração até à primeira entrega | Grupo, porto, TTL e três `joinGroup()` | `ipfs init`, PubSub ativo, daemon, teste CLI, tópico e API | Configuração de portos/repositório do segundo nó, `swarm connect` e confirmação em `pubsub peers` |
| Réplica atrasada | Perde as mensagens; registos posteriores ficam retidos | Mesmo comportamento observado | PubSub não guarda publicações para entrega posterior; comportamento esperado, sem ensaio específico de réplica atrasada neste cenário |
| Entre redes diferentes sem alterar routers | Em geral, não: multicast IP costuma ficar na rede local | Sim, com nós IPFS alcançáveis | Sim, com nós IPFS alcançáveis e ligados |

O menor tempo medido foi o multicast. No PubSub, cada `publish` é um pedido HTTP síncrono; o líder espera pela resposta do Kubo antes do próximo registo. No cenário de dois nós há ainda encaminhamento entre nós. As medições têm carga e estado de rede variáveis, portanto comparam estas execuções, não estabelecem um limite universal de desempenho.

## 5. Falhas deliberadas — ponto 14

Cada falha foi ensaiada isoladamente. As alterações temporárias ao endereço de grupo, tamanho do buffer e nome do tópico foram repostas no código final. Os ficheiros de saída dos ensaios foram criados em diretórios temporários separados.

| Falha introduzida | Mecanismo | Lado e erro observado | Ficheiros divergiram? | Conclusão |
|---|---|---|---|---|
| Líder enviou três registos antes de arrancar as réplicas; depois enviou `4` e `5` | Multicast | Nenhuma exceção. Cada réplica mostrou `EM ESPERA: 4/5 (falta o registo 1)` | Sim: líder com 5 linhas; ficheiros das réplicas ainda inexistentes | Não há entrega retroativa nem aviso ao líder. `seq` revela a lacuna quando chega um número posterior. |
| Réplica 2 no grupo `230.0.0.2` | Multicast | Nenhuma exceção; a réplica ficou à escuta no grupo errado | Sim: líder e réplica correta com 3 linhas; réplica 2 sem ficheiro | Um `joinGroup()` bem-sucedido não garante que o endereço coincida com o do emissor. |
| Buffer de receção com 10 bytes | Multicast | `SensorRecord.fromLine()` lançou `IllegalArgumentException`, capturada na réplica como `REJEITADO`; a réplica continuou ativa | Sim: líder com 3 linhas; réplica sem ficheiro | Um datagrama truncado não é um registo válido. A rejeição evita escrever dados incompletos. |
| Parar a réplica 2 durante `rajada 1000`, reiniciá-la e enviar mais dois registos | Multicast | Nenhuma exceção | Sim: líder, réplicas 1 e 3 com 1002 linhas; réplica 2 com 83 | A réplica 2 ficou à espera do `seq 84`; `1001` e `1002` foram retidos. O buffer antigo em memória perdeu-se no reinício. |
| Daemon do líder parado | PubSub | `ConnectException` no `PubSubLeader`, capturada e mostrada; a réplica ligada ao outro nó não lançou exceção | Sim: líder com 1 linha; réplica sem ficheiro | O líder escreveu antes de tentar publicar. A API indisponível é um erro visível do lado do líder. |
| `Pubsub.Enabled=false` no nó da réplica 2 | PubSub | `IOException: sub falhou (500)` na `PubSubReplica` | Sim: líder publicou 3 registos; réplica sem ficheiro | O daemon estava ativo, mas recusou a subscrição. A opção foi reposta em `true` depois do ensaio. |
| Réplica subscreveu `rep01-temp` | PubSub | Nenhuma exceção | Sim: líder e réplica correta com 3 linhas; réplica no tópico errado sem ficheiro | Tópicos diferentes não se cruzam e a ausência de mensagens pode ser silenciosa. |
| Dois nós sem ligação, sem bootstrap e com MDNS desativado | PubSub | Nenhuma exceção; `swarm peers` mostrou 0 em ambos | Sim: líder com 3 linhas; réplica do outro nó sem ficheiro | O daemon aceitar a publicação não prova que outro nó a recebeu. Foram usados dois nós temporários isolados e parados após o ensaio. |

## 6. Construções e garantias — ponto 15

| Construção | Onde foi usada | O que garante neste código | O que continua sem garantia |
|---|---|---|---|
| `MulticastSocket` e `joinGroup()` | Líder e réplica multicast | Envio ao endereço de grupo e receção por sockets associados ao grupo | Entrega, ordem, ausência de duplicados e receção por membros desligados |
| `setTimeToLive(1)` | `MulticastLeader` | Limita a propagação à rede local, sem atravessar múltiplos routers | Que todos os membros da rede local recebam o datagrama |
| `append` antes de propagar | Ambos os líderes | Um registo que o líder tenta publicar já foi guardado localmente | Que qualquer réplica o receba ou escreva |
| `seq` | Líderes e réplicas | Permite detetar duplicados e lacunas e manter o ficheiro da réplica ordenado | Recuperar um registo perdido ou saber se a lacuna é temporária |
| `toLine()` / `fromLine()` | Ambos os transportes | Uma representação textual comum para ficheiro e rede | Autenticidade do emissor ou validação semântica completa dos campos |
| Tópico `rep01-temperaturas` | Líder e réplica PubSub | Publicações e subscrições com o mesmo nome encontram-se quando há ligação | Armazenamento de mensagens ou entrega a subscritores ausentes |
| Daemon IPFS e API HTTP | `IpfsPubSub` e programas PubSub | O Java comunica com o seu nó e este participa na distribuição | Que os nós estejam ligados ou que cada réplica aplicou o registo |
| Multibase | `IpfsPubSub.toMultibase()` e `fromMultibase()` | Codifica o tópico exigido pela API e descodifica os dados recebidos em JSON | Fiabilidade, ordem ou segurança da aplicação |

## 7. Reflexão crítica — ponto 17

**Desempenho.** Nesta máquina, o multicast foi mais rápido. O líder multicast escreve no ficheiro e faz `send` UDP; o líder PubSub faz um pedido HTTP para cada registo e espera pela resposta do daemon. Com dois nós, a mensagem ainda atravessa uma ligação IPFS. A diferença medida não deve ser generalizada para todas as redes e configurações.

**Escalabilidade.** Com 300 réplicas, o líder multicast continuaria a fazer um `send` por registo; a rede teria de distribuir as cópias aos membros e o tráfego em cada ligação dependeria da topologia. No PubSub, os nós encaminham mensagens para vizinhos por *gossip*: o emissor não faz 300 envios diretos, mas o tráfego total e a carga dos nós aumentam. O multicast IP geralmente não atravessa a Internet pública; o PubSub pode fazê-lo se os nós estiverem alcançáveis e ligados.

**Fiabilidade.** As maiores perdas observadas ocorreram no ensaio PubSub com dois nós: cinco registos por réplica do segundo nó em cada rajada. `seq` identificou os números ausentes e impediu escritas fora de ordem. Não determinou o componente exato da perda nem trouxe esses registos de volta. A ausência de ACK significa que o líder não sabe se uma réplica escreveu.

**Recuperação.** Quando falta permanentemente um `seq`, o ficheiro da réplica para no último número contínuo e a `TreeMap` cresce com os posteriores. Recuperação automática exigiria um mecanismo adicional para pedir e obter os registos perdidos ou transferir estado, explicitamente fora do âmbito da ficha.

**Dependências e alcance.** Num edifício com rede local que suporta multicast, escolheria multicast para este protótipo pela simplicidade e pelos tempos medidos. Para sensores em várias cidades, escolheria PubSub, porque os nós IPFS podem ligar-se por redes IP normais; pagaria o custo dos daemons e da configuração. Em nenhum caso assumiria fiabilidade completa sem mecanismos adicionais.

**Ponto único de falha.** Se o líder parar, ninguém atribui novos `seq` nem cria novos registos. Multicast e PubSub apenas transportam mensagens; nenhum elege substituto. Dois líderes simultâneos poderiam atribuir o mesmo `seq` a registos diferentes, exigindo coordenação fora da ficha.

**Segurança.** Qualquer processo capaz de enviar para o grupo ou publicar no tópico pode tentar introduzir um registo forjado. As réplicas atuais validam o formato, não a identidade do emissor. O campo `from` do PubSub identifica o nó publicador e poderia ser verificado contra uma identidade autorizada, mas esta verificação não está implementada em `IpfsPubSub` nem substitui por si só uma política de autorização. Esta limitação deve ser assumida na apresentação.

## 8. Pontos para defesa oral

- `Scanner.hasNextLine()`/`nextLine()` esperam pela CLI; `MulticastSocket.receive()` espera por um datagrama; o processamento da resposta de `subscribe()` espera por cada linha JSON; `HttpClient.send()` pode esperar pela resposta do daemon.
- O líder faz uma publicação por registo, não percorre uma lista de réplicas. Um `send` multicast gera um datagrama para o grupo; quando tudo corre na mesma máquina, a entrega pode ser local e não implica três transmissões físicas na placa de rede. Noutras máquinas, a rede distribui as cópias. O sistema operativo permite aos sockets multicast partilhar o porto de receção.
- TTL 0 restringiria ainda mais o alcance ao anfitrião local; TTL 1 permite a rede local; TTL 32 só aumenta o limite de saltos, sem obrigar routers a encaminhar multicast.
- Reiniciar o líder usa `RecordFile.lastSeq()` para continuar a numeração. Reiniciar uma réplica também lê o último `seq` escrito, mas perde os registos que estavam apenas na `TreeMap` em memória.
- No PubSub, o Java do líder fala por HTTP com o Kubo local. O Java da réplica mantém uma subscrição HTTP aberta ao seu Kubo. O nome do tópico é codificado em multibase na chamada à API; os dados chegam codificados no JSON e são descodificados antes de `SensorRecord.fromLine()`.
- PubSub não guarda ficheiros no IPFS nem mensagens para uma réplica que entre amanhã. O teste com dois nós confirma encaminhamento entre nós; o teste com um só nó não demonstra esse percurso.
- Ficheiros iguais numa execução mostram que aqueles registos foram replicados nessa execução. Não demonstram uma garantia de entrega para execuções futuras.
