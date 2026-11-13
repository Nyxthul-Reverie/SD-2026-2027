# Validação da COMINDIR05

Executar a partir da pasta superior, depois de `compilar.ps1`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\testes\testar.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\testes\ensaios.ps1 -Suite all
```

`ProtocolTest.java` executa testes dirigidos sem Kubo: persistência antes das
mensagens, restart como seguidor, voto único/idempotente, maioria distinta,
restrição de histórico, demissão por termo superior, codecs, truncagem atómica
e monitor. Inclui recuperação com socket TCP local: recua pelos blocos de
termo, preserva o prefixo e rejeita uma resposta cujo termo mudou durante a
transferência. A reflexão permite testar esse método privado; não substitui
os ensaios com nove JVMs. Os ficheiros ficam num diretório unitário novo.

`Experiments.java` executa JVMs reais, produtor e processador contra o Kubo
isolado na API 5005. Precisa dos portos 7100–7108 livres. Só termina Process
que criou e guarda cada execução num diretório novo, sem limpar anteriores.
Os modos a–e seguem os cenários da ficha. b inclui dez arranques aleatórios e
cinco fixos com barreira; `fixed` repete apenas estes cinco. `all` dura vários
minutos, incluindo esperas reais de 30 e 60 segundos.

Os cenários corretos exigem PASS dos dois verificadores, igualdade integral
das três cópias depois de estabilizar e correspondência entre os envios
publicados e as linhas em produtor.txt. Não se usa o simples retorno zero
do processo como prova de ausência de perda.

`FaultTest.java` é um teste dirigido das classes mutadas de maioria e estado
volátil. `faults` acrescenta os cinco ensaios distribuídos, `networkfaults`
executa apenas estes. As mutações são cópias compiladas em execucoes/, sem
alterar os fontes oficiais. A conclusão depende da observação esperada:
há falhas que os verificadores de históricos não detetam. O cenário de
termo obsoleto reproduz explicitamente um cabeçalho antigo para assegurar
a entrega atrasada; o ficheiro replay-lote.txt documenta essa instrumentação.

Resultados reais e limitações: [relatório](../Relatorio.md),
[evidências](../Evidencias.md), [matriz](../Conformidade.md).
Não são testes formais de todos os interleavings, falhas de disco ou rede
entre máquinas. A avaliação da compreensão do autor permanece para a oral.
