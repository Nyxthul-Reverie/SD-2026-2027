# E1-UDP01

## 4.3 Verificação

Estado inicial em cada cenário: `L = 0`. As respostas correspondem ao conteúdo dos datagramas; os valores de `L` resultam da regra implementada no servidor.

### Sequência normal

| Mensagem enviada | Resposta recebida | L após processamento | Justificação                             |
| ---------------- | ----------------- | -------------------: | ---------------------------------------- |
| `1,ola`          | `1,ola`           |                    1 | A mensagem é aceite porque `1 == 0 + 1`. |
| `2,cruel`        | `2,cruel`         |                    2 | A mensagem é aceite porque `2 == 1 + 1`. |
| `3,mundo`        | `3,mundo`         |                    3 | A mensagem é aceite porque `3 == 2 + 1`. |

### Sequência fora de ordem

| Mensagem enviada | Resposta recebida | L após processamento | Justificação                                                                       |
| ---------------- | ----------------- | -------------------: | ---------------------------------------------------------------------------------- |
| `1,ola`          | `1,ola`           |                    1 | Chegou o primeiro número esperado.                                                 |
| `3,mundo`        | `waitingfor,2`    |                    1 | O servidor esperava 2, logo, mensagem 3 não é aceite nem guardada e `L` mantém-se. |
| `2,cruel`        | `2,cruel`         |                    2 | Chegou a mensagem em falta (2), que é aceite.                                      |
| `3,mundo`        | `3,mundo`         |                    3 | Agora o número 3 já era o esperado.                                                |

## 4.4 Reflexão crítica

### 1. O mecanismo implementado deteta mensagens duplicadas? E mensagens perdidas? Justifique.

Uma mensagem cujo número já foi aceite não volta a ser aceite, porque não corresponde a `L + 1`. No entanto, o mecanismo não distingue explicitamente uma mensagem duplicada de outras mensagens fora de ordem: responde sempre com `waitingfor` e o número esperado.

Quando chega um número superior ao esperado, o servidor identifica uma lacuna, mas não consegue determinar se a mensagem em falta foi perdida ou está apenas atrasada. Se não chegarem mais mensagens, não deteta a perda por si só. Também não existem temporizadores nem retransmissão automática; se uma mensagem ou resposta se perder, o cliente pode ficar à espera indefinidamente.

### 2. O que acontece se dois clientes usarem o servidor em simultâneo? Que alteração seria necessária?

O servidor mantém um único `L`, partilhado por todos os clientes. Se um cliente enviar a mensagem 1 e esta for aceite, a mensagem 1 de outro cliente será rejeitada, porque o servidor já espera 2. Assim, as sequências dos clientes interferem entre si.

Seria necessário manter um `L` independente por cliente ou sessão, identificado, por exemplo, pelo endereço e porta de origem durante essa sessão. Também seria necessário definir como reiniciar esse estado quando um cliente começa uma nova sessão.

### 3. Das características do UDP corrupção, duplicação, perda e desordenação quais ficaram resolvidas e quais continuam por resolver?

| Característica | Resultado do mecanismo                                                                                                                                                   |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Desordenação   | Deteta números fora da sequência e só aceita mensagens em ordem. A recuperação depende do reenvio da mensagem rejeitada; não existe retenção nem reordenação automática. |
| Duplicação     | Impede que um número já aceite seja aceite novamente enquanto o estado se mantém, mas não identifica explicitamente o caso como duplicado.                               |
| Perda          | Não garante a recuperação de mensagens ou respostas perdidas. Não existem temporizadores nem retransmissão automática.                                                   |
| Corrupção      | Não acrescenta deteção ou correção própria de corrupção. A validação do formato não garante a integridade do conteúdo.                                                   |
