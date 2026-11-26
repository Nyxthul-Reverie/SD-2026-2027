# TPF · Sprint E2-03

O líder recebe registos no Discord, produz chunks cifrados no disco local e
envia-os às réplicas voluntárias pelo hub Pub/Sub WebSocket. O catálogo regista
as cópias confirmadas. A réplica é uma aplicação Java autónoma.

## Compilar e arrancar

Usa Java 17+ e Maven a partir de `Sprints/E2-PROJETO`. No líder, configura
`DISCORD_TOKEN` e `DISCORD_GUILD_ID` nas variáveis de ambiente ou num ficheiro
`.env` local. No servidor de teste, convida o bot, ativa o intent **Message
Content** e cria o canal `#arquivo` e o papel `admin-arquivo`.

```powershell
mvn clean package
mvn -f tpf-replica/pom.xml clean package
java -jar target/tpf-lider-1.0-SNAPSHOT.jar
```

O caminho de dados por omissão é `dados-lider`. Em Windows, se o projeto estiver
num volume sem ACL, especifica uma pasta privada em NTFS.

```powershell
java "-Dtpf.dados=$env:LOCALAPPDATA\SDT-E2" -jar target/tpf-lider-1.0-SNAPSHOT.jar
```

Mantém uma instância do líder por pasta de dados. Em cada reinício usa a mesma
pasta e conserva a `master.key`. Podes ajustar a selagem com
`-Dtpf.chunkMax=65536`, `-Dtpf.pad=4096` e `-Dtpf.selagem=5000` antes de `-jar`.

Para iniciar N réplicas, um administrador executa `/replica-token` N vezes e
entrega cada token em privado. Abre um terminal por réplica na pasta
`Sprints/E2-PROJETO`. Usa um identificador único, com até 64 letras ASCII,
algarismos, hífen ou underscore, e indica a quota em bytes.

```powershell
java -jar tpf-replica/target/tpf-replica-1.0-SNAPSHOT.jar r1 ws://127.0.0.1:8765 8000000 $tokenR1
java -jar tpf-replica/target/tpf-replica-1.0-SNAPSHOT.jar r2 ws://127.0.0.1:8765 2000000 $tokenR2
java -jar tpf-replica/target/tpf-replica-1.0-SNAPSHOT.jar r3 ws://127.0.0.1:8765 4000000 $tokenR3
```

Nos reinícios, omite o token e conserva `dados-<id>/chaves/`. Os formatos das
chaves são PKCS#8 (`privada.pk8`) e X.509 (`publica.x509`), com permissões
reservadas ao dono. Se a primeira ligação cair entre a prova e o pong, a
réplica alterna o OLA de registo com o OLA simples até o hub confirmar a ligação.
O `ws://` usado acima destina-se ao ensaio local. Num percurso de rede onde
seja preciso proteger o token em trânsito, usa `wss://` com terminação TLS.

## Escrita

- `/registar texto:<texto>` e mensagens humanas em `#arquivo` escrevem em
  `registos.log`. Cada linha é `lsn;instante;autor;canal;textoBase64`, em UTF-8
  com LF. O instante é Unix em milissegundos. Autor e canal são IDs Discord.
- A confirmação com LSN segue `FileChannel.force(true)`. O LSN seguinte parte do
  último registo completo e cresce a partir de 1.
- `/rajada n:<n> intervalo-ms:<ms>` (papel `admin-arquivo`) injeta registos
  sintéticos pelo mesmo caminho e indica quando aceitou o pedido. Acompanhe
  `eventos-lider.txt` para ver as escritas e selagens.
- `/replica-token` entrega ao administrador, numa resposta efémera, um token
  de uso único. Por omissão expira passados 10 minutos.
- `/replica-remover id:<id>` (papel `admin-arquivo`) revoga a chave, fecha
  a ligação e retira a réplica da colocação. As cópias já confirmadas
  permanecem no catálogo para tratamento no próximo sprint.
- `/estado` mostra o estado, a ocupação e o número de chunks por réplica. Mostra
  também os totais de chunks `SELADO`, `DURAVEL` e subreplicados.
- `/config` e `/recuperar` conservam as respostas provisórias.

## Formatos

O conteúdo antes de cifrar usa ordem big-endian. Começa com um inteiro de 4
bytes que indica quantos registos se seguem. Cada registo tem quatro inteiros
de 8 bytes (`lsn`, `instante`, `autor`, `canal`), um inteiro de 4 bytes com o
comprimento do texto em bytes e, a seguir, o texto em UTF-8. Depois do último
registo são acrescentados zeros até ao próximo múltiplo de `PAD_BYTES`, se
necessário. O leitor usa a contagem e os comprimentos para ignorar esse padding.

O `.bin` contém o texto cifrado e, no fim, a tag GCM de 16 bytes. O nome base
é o SHA-256 hexadecimal de todo o `.bin`. O `.hdr` associado tem esse mesmo
nome base e contém `keyVersion;base64(salt);base64(nonce)` seguido de LF.
A versão é 1. O salt tem 32 bytes e o nonce tem 12. A chave do chunk vem de
HKDF-SHA256. A AAD é `tpf-v1` em ASCII, seguida da versão de 4 bytes big-endian,
do salt e do nonce.

No log de eventos, `REGISTO` indica o LSN e `SELADO` identifica o chunk, o
intervalo de LSN e o tamanho do `.bin`.

## Protocolo WebSocket

Os binários em mensagens de texto são codificados em Base64. Cada linha da
tabela representa uma mensagem WebSocket completa de texto.

| Sentido | Mensagem | Finalidade |
| --- | --- | --- |
| Réplica → hub | `OLA;<id>;<chavePublicaB64>;<token>` | Primeiro registo. |
| Réplica → hub | `OLA;<id>` | Nova ligação de uma réplica já registada. |
| Hub → réplica | `DESAFIO;<nonceB64>` | 32 bytes aleatórios novos para esta ligação e uma única tentativa. |
| Réplica → hub | `PROVA;<id>;<assinaturaB64>` | Ed25519 sobre `tpf-registo\|<id>\|<nonceB64>` em UTF-8. |
| Réplica → hub | `SUB;tpf-r-<id>` e `SUB;tpf-ctl` | Subscrição ao tópico próprio e ao tópico comum. |
| Réplica → líder, via hub | `PUB;tpf-lider;INVENTARIO;<id>;<chunkId1>,<chunkId2>,...` | Lista de chunks completos após cada ligação. Lista vazia termina em `;`. |
| Réplica → líder, via hub | `PUB;tpf-lider;HB;<id>;<usados>;<quota>` | Ocupação em bytes, anunciada logo ao ligar e periodicamente. |
| Líder → réplica, via hub | `STORE;<chunkId>;<hdrB64>;<binB64>` | Bytes do `.hdr` e do `.bin` no tópico da réplica escolhida. |
| Réplica → líder, via hub | `PUB;tpf-lider;ACK;<id>;<chunkId>;<sha256>` | Confirma a gravação ou a presença de uma cópia local com o hash esperado. |

O hub passa a ligação de `NOVA` para `DESAFIADA` e depois para
`AUTENTICADA`. No primeiro registo, só escreve `replicas.txt` e consome
o token após uma prova válida e após verificar novamente a validade do token.
Cada linha de `replicas.txt` contém `id;chavePublicaB64`. Mensagens fora de
ordem e operações em tópicos proibidos geram `ERRO` e fecho WebSocket 1008.
`PUB` da réplica só é permitido em `tpf-lider`. O hub entrega a mensagem em
processo com a identidade autenticada e entrega `STORE` apenas ao subscritor
do tópico de destino. O tópico comum está disponível para difusão futura.
Um ping/pong WebSocket após as mensagens iniciais marca a conclusão da
ligação para o backoff. O inventário fica registado para observação.

## Colocação, durabilidade e catálogo

O líder calcula `livre = quota − usados − reservados`. As réplicas VIVA e
SUSPEITA podem ser escolhidas se tiverem espaço para o chunk e para a margem.
A ordenação usa `livre^ALFA / -ln(u)`, onde `u = (v + 0.5) / 2^53` e `v`
são os primeiros 53 bits de SHA-256 de `chunkId + "|" + replicaId`. A
implementação compara os logaritmos das pontuações para manter a ordem e
evitar overflow. Em caso de empate usa o identificador da réplica.

A quota e a reserva contam os bytes do `.bin`, conforme o cálculo de
`usados` do enunciado. A réplica verifica o hash, grava o `.hdr`, publica
o `.bin` e confirma. Cada publicação usa um ficheiro temporário na
mesma pasta e `ATOMIC_MOVE`. A presença dos dois ficheiros e o hash do
`.bin` permitem confirmar um `STORE` repetido sem voltar a escrever.

Por cada chunk, o catálogo tem uma linha `chunkId;lsnDe;lsnAte;tamanho;replicas;estado`.
`replicas` lista identificadores distintos separados por vírgulas. O chunk
entra como `SELADO` e passa a `DURAVEL` quando atinge
`max(W_MIN, floor(R/2)+1)` ACK válidos. A subreplicação é calculada
separadamente e mantém-se enquanto houver menos de `R` cópias confirmadas.

Quando expira uma ronda, o líder liberta as reservas pendentes e exclui da
próxima ronda as réplicas já tentadas. Para após `MAX_RONDAS` ou quando
obtém `R` cópias. Um ACK chega a tempo se ainda houver um STORE pendente
para aquela réplica e aquele chunk. ACKs tardios, duplicados ou enviados
sem um STORE pendente são rejeitados. A cada alteração, o catálogo é escrito
atomicamente. No arranque, o líder carrega o catálogo e inclui chunks válidos
do E2-02 que ainda estejam por catalogar. Rondas são mantidas em memória
durante a escrita. Os chunks já catalogados mantêm as cópias confirmadas
no reinício. A reparação posterior e a reconciliação do inventário pertencem
ao sprint seguinte, tal como manifesto, `FETCH`, `DELETE`, desafios de
posse, recuperação a partir das réplicas e `ArquivoCheck`.

Em `eventos-lider.txt` ficam `ESTADO <id> <antigo> -> <novo>`,
`INVENTARIO <id> <n>`, `STORE <chunkId> -> <id>`, `ACK <chunkId> <id>`,
`ACK-REJEITADO <chunkId> <id> <motivo>`, `DURAVEL <chunkId>`,
`SUBREPLICADO <chunkId> <n>/<R>` e `RONDA <chunkId> <k>`.
Em `eventos-<id>.txt`, cada réplica regista `LIGADO`, `DESLIGADO`,
`RELIGAR <ms>`, `STORE`, `QUOTA` e `ERRO`.

### Parâmetros

Passa a propriedade com `-D` antes de `-jar`. Para mudar o porto, atualiza
também o URL dado às réplicas. Se ajustares valores partilhados, usa as mesmas
opções nos processos que os consomem.

| Constante | Propriedade JVM | Omissão |
| --- | --- | ---: |
| `PORTO` | `tpf.porto` | 8765 |
| `PERIODO_HB_MS` | `tpf.periodoHbMs` | 2000 |
| `SUSPEITA_MS` | `tpf.suspeitaMs` | 6000 |
| `MORTA_MS` | `tpf.mortaMs` | 10000 |
| `TOKEN_VALIDADE_MS` | `tpf.tokenValidadeMs` | 600000 |
| `R` | `tpf.r` | 3 |
| `W_MIN` | `tpf.wMin` | 2 |
| `ACK_TIMEOUT_MS` | `tpf.ackTimeoutMs` | 4000 |
| `MAX_RONDAS` | `tpf.maxRondas` | 3 |
| `MARGEM_BYTES` | `tpf.margemBytes` | 65536 |
| `ALFA` | `tpf.alfa` | 1.0 |
| `BACKOFF_MIN_MS` | `tpf.backoffMinMs` | 1000 |
| `BACKOFF_MAX_MS` | `tpf.backoffMaxMs` | 30000 |

### Concorrência

- Os eventos JDA delegam o registo e a rajada ao executor local, também usado
  pelo temporizador de selagem. O monitor de `ArquivoLocal` protege o LSN
  e o chunk em formação. O callback da selagem apenas enfileira a replicação.
- As callbacks WebSocket e o Discord acedem ao `PubSubHub` sob o monitor
  do hub. Este protege ligações, chaves, tokens e subscrições. A entrega
  à fila de replicação ocorre sob esse monitor. O fecho do socket ocorre
  fora dele para evitar um ciclo com o bloqueio interno do WebSocket.
- O executor de `EscritaReplicada` processa chunks, HB, inventários, ACK,
  revogações, transições e timeouts em sequência. Uma publicação já em fila
  é descartada se a chave que a autenticou entretanto foi revogada.
- O monitor de `ReplicaRegistry` protege espaço usado e reservado e cada
  `HeartbeatMonitor`. Escolha e reserva são indivisíveis. O monitor de
  `Catalog` protege o mapa e a escrita atómica. As vistas devolvidas
  aos leitores são instantâneos. Os eventos do líder partilham um bloqueio
  de append.
- Na réplica, o monitor da ligação serializa gravações, ACK e HB. O ACK
  de uma escrita precede os HB que já incluem os seus bytes. O próximo
  HB substitui o valor absoluto de usados no líder, preservando as reservas
  ainda pendentes.

## Questões de reflexão

1. **Token e Ed25519.** O token autoriza a inscrição inicial. Ed25519 prova a
   posse da chave privada e autentica as ligações seguintes. Quem intercetar
   um token válido antes do primeiro uso pode inscrever uma chave própria
   e consumir o token. O token é uma credencial de posse com validade curta.
2. **ID e prefixo.** O ID vincula a assinatura à identidade que o hub
   está a verificar. O prefixo vincula-a a este protocolo. O nonce novo
   vincula-a a uma ligação e impede repetir uma prova antiga.
3. **Escolha e reserva.** Com 200 KiB livres e margem de 64 KiB, dois chunks
   de 120 KiB passam individualmente. Se ambos lerem o espaço antes de
   reservar, comprometem 240 KiB na mesma réplica. Com o mesmo bloqueio,
   o segundo vê 80 KiB livres e procura outra candidata.
4. **Mínimo e maioria.** O mínimo absoluto mantém um piso de cópias mesmo
   com `R` pequeno. Com `R=5`, a maioria sobe o limiar de 2 para 3.
   Uma única cópia confirmada pode perder-se com uma falha. Exigir `R`
   confirmações aumenta a latência e impede declarar durabilidade enquanto
   faltar uma única réplica.
5. **Escritor único.** O líder é o único escritor. Um chunk imutável é
   identificado pelo hash dos bytes cifrados, por isso as réplicas apenas
   aceitam os bytes daquela identidade. A maioria aqui mede a redundância
   confirmada. Em sistemas com vários escritores, os quóruns também podem
   servir para coordenar versões concorrentes.
6. **Efeito de α.** Com α positivo, as réplicas com mais espaço têm maior
   probabilidade de ficar em primeiro lugar. Aumentar α acentua a diferença.
   Com α zero, o peso é igual entre as candidatas, mas a quota continua a
   determinar quais passam o filtro de espaço e margem.
7. **Gravar antes do ACK.** O ACK faz o líder contar uma cópia. O temporário
   é escrito e forçado para disco antes da publicação atómica. O ACK segue
   apenas depois de ambos os nomes finais estarem publicados.
8. **ID autenticado.** Qualquer cliente pode escolher o texto do campo
   `id`. O líder confere esse texto com a identidade provada na ligação
   e com o destino do STORE, para impedir que um cliente conte por outro.

## Testes

```powershell
mvn test
mvn "-Dtest=CatalogTest" "-Dtpf.r=5" test
mvn "-Dtest=CatalogTest" "-Dtpf.wMin=3" test
```

Os testes usam WebSocket em loopback, chaves e tokens temporários e uma
pasta JUnit por cenário. As falhas intencionais são enviadas por clientes
de teste. A suite principal usa os parâmetros por omissão.

| Objetivo da issue #22 | Implementação e verificação |
| --- | --- |
| 1 · Parâmetros | `Wire`, valores documentados, suite com os valores por omissão e ensaios do catálogo com `R` e `W_MIN` alterados. |
| 2 · Registo e autenticação | `PubSubHubTest` executa o desafio-resposta Ed25519 e verifica o consumo do token. |
| 3 · Hub | `PubSubHubTest` verifica ordem, encaminhamento, identidade e isolamento de tópicos. |
| 4 · Réplica | `EscritaReplicadaTest` executa o serviço real, exercita disco, inventário, quota, hash, ACK e backoff. |
| 5 · Registo de réplicas e HB | `ColocacaoTest` verifica limites de tempo e reservas. O ensaio de rajada verifica também a ausência e retoma de HB reais. |
| 6 · Colocação | `ColocacaoTest` verifica determinismo, margem, exclusões e distribuição. |
| 7 · Escrita e catálogo | `CatalogTest` e os ensaios de rajada, migração do E2-02 e nova ronda validam o caminho a partir da selagem. |
| 8 · Discord | `LeaderBot` chama os serviços testados. Os passos que exigem a interface Discord estão abaixo. |
| 9 · Concorrência | Testes de reserva concorrente e de disputa pelo token. A secção Concorrência identifica as restantes fronteiras de sincronização. |

| Experiência da issue #22 | Evidência reproduzível |
| --- | --- |
| 1 · Três inscrições, token repetido/expirado e prova com chave errada | `rajadaCifradaDurabilidadeSubreplicacaoEReinicio` e `PubSubHubTest`, que também verifica expiração entre OLA e PROVA, corrida pelo mesmo token, replay e ACL dos tópicos. |
| 2 · VIVA → SUSPEITA → MORTA → VIVA e inventário | `rajadaCifradaDurabilidadeSubreplicacaoEReinicio` e `ColocacaoTest`. |
| 3 · Desligar o líder e observar backoff | `backoffExponencialERepostoAposReligacao`. Foram observados 1000, 2000 e 4000 ms, com regresso a 1000 ms após religação. |
| 4 · Rajada de 50, três cópias e SHA-256 | `rajadaCifradaDurabilidadeSubreplicacaoEReinicio` passa os mesmos 50 registos sintéticos pelo arquivo que o comando usa, confirma 3/3 e compara os hashes e ficheiros. |
| 5 · Distribuição para α = 0, 1 e 2 | `medePrimeiraEscolhaSemAlterarVagas`. Resultados abaixo. |
| 6 · Duas e uma réplicas | `rajadaCifradaDurabilidadeSubreplicacaoEReinicio` verifica `DURAVEL` com 2/3 e `SELADO` com 1/3. |
| 7 · Exclusão por espaço e recusa de STORE sem quota | `ColocacaoTest` verifica o filtro e `storeValidaQuotaHashFicheirosEAckIdempotente` verifica `QUOTA`. |
| 8 · ACK com hash errado e terceira cópia na segunda ronda | `ackComHashErradoIdentidadeErradaOuSemStoreENovaRonda`. |
| 9 · ACK com identidade de outra réplica | O mesmo teste verifica a rejeição por identidade e por falta de STORE pendente. |

Primeira escolha entre 10 000 chunks, com espaço livre constante.

| α | 8 MB | 2 MB | 4 MB | 6 MB |
| ---: | ---: | ---: | ---: | ---: |
| 0 | 24,50% | 24,19% | 25,92% | 25,39% |
| 1 | 39,70% | 9,84% | 20,18% | 30,28% |
| 2 | 52,89% | 3,26% | 13,56% | 30,29% |

### Critérios de aceitação

| Critério | Verificação |
| --- | --- |
| 1 · Token válido, único, com expiração e prova válida | `PubSubHubTest`. |
| 2 · Religação por OLA e proteção contra provas repetidas ou de outra identidade | `PubSubHubTest` e ensaio de backoff. |
| 3 · Ordem e permissões dos tópicos | `PubSubHubTest`. |
| 4 · Papel `admin-arquivo` e token efémero | Verificado no código JDA. Ensaio no servidor Discord pendente. |
| 5 · Estados, visibilidade e backoff | Testes WebSocket e consulta ao mesmo estado usado por `/estado`. Apresentação na interface Discord pendente. |
| 6 · Reserva concorrente e capacidade após o ACK | `escolhaEReservaSaoAtomicasAntesEDepoisDoAck`. |
| 7 · Colocação determinística e distribuição | `ColocacaoTest` e tabela acima. |
| 8 · R cópias, limiar e subreplicação | Teste de rajada, `CatalogTest` com R=5 e com W_MIN=3. |
| 9 · ACK após gravação e rejeição por hash ou identidade | Testes de STORE e de ACK inválido. |
| 10 · Catálogo atómico, persistente e coerente com os ficheiros | Escrita temporária seguida de `ATOMIC_MOVE`, teste de rajada com reinício e `CatalogTest`. |
| 11 · Réplicas recebem o chunk cifrado e cabeçalho | Teste de rajada verifica os tipos de ficheiros e os bytes das três cópias. |
| 12 · Documentação de protocolo, arranque, concorrência e reflexão | Secções deste README. |

No Discord real, falta executar `/replica-token` com e sem o papel de
administrador, confirmar a visibilidade da resposta e executar `/estado`,
`/rajada n:50 intervalo-ms:0` e `/replica-remover id:r2` no canal
`#arquivo`. O serviço é exercitado localmente pelos testes acima.

[Enunciado oficial · Issue #22](https://github.com/cunhaestgv/SD-2026-2027/issues/22)
