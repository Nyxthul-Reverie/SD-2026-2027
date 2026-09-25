# E1-UDP02

## 4.1 Estruturas de dados

Na solução UDP01, para a sequência `1, 3, 4, 5, 2`, as mensagens `3`, `4`
e `5` são descartadas por terem chegado antes da mensagem `2`. Para completar
a sequência, essas três mensagens têm de ser retransmitidas. Na UDP02 não são
necessárias essas retransmissões, porque as mensagens adiantadas ficam guardadas
temporariamente até poderem ser entregues em ordem.

O servidor utiliza duas estruturas:

- `ArrayList<String>` para a lista de receção, porque a operação principal é
  acrescentar ao fim as mensagens entregues em ordem;
- `HashMap<Integer, String>` para as mensagens temporárias, porque permite
  procurar diretamente uma mensagem através do seu número.

Uma mensagem recebida já chegou ao servidor. Uma mensagem entregue já pôde ser
passada à aplicação na ordem correta. Assim, uma mensagem pode ter sido recebida
e continuar temporariamente guardada sem ter sido entregue.

## 4.3 Verificação

### Sequência normal

| Mensagem enviada | Resposta recebida | L após processamento | Estrutura temporária | Mensagens entregues neste passo |
| --- | --- | ---: | --- | --- |
| `1,ola` | `1,ola` | 1 | `{}` | `[1,ola]` |
| `2,cruel` | `2,cruel` | 2 | `{}` | `[2,cruel]` |
| `3,mundo` | `3,mundo` | 3 | `{}` | `[3,mundo]` |

### Sequência com desordenação múltipla

| Mensagem enviada | Resposta recebida | L após processamento | Estrutura temporária | Mensagens entregues neste passo | Justificação |
| --- | --- | ---: | --- | --- | --- |
| `1,ola` | `1,ola` | 1 | `{}` | `[1,ola]` | É a primeira mensagem esperada (`1 = L + 1`), por isso é entregue imediatamente e `L` passa para 1. |
| `3,mundo` | `waitingfor,2` | 1 | `{3=mundo}` | `[]` | A próxima mensagem esperada era a 2. A mensagem 3 chega fora de ordem, fica guardada temporariamente e `L` mantém-se em 1. |
| `4,tudo bem` | `waitingfor,2` | 1 | `{3=mundo, 4=tudo bem}` | `[]` | Continua a faltar a mensagem 2, por isso a mensagem 4 também fica guardada temporariamente e `L` mantém-se em 1. |
| `2,cruel` | `2,cruel` | 4 | `{}` | `[2,cruel, 3,mundo, 4,tudo bem]` | É a mensagem esperada. A sua entrega permite entregar em cascata as mensagens 3 e 4 que estavam guardadas, fazendo `L` passar para 4. |
| `3,mundo` | `waitingfor,5` | 4 | `{3=mundo}` | `[]` | É um duplicado de uma mensagem já entregue. Como `3 != L + 1`, fica na estrutura temporária e o servidor responde `waitingfor,5`. |

No final desta sequência:

- lista de receção: `[1,ola, 2,cruel, 3,mundo, 4,tudo bem]`;
- estrutura temporária: `{3=mundo}`.

O último elemento da estrutura temporária é o duplicado de uma mensagem já
entregue. Este resultado corresponde à limitação que deve ser analisada na
reflexão crítica.

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
Como a solução atual não tem timeout nem mecanismo de expiração, podem ficar
guardadas indefinidamente. Uma possível melhoria seria definir um limite de
tempo ou uma política para remover mensagens demasiado antigas.

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
mensagem ainda temporariamente guardada e indicar a verificação em falta.

**Resposta do estudante:** a solução não trata completamente os duplicados. Se
uma mensagem for repetida enquanto ainda está na estrutura temporária, o mesmo
número utiliza a mesma chave no `HashMap` e o valor anterior é substituído. Se
a mensagem já tiver sido entregue, como aconteceu com o segundo `3,mundo`, é
considerada fora de ordem e volta a ser guardada temporariamente. Faltaria uma
verificação para ignorar mensagens com número menor ou igual a `L`.

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
guardadas até poderem ser entregues por ordem. A duplicação continua apenas
parcialmente tratada. A perda definitiva não é resolvida, porque, se uma
mensagem nunca chegar, o servidor fica à espera. A corrupção também não é
resolvida por este mecanismo, embora o programa valide o formato das mensagens.
