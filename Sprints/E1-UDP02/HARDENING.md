# Consulta ao server (status)

O client envia o datagrama `status` e o server responde apenas com o próximo
número esperado `L + 1`. Esta consulta não altera `L`, as listas de
mensagens ou a estrutura temporária. Também não aparece como uma mensagem
numerada no registo de entregas do server.

O client consulta o estado ao iniciar e sempre que se escolhe o modo
automático. A consulta é silenciosa e o número recebido é usado no envio
automático. Não calcula o próximo número a partir do echo, pois esse envio
pode ter desencadeado uma entrega em cascata.
