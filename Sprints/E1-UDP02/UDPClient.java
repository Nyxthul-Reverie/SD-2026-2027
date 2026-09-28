import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class UDPClient {

    private static int automaticCounter = 0;

    public static void main(String args[]) {

        DatagramSocket aSocket = null;

        try {

            aSocket = new DatagramSocket();

            InetAddress aHost =
                    InetAddress.getByName("localhost");

            int serverPort = 6789;

            Scanner sc =
                    new Scanner(System.in);

            boolean running = true;

            while (running) {

                System.out.println(
                        "Escolha o modo: automatico (a), manual (m) ou sair (s):"
                );

                String option =
                        sc.nextLine().trim();

                switch (option.toLowerCase()) {

                    case "a":

                        runAutomaticMode(
                                aSocket,
                                aHost,
                                serverPort,
                                sc
                        );

                        break;

                    case "m":

                        runManualMode(
                                aSocket,
                                aHost,
                                serverPort,
                                sc
                        );

                        break;

                    case "s":

                        System.out.println(
                                "A sair..."
                        );

                        running = false;

                        break;

                    default:

                        System.out.println(
                                "Opcao invalida. Insira 'a', 'm' ou 's'."
                        );
                }
            }

            sc.close();

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

    private static void runAutomaticMode(
            DatagramSocket socket,
            InetAddress host,
            int serverPort,
            Scanner sc) throws IOException {

        System.out.println(
                "Insira a mensagem (ou 'sair' para voltar ao menu):"
        );

        String message =
                sc.nextLine();

        if (message.equalsIgnoreCase("sair")) {
            return;
        }

        /*
         * MODO AUTOMATICO
         *
         * O cliente atribui automaticamente:
         * 1, 2, 3, 4...
         */
        automaticCounter++;

        sendMessage(
                socket,
                host,
                serverPort,
                automaticCounter,
                message
        );
    }

    private static void runManualMode(
            DatagramSocket socket,
            InetAddress host,
            int serverPort,
            Scanner sc) throws IOException {

        System.out.println(
                "Insira a mensagem (ou 'sair' para voltar ao menu):"
        );

        String message =
                sc.nextLine();

        if (message.equalsIgnoreCase("sair")) {
            return;
        }

        Integer number = null;

        while (number == null) {

            System.out.println(
                    "Insira o numero da mensagem:"
            );

            String input =
                    sc.nextLine().trim();

            try {

                number =
                        Integer.parseInt(input);

            } catch (NumberFormatException e) {

                System.out.println(
                        "Numero invalido. Insira um numero inteiro."
                );
            }
        }

        sendMessage(
                socket,
                host,
                serverPort,
                number,
                message
        );
    }

    private static void sendMessage(
            DatagramSocket socket,
            InetAddress host,
            int serverPort,
            int number,
            String message) throws IOException {

        /*
         * Formato:
         *
         * N,mensagem
         *
         * Exemplo:
         * 3,mundo
         */
        String text =
                number + "," + message;

        byte[] data =
                text.getBytes(
                        StandardCharsets.UTF_8
                );

        DatagramPacket request =
                new DatagramPacket(
                        data,
                        data.length,
                        host,
                        serverPort
                );

        System.out.println(
                "Envio: " + text
        );

        socket.send(request);

        byte[] buffer =
                new byte[1000];

        DatagramPacket reply =
                new DatagramPacket(
                        buffer,
                        buffer.length
                );

        socket.receive(reply);

        String serverAnswer =
                new String(
                        reply.getData(),
                        0,
                        reply.getLength(),
                        StandardCharsets.UTF_8
                );

        /*
         * Caso o servidor ainda esteja à espera
         * de uma mensagem anterior.
         *
         * Exemplo:
         * waitingfor,2
         */
        if (serverAnswer.startsWith("waitingfor,")) {

            String[] parts =
                    serverAnswer.split(",", 2);

            if (parts.length == 2) {

                try {

                    int expectedNumber =
                            Integer.parseInt(
                                    parts[1]
                            );

                    System.out.println(
                            "Servidor esta a espera da mensagem numero: "
                                    + expectedNumber
                    );

                } catch (NumberFormatException e) {

                    System.out.println(
                            "Resposta do servidor malformada."
                    );
                }

            } else {

                System.out.println(
                        "Resposta do servidor malformada."
                );
            }

        } else if (
                serverAnswer.equals(
                        "malformed message"
                )) {

            System.out.println(
                    "Resposta: mensagem malformada."
            );

        } else {

            System.out.println(
                    "Resposta: " + serverAnswer
            );
        }

        System.out.println();
    }
}