# E1-UDP02

## 4.1 Estruturas de dados

Na solução UDP01, para a sequência `1, 3, 4, 5, 2`, as mensagens `3`, `4`
e `5` são descartadas por terem chegado antes da mensagem `2`. Para completar
a sequência, essas três mensagens têm de ser retransmitidas. Na UDP02 não são
necessárias essas retransmissões, porque as mensagens adiantadas ficam guardadas
temporariamente até poderem ser entregues em ordem.

O servidor utiliza duas estruturas persistentes:

- `ArrayList<String> receivedMessages` para a lista de receção, porque a
  operação principal é acrescentar ao fim as mensagens entregues em ordem;
- `HashMap<Integer, String> temporaryMessages` para as mensagens temporárias,
  porque permite procurar diretamente uma mensagem através do seu número.

O servidor utiliza ainda `ArrayList<String> deliveredThisStep` como estrutura
auxiliar. Esta lista é limpa antes do processamento de cada datagrama normal e
contém apenas as mensagens entregues nesse passo. Não representa o estado
persistente da lista de receção.

Uma mensagem recebida já chegou ao servidor. Uma mensagem entregue já pôde ser
passada à aplicação na ordem correta. Assim, uma mensagem pode ter sido recebida
e continuar temporariamente guardada sem ter sido entregue.

A ordem de apresentação dos elementos de um `HashMap` não é garantida. Nos
exemplos seguintes interessa o conteúdo da estrutura, não a ordem visual das
suas entradas.

## 4.2 Processamento das mensagens

O método `processDeliveredMessages` mantém `L` como o número da última mensagem
entregue em ordem e trata três casos:

- se `N == L + 1`, a mensagem é entregue, adicionada a `receivedMessages` e a
  `deliveredThisStep`, e `L` é atualizado. Em seguida, são procuradas e
  entregues em cascata as mensagens consecutivas existentes em
  `temporaryMessages`. Cada mensagem entregue na cascata é removida da
  estrutura temporária;
- se `N > L + 1`, a mensagem chegou adiantada e é guardada em
  `temporaryMessages`. O método usa `putIfAbsent`, pelo que uma mensagem com o
  mesmo número não substitui a primeira que ficou guardada;
- se `N <= L`, a mensagem é duplicada ou antiga e é ignorada, sem alterar as
  estruturas persistentes.

O método devolve o valor final de `L`, incluindo qualquer avanço provocado pela
entrega em cascata.

## 4.3 Verificação

### Sequência normal

| Mensagem enviada | Resposta recebida | L após processamento | Estrutura temporária | Mensagens entregues neste passo |
| --- | --- | ---: | --- | --- |
| `1,ola` | `1,ola` | 1 | `{}` | `[1,ola]` |
| `2,cruel` | `2,cruel` | 2 | `{}` | `[2,cruel]` |
| `3,mundo` | `3,mundo` | 3 | `{}` | `[3,mundo]` |

No final, a lista de receção é
`[1,ola, 2,cruel, 3,mundo]` e a estrutura temporária está vazia.

### Sequência com desordenação múltipla

| Mensagem enviada | Resposta recebida | L após processamento | Estrutura temporária | Mensagens entregues neste passo | Justificação |
| --- | --- | ---: | --- | --- | --- |
| `1,ola` | `1,ola` | 1 | `{}` | `[1,ola]` | É a primeira mensagem esperada (`1 = L + 1`), por isso é entregue imediatamente e `L` passa para 1. |
| `3,mundo` | `waitingfor,2` | 1 | `{3=mundo}` | `[]` | A próxima mensagem esperada era a 2. A mensagem 3 chega fora de ordem, fica guardada temporariamente e `L` mantém-se em 1. |
| `4,tudo bem` | `waitingfor,2` | 1 | `{3=mundo, 4=tudo bem}` | `[]` | Continua a faltar a mensagem 2, por isso a mensagem 4 também fica guardada temporariamente e `L` mantém-se em 1. |
| `2,cruel` | `2,cruel` | 4 | `{}` | `[2,cruel, 3,mundo, 4,tudo bem]` | É a mensagem esperada. A sua entrega permite entregar em cascata as mensagens 3 e 4 que estavam guardadas, fazendo `L` passar para 4. |
| `3,mundo` | `waitingfor,5` | 4 | `{}` | `[]` | É um duplicado de uma mensagem já entregue. Como `3 <= L`, é ignorado e o servidor responde que espera a mensagem 5. |

No final desta sequência:

- lista de receção: `[1,ola, 2,cruel, 3,mundo, 4,tudo bem]`;
- estrutura temporária: `{}`.

O último `3,mundo` não é novamente entregue nem guardado, porque o código atual
ignora mensagens cujo número seja menor ou igual a `L`.

## 4.4 Reflexão crítica

### Comparação das retransmissões

Para a sequência `1, 3, 4, 5, 6, 2`, comparar o número de retransmissões na
UDP01 e na UDP02 e justificar a diferença.

**Resposta do estudante:** na UDP01 são necessárias quatro retransmissões: as
mensagens `3`, `4`, `5` e `6` chegam fora de ordem e são descartadas, tendo de
ser reenviadas depois da mensagem `2`. Na UDP02 são necessárias zero
retransmissões, porque essas mensagens ficam guardadas na estrutura temporária
e, quando chega a mensagem `2`, são entregues em cascata. A diferença deve-se
ao facto de a UDP02 aproveitar mensagens que já foram recebidas, enquanto a
UDP01 as descartava.

### Mensagem em falta

Explicar o que acontece à estrutura temporária se a mensagem em falta nunca
chegar e indicar o mecanismo que seria necessário.

**Resposta do estudante:** se uma mensagem em falta nunca chegar, as mensagens
posteriores ficam guardadas na estrutura temporária e não podem ser entregues.
Como a solução atual não tem timeout nem mecanismo de expiração no servidor,
podem ficar guardadas indefinidamente. Uma possível melhoria seria definir um
limite de tempo ou uma política para remover mensagens demasiado antigas.

### Crescimento da estrutura temporária

Explicar a consequência de receber a mensagem número `1000000` e como limitar
esse risco.

**Resposta do estudante:** se um cliente enviar números muito à frente, como
`1000000`, essas mensagens podem ficar guardadas durante muito tempo à espera
das anteriores. Isso pode fazer a estrutura temporária crescer e consumir
memória. Poderia ser imposto um limite máximo de distância em relação a `L` ou
um limite de mensagens temporárias.

### Duplicados

Distinguir um duplicado de uma mensagem já entregue de um duplicado de uma
mensagem ainda temporariamente guardada.

**Resposta do estudante:** se uma mensagem já entregue voltar a chegar, o seu
número será menor ou igual a `L` e a mensagem será ignorada. Não volta a entrar
em `temporaryMessages` e não é novamente acrescentada a `receivedMessages`.

Se uma mensagem for repetida enquanto ainda está na estrutura temporária,
`putIfAbsent` mantém o primeiro conteúdo associado ao número e não o substitui
pelo conteúdo do duplicado. A solução não verifica se os dois conteúdos são
iguais nem sinaliza ao cliente esse conflito; limita-se a preservar o primeiro.

### Múltiplos clientes

O servidor mantém um único `L`, uma única lista de receção e uma única estrutura
temporária. Por isso, com vários clientes, as sequências interferem entre si.
Para suportar vários clientes seria necessário manter um estado separado para
cada cliente, por exemplo identificado pelo endereço e porta de origem.

### Limites do mecanismo

Indicar quais das características — corrupção, duplicação, perda e
desordenação — são resolvidas pelo mecanismo e quais continuam por resolver.

**Resposta do estudante:** a desordenação é melhor tratada nesta solução,
porque as mensagens adiantadas deixam de ser descartadas e passam a ser
guardadas até poderem ser entregues por ordem. Os duplicados já entregues são
ignorados e um duplicado temporário não substitui o primeiro conteúdo guardado,
embora conteúdos diferentes associados ao mesmo número não sejam comparados.
A perda definitiva não é resolvida, porque, se uma mensagem nunca chegar, o
servidor fica à espera. A corrupção também não é resolvida em termos de
integridade do conteúdo, embora o programa valide o tamanho, a codificação
UTF-8 e o formato das mensagens.

## Funcionalidades adicionais de hardening

As funcionalidades desta secção reforçam a robustez e a utilização da solução,
mas não alteram a lógica principal de ordenação da UDP02.

### Consulta `status` e modo automático

O cliente envia o datagrama especial `status` e o servidor responde apenas com
o próximo número esperado, ou seja, `L + 1`. A consulta não chama
`processDeliveredMessages` e não altera `L`, `receivedMessages`,
`temporaryMessages` ou `deliveredThisStep`.

O cliente consulta o estado ao iniciar e antes de cada envio automático. No
modo automático, usa a resposta como número da nova mensagem e pede ao
utilizador apenas o conteúdo. Não existe um `automaticCounter` local. Esta
consulta é necessária porque um envio manual ou uma entrega em cascata pode
alterar o próximo número esperado pelo servidor.

O modo manual continua a permitir escolher explicitamente o número da mensagem,
possibilitando testar sequências desordenadas como `1, 3, 4, 2, 3`.

### Mensagens malformadas

O servidor responde `malformed message` quando recebe:

- um datagrama sem o formato `N,mensagem`;
- um valor de `N` não numérico ou menor que 1;
- uma mensagem vazia ou composta apenas por espaços;
- dados que não formem uma sequência UTF-8 válida;
- um datagrama com mais de 1000 bytes.

Nestes casos, `L`, `receivedMessages` e `temporaryMessages` são preservados,
`deliveredThisStep` fica vazio e o servidor continua ativo.
