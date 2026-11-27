# TPF Líder · Sprint E2-02

Bot Java 17+ que utiliza a JDA para registar mensagens e produzir chunks cifrados
no disco local. Requer Maven, um bot convidado para o servidor de teste, o intent
**Message Content**, o canal `#arquivo` e o papel `admin-arquivo` para administradores.

Na pasta do `pom.xml`, configura `DISCORD_TOKEN` e `DISCORD_GUILD_ID` nas variáveis
de ambiente ou num ficheiro `.env`. Compila e arranca assim.

```powershell
mvn clean package
java -jar target/tpf-lider-1.0-SNAPSHOT.jar
```

O caminho de dados por omissão é `dados-lider`. Em Windows, se o projeto estiver
num volume sem ACL, especifica uma pasta privada em NTFS, por exemplo assim.

```powershell
java "-Dtpf.dados=$env:LOCALAPPDATA\SDT-E2-02" -jar target/tpf-lider-1.0-SNAPSHOT.jar
```

Mantém uma única instância do líder por pasta de dados. Em cada reinício usa a
mesma pasta e conserva a chave original. Podes ajustar os valores de selagem com
`-Dtpf.chunkMax=65536`, `-Dtpf.pad=4096` e `-Dtpf.selagem=5000`
antes de `-jar`.

## Escrita

- `/registar texto:<texto>` e mensagens humanas em `#arquivo` escrevem em
  `registos.log`. Cada linha é `lsn;instante;autor;canal;textoBase64`, em UTF-8
  com LF. O instante é Unix em milissegundos. Autor e canal são IDs Discord.
- A confirmação com LSN segue `FileChannel.force(true)`. O LSN seguinte parte do
  último registo completo e cresce a partir de 1.
- `/rajada n:<n> intervalo-ms:<ms>` (papel `admin-arquivo`) injeta registos
  sintéticos pelo mesmo caminho e indica quando aceitou o pedido. Acompanhe
  `eventos-lider.txt` para ver as escritas e selagens.
- `/estado`, `/config`, `/recuperar`, `/replica-token` e `/replica-remover`
  dão respostas provisórias.

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

## Testes

```powershell
mvn test
```
