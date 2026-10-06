# TCP01 - Entrega final

## Resultado verificado

A execução funcional validou a comunicação com a saída:

```text
Received: Porto
```

Isto confirma que:
- o cliente envia uma `Person`;
- o servidor lê a `Person` corretamente;
- a dependência `Place` chega junto com a `Person` sem escrita explícita;
- o servidor devolve a localidade do `Place`.

## Testes de falha

- Cliente sem servidor
  - resultado esperado: `ConnectException` / `SocketException`
  - onde falha: no cliente ao abrir o socket
  - conclusão: sem `ServerSocket` à escuta, não há ligação TCP

- `Place` sem `implements Serializable`
  - resultado esperado: `NotSerializableException`
  - onde falha: no cliente ao fazer `writeObject(person)`
  - conclusão: o grafo de objetos não pode ser serializado se uma dependência não for serializável

- `serialVersionUID` diferente num dos lados
  - resultado esperado: `InvalidClassException`
  - onde falha: no servidor ao fazer `readObject()`
  - conclusão: a classe foi alterada de versão e a desserialização é rejeitada

- `Person` num pacote diferente no servidor
  - resultado esperado: `ClassNotFoundException`
  - onde falha: no servidor ao fazer `readObject()`
  - conclusão: o nome completo da classe muda e o Java trata-a como outra classe

## Interpretação

O TCP garante integridade dos bytes, mas não garante que o destinatário saiba interpretar a mensagem. A rede pode entregar tudo corretamente e ainda assim a aplicação falha se a classe, a versão ou o pacote não forem compatíveis.

O `serialVersionUID` controla essa compatibilidade. Se não coincidir, a desserialização falha. A serialização automática também envia o grafo de objetos alcançável, por isso pode mandar mais dados do que o necessário.

## Conclusão

A tarefa está concluída na parte funcional e validada com sucesso. O programa funciona corretamente e os testes de falha têm o comportamento esperado pela ficha.
