# TPF Líder — Sprint E2-01

Aplicação Java que implementa a base do bot líder do arquivo distribuído, utilizando a biblioteca JDA para comunicar com o Discord.

Nesta sprint, os comandos são registados, os argumentos são validados e o bot devolve respostas provisórias. As funcionalidades reais de registo, cifra, replicação e recuperação ainda não estão implementadas.

## Pré-requisitos

- Java 17 ou superior
- Apache Maven
- Aplicação e bot criados no Discord Developer Portal
- Bot adicionado ao servidor de teste
- **Message Content Intent** ativado no Discord Developer Portal

## Configuração do Discord

O servidor deve possuir:

- um canal de texto chamado `#arquivo`;
- um papel chamado `admin-arquivo`.

O papel `admin-arquivo` deve ser atribuído aos membros autorizados a executar os comandos administrativos.

## Configuração das variáveis

Na raiz do projeto, criar um ficheiro `.env`:

```env
DISCORD_TOKEN=TOKEN_DO_BOT
DISCORD_GUILD_ID=ID_DO_SERVIDOR
```

Substituir os valores pelos dados reais do bot e do servidor.

> O ficheiro `.env` contém informação sensível e não deve ser enviado para o repositório. Está incluído no `.gitignore`.

Para obter o ID do servidor:

1. Ativar o **Modo de programador** nas definições avançadas do Discord.
2. Clicar com o botão direito no servidor.
3. Selecionar **Copiar ID do servidor**.

## Compilar

Na raiz do projeto, executar:

```bash
mvn clean compile
```

## Executar

```bash
mvn exec:java -Dexec.mainClass=tpf.LeaderBot
```

O bot permanece online enquanto a aplicação Java estiver em execução. Para terminar, utilizar `Ctrl+C`.

## Comandos disponíveis

### Comandos gerais

- `/estado`
- `/registar texto:<texto>`
- `/recuperar de:<id> ate:<id>`

### Comandos administrativos

Os seguintes comandos exigem o papel `admin-arquivo`:

- `/config r:<número>`
- `/replica-token`
- `/replica-remover id:<identificador>`
- `/rajada n:<número> intervalo-ms:<milissegundos>`

## Validações

A aplicação valida, entre outros, os seguintes casos:

- `texto` não pode estar vazio;
- `r` tem de ser maior que zero;
- `de` e `ate` não podem ser negativos;
- `de` tem de ser menor ou igual a `ate`;
- `id` não pode estar vazio;
- `n` tem de ser maior que zero;
- `intervalo-ms` não pode ser negativo;
- comandos administrativos são recusados a membros sem o papel `admin-arquivo`.

## Canal `#arquivo`

O bot deteta mensagens enviadas no canal `#arquivo` e devolve uma resposta provisória.

São ignoradas:

- mensagens enviadas por bots;
- mensagens privadas;
- mensagens enviadas noutros canais.

## Estado atual

Esta sprint implementa apenas a base e a interface do líder.

Ainda não estão implementadas as funcionalidades reais de:

- registo no arquivo;
- cifra;
- gestão de réplicas;
- geração real de tokens;
- recuperação de registos;
- execução de rajadas.