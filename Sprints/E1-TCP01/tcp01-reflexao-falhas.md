# TCP01 - Testes de falhas e reflexão

## Resultado verificado

A execução do cliente devolveu a mensagem:

```text
Received: Porto
```

Isto confirma que a serialização da `Person` com a dependência `Place` está funcional: o cliente envia um objeto, o servidor lê esse objeto e devolve a localidade do `Place`.

## 1. Testes de falha

| Caso | Exceção esperada | Onde acontece | Conclusão |
| --- | --- | --- | --- |
| Cliente sem servidor | `ConnectException` / `SocketException` | No cliente ao abrir o socket | Sem `ServerSocket` à escuta, não há ligação TCP |
| `Place` sem `implements Serializable` | `NotSerializableException` | No cliente ao fazer `writeObject(person)` | O grafo de objetos não pode ser serializado se uma dependência não for serializável |
| `serialVersionUID` diferente | `InvalidClassException` | No servidor ao fazer `readObject()` | A classe mudou de versão e a desserialização é rejeitada |
| `Person` em pacote diferente | `ClassNotFoundException` | No servidor ao fazer `readObject()` | O nome completo da classe mudou e o Java trata-a como outra classe |

## 2. Interpretação

O TCP garante integridade dos bytes, mas não garante que o destinatário saiba interpretar a mensagem. A rede pode entregar tudo corretamente e ainda assim a aplicação falhar se a classe, a versão ou o pacote não forem compatíveis.

O `serialVersionUID` controla essa compatibilidade. Se não coincidir, a desserialização falha. A serialização automática também envia o grafo de objetos alcançável, por isso pode mandar mais dados do que o necessário.

## 3. Reflexão crítica

- O TCP garante entrega correta, mas não garante compreensão do conteúdo.
- O `serialVersionUID` é essencial para controlar compatibilidade entre versões.
- A serialização automática pode enviar mais informação do que o necessário.
- Uma thread por ligação permite atender vários clientes em paralelo, com maior custo em recursos.

## 4. Conclusão

A parte principal da ficha foi validada com sucesso: a comunicação por objetos funciona corretamente, a dependência `Place` foi enviada junto com a `Person`, e os erros de serialização e de ligação foram interpretados corretamente.
