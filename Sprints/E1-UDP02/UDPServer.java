import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

public class UDPServer {

    private static final ArrayList<String> receivedMessages = new ArrayList<>();
    private static final HashMap<Integer, String> temporaryMessages = new HashMap<>();
    private static final ArrayList<String> deliveredThisStep = new ArrayList<>();

    /**
     * Processes delivered messages
     * @return the last message processed in order
     */
    public static int processDeliveredMessages(
            int nLastMessageInOrder,
            int nCurrentMessage,
            String currentMessage) {

        deliveredThisStep.clear();

        // CASO 1:
        // A mensagem recebida é exatamente a próxima esperada.
        if (nCurrentMessage == nLastMessageInOrder + 1) {

            String deliveredMessage =
                    nCurrentMessage + "," + currentMessage;

            receivedMessages.add(deliveredMessage);
            deliveredThisStep.add(deliveredMessage);

            nLastMessageInOrder = nCurrentMessage;

            /*
             * Depois de entregar a mensagem atual,
             * verificamos se já existem mensagens seguintes
             * guardadas temporariamente.
             *
             * Isto permite a entrega em cascata.
             */
            while (temporaryMessages.containsKey(nLastMessageInOrder + 1)) {

                int nextMessageNumber = nLastMessageInOrder + 1;

                String nextMessage =
                        temporaryMessages.remove(nextMessageNumber);

                deliveredMessage =
                        nextMessageNumber + "," + nextMessage;

                receivedMessages.add(deliveredMessage);
                deliveredThisStep.add(deliveredMessage);

                nLastMessageInOrder = nextMessageNumber;
            }

        }

        // CASO 2:
        // A mensagem chegou adiantada.
        else if (nCurrentMessage > nLastMessageInOrder + 1) {

            /*
             * putIfAbsent evita substituir uma mensagem
             * temporária caso o mesmo número chegue novamente.
             */
            temporaryMessages.putIfAbsent(
                    nCurrentMessage,
                    currentMessage
            );
        }

        // CASO 3:
        // nCurrentMessage <= L
        // A mensagem já foi entregue anteriormente.
        else {

            System.out.println(
                    "Mensagem duplicada/antiga ignorada: "
                            + nCurrentMessage + "," + currentMessage
            );
        }

        return nLastMessageInOrder;
    }

    public static void main(String args[]) {

        DatagramSocket aSocket = null;

        // L = número da última mensagem entregue em ordem.
        int L = 0;

        try {

            aSocket = new DatagramSocket(6789);

            byte[] buffer = new byte[1000];

            System.out.println(
                    "Servidor UDP iniciado na porta 6789."
            );

            System.out.println(
                    "Estado inicial: L = 0"
            );

            System.out.println();

            while (true) {

                DatagramPacket request =
                        new DatagramPacket(
                                buffer,
                                buffer.length
                        );

                aSocket.receive(request);

                /*
                 * Limpa as mensagens entregues no passo anterior.
                 * Se a mensagem atual for válida,
                 * processDeliveredMessages também fará clear().
                 */
                deliveredThisStep.clear();

                String received =
                        new String(
                                request.getData(),
                                0,
                                request.getLength(),
                                StandardCharsets.UTF_8
                        );

                String[] parts =
                        received.split(",", 2);

                /*
                 * Validação da mensagem.
                 *
                 * Formato esperado:
                 *
                 * N,mensagem
                 */
                if (parts.length != 2
                        || parts[0].trim().isEmpty()
                        || parts[1].trim().isEmpty()) {

                    String response = "malformed message";

                    sendReply(
                            aSocket,
                            request,
                            response
                    );

                    System.out.println(
                            "---------------------------------------"
                    );

                    System.out.println(
                            "Recebido: " + received
                    );

                    System.out.println(
                            "Mensagem malformada; estado preservado."
                    );

                    System.out.println(
                            "Resposta: " + response
                    );

                    System.out.println(
                            "L: " + L
                    );

                    System.out.println(
                            "Temporarias: " + temporaryMessages
                    );

                    System.out.println(
                            "Entregues neste passo: "
                                    + deliveredThisStep
                    );

                    System.out.println(
                            "Lista de rececao: "
                                    + receivedMessages
                    );

                    System.out.println(
                            "---------------------------------------"
                    );

                    System.out.println();

                    continue;
                }

                int N;

                try {

                    N = Integer.parseInt(
                            parts[0].trim()
                    );

                } catch (NumberFormatException e) {

                    String response = "malformed message";

                    sendReply(
                            aSocket,
                            request,
                            response
                    );

                    System.out.println(
                            "---------------------------------------"
                    );

                    System.out.println(
                            "Recebido: " + received
                    );

                    System.out.println(
                            "Mensagem malformada; estado preservado."
                    );

                    System.out.println(
                            "Resposta: " + response
                    );

                    System.out.println(
                            "L: " + L
                    );

                    System.out.println(
                            "Temporarias: "
                                    + temporaryMessages
                    );

                    System.out.println(
                            "Entregues neste passo: "
                                    + deliveredThisStep
                    );

                    System.out.println(
                            "Lista de rececao: "
                                    + receivedMessages
                    );

                    System.out.println(
                            "---------------------------------------"
                    );

                    System.out.println();

                    continue;
                }

                /*
                 * Guardamos o valor antigo de L
                 * para perceber se a mensagem atual
                 * foi entregue ou não.
                 */
                int oldL = L;

                L = processDeliveredMessages(
                        L,
                        N,
                        parts[1]
                );

                String response;

                /*
                 * Se L não mudou, a mensagem atual
                 * não permitiu avançar a sequência.
                 */
                if (L == oldL) {

                    response =
                            "waitingfor," + (L + 1);

                } else {

                    // Echo da mensagem recebida.
                    response = received;
                }

                sendReply(
                        aSocket,
                        request,
                        response
                );

                /*
                 * Informação explícita para demonstração.
                 */
                System.out.println(
                        "---------------------------------------"
                );

                System.out.println(
                        "Recebido: " + received
                );

                System.out.println(
                        "Resposta: " + response
                );

                System.out.println(
                        "L: " + L
                );

                System.out.println(
                        "Temporarias: "
                                + temporaryMessages
                );

                System.out.println(
                        "Entregues neste passo: "
                                + deliveredThisStep
                );

                System.out.println(
                        "Lista de rececao: "
                                + receivedMessages
                );

                System.out.println(
                        "---------------------------------------"
                );

                System.out.println();
            }

        } catch (SocketException e) {

            System.out.println(
                    "Socket: " + e.getMessage()
            );

        } catch (IOException e) {

            System.out.println(
                    "IO: " + e.getMessage()
            );

        } finally {

            if (aSocket != null) {
                aSocket.close();
            }
        }
    }

    private static void sendReply(
            DatagramSocket socket,
            DatagramPacket request,
            String message) throws IOException {

        byte[] data =
                message.getBytes(
                        StandardCharsets.UTF_8
                );

        DatagramPacket reply =
                new DatagramPacket(
                        data,
                        data.length,
                        request.getAddress(),
                        request.getPort()
                );

        socket.send(reply);
    }
}