# TCP01 - Card 10

## Estado verificado

A execução funcional do cliente devolveu:

```text
Received: Porto
```

Isto confirma que a versão base da aplicação funciona: a `Person` e a dependência `Place` são serializados corretamente e o servidor consegue desserializá-los.

## 1. O que se testa

O ponto central é o campo:

```java
private static final long serialVersionUID = 1L;
```

Se os lados não tiverem o mesmo valor, a desserialização falha.

## 2. Caso de sucesso

A versão correta do código tem:

```java
public class Person implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private int year;
    private Place place;
}
```

Resultado esperado:
- o cliente envia a `Person`;
- o servidor recebe a `Person`;
- o `Place` chega automaticamente;
- a resposta devolvida é a localidade do `Place`.

## 3. Caso de incompatibilidade

Se alterar o `serialVersionUID` num dos lados para `2L`, a leitura falha com:

```text
InvalidClassException
```

Isto acontece no lado que faz `readObject()`, normalmente no servidor. Prova que o Java valida também a compatibilidade de versão da classe.

## 4. Conclusão

O card 10 está concluído quando se confirma que:
- a versão compatível funciona;
- a versão incompatível falha com `InvalidClassException`;
- o erro aparece no lado que lê o objeto;
- a causa é a incompatibilidade da classe serializável.
