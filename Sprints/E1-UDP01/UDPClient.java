import java.net.*;
import java.io.*;
import java.util.Scanner;

public class UDPClient {

	public static void main(String args[]) {
		DatagramSocket aSocket = null;
		try {
			aSocket = new DatagramSocket();

			// byte[] m = "vou enviar esta mensagem ao servidor".getBytes();

			InetAddress aHost = InetAddress.getByName("localhost");
			int serverPort = 6789;
			int count = 0;
			Scanner sc = new Scanner(System.in);

			while (true) {
				System.out.println("Digite uma mensagem para enviar ao servidor (ou 'sair' para encerrar):");
				String teclado = sc.nextLine();

				if (teclado.equalsIgnoreCase("sair")) {
					System.out.println("A sair...");
					sc.close();
					break;
				}

				String resposta;

				while (true) {
					System.out.println("Pretende usar o modo de numeração automático? (s/n)");
					resposta = sc.nextLine();

					if (resposta.equalsIgnoreCase("s") || resposta.equalsIgnoreCase("n")) {
						break;
					}

					System.out.println("Resposta inválida. Insira 's' ou 'n'.");
				}

				if (resposta.equalsIgnoreCase("n")) {
					while (true) {
						System.out.println("Insira o número da mensagem:");
						String numero = sc.nextLine();

						try {
							count = Integer.parseInt(numero);
							break;
						} catch (NumberFormatException e) {
							System.out.println("Número inválido. Insira um número inteiro.");
						}
					}
				} else {
					count++;
				}

				byte[] m = (count + "," + teclado).getBytes();

				DatagramPacket request = new DatagramPacket(m, m.length, aHost, serverPort);

				aSocket.send(request);

				byte[] buffer = new byte[1000];

				DatagramPacket reply = new DatagramPacket(buffer, buffer.length);

				aSocket.receive(reply);

				String serverAnswer = new String(
						reply.getData(), 0, reply.getLength());

				if (serverAnswer.startsWith("waitingfor,")) {
					String[] parts = serverAnswer.split(",", 2);
					if (parts.length == 2) {
						try {
							int expectedNumber = Integer.parseInt(parts[1]);
							System.out.println("Servidor está à espera da mensagem número: " + expectedNumber);
						} catch (NumberFormatException e) {
							System.out.println("Resposta do servidor malformada.");
						}
					} else {
						System.out.println("Resposta do servidor malformada.");
					}
				} else if (serverAnswer.equals("malformed message")) {
					System.out.println("Reply: mensagem malformada.");
				} else {
					System.out.println("Reply: " + serverAnswer);
				}
			}

		} catch (SocketException e) {
			System.out.println("Socket: " + e.getMessage());
		} catch (IOException e) {
			System.out.println("IO: " + e.getMessage());
		} finally {
			if (aSocket != null)
				aSocket.close();
		}
	}
}