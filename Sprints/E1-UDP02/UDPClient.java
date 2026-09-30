import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;

public class UDPClient {
	private static final int SERVER_PORT = Integer.getInteger("udp.port", 6789);
	private static final int MAX_DATAGRAM_BYTES = 1000;
	private static final int TIMEOUT_MS = 2000;
	private static int automaticCounter = 0;

	public static void main(String[] args) {
		if (SERVER_PORT < 1 || SERVER_PORT > 65535) {
			System.out.println("Porta inválida. Use uma porta entre 1 e 65535.");
			return;
		}
		try (Scanner sc = new Scanner(System.in)) {
			InetAddress host = InetAddress.getByName("localhost");
			while (true) {
				String option = readLine(sc, "Escolha o modo: automático (a), manual (m) ou sair (s):");
				if (option == null || option.trim().equalsIgnoreCase("s")
						|| option.trim().equalsIgnoreCase("sair")) {
					break;
				}
				try {
					switch (option.trim().toLowerCase(Locale.ROOT)) {
						case "a":
							runAutomaticMode(host, sc);
							break;
						case "m":
							runManualMode(host, sc);
							break;
						default:
							System.out.println("Opção inválida. Insira 'a', 'm' ou 's'.");
					}
				} catch (IOException e) {
					System.out.println("Falha de comunicação: " + e.getMessage());
				}
			}
			System.out.println("A sair...");
		} catch (IOException e) {
			System.out.println("Erro de entrada/saída: " + e.getMessage());
		}
	}

	private static String readLine(Scanner sc, String prompt) {
		System.out.println(prompt);
		if (!sc.hasNextLine()) {
			if (sc.ioException() != null) {
				System.out.println("Erro na leitura: " + sc.ioException().getMessage());
			}
			return null;
		}
		return sc.nextLine();
	}

	private static String readMessage(Scanner sc) {
		while (true) {
			String message = readLine(sc, "Insira a mensagem (ou 'sair' para voltar ao menu):");
			if (message == null || message.trim().equalsIgnoreCase("sair")) {
				return null;
			}
			if (message.isBlank()) {
				System.out.println("A mensagem não pode estar vazia.");
			} else if (message.getBytes(StandardCharsets.UTF_8).length > MAX_DATAGRAM_BYTES - 2) {
				System.out.println("Mensagem demasiado longa: a mensagem completa (N,mensagem) tem um limite de 1000 bytes UTF-8.");
			} else {
				return message;
			}
		}
	}

	private static void runAutomaticMode(InetAddress host, Scanner sc) throws IOException {
		String message = readMessage(sc);
		if (message == null) {
			return;
		}
		if (automaticCounter == Integer.MAX_VALUE) {
			System.out.println("Numeração esgotada. Não é possível enviar outra mensagem automática.");
			return;
		}
		int number = automaticCounter + 1;
		if (!fitsDatagram(number, message)) {
			return;
		}
		automaticCounter = number;
		sendMessage(host, number, message);
	}

	private static void runManualMode(InetAddress host, Scanner sc) throws IOException {
		String message = readMessage(sc);
		if (message == null) {
			return;
		}
		while (true) {
			String input = readLine(sc, "Insira o número da mensagem (ou 'sair' para cancelar):");
			if (input == null || input.trim().equalsIgnoreCase("sair")) {
				return;
			}
			int number;
			try {
				number = Integer.parseInt(input.trim());
				if (number < 1) {
					throw new NumberFormatException();
				}
			} catch (NumberFormatException e) {
				System.out.println("Número inválido. Introduza um número entre 1 e 2147483647.");
				continue;
			}
			sendMessage(host, number, message);
			return;
		}
	}

	private static long parseNextNumber(String text) throws IOException {
		try {
			long number = Long.parseLong(text);
			if (number < 1 || number > (long) Integer.MAX_VALUE + 1) {
				throw new NumberFormatException();
			}
			return number;
		} catch (NumberFormatException e) {
			throw new IOException("Número inválido na resposta do servidor.");
		}
	}

	private static boolean fitsDatagram(int number, String message) {
		String text = number + "," + message;
		if (text.getBytes(StandardCharsets.UTF_8).length > MAX_DATAGRAM_BYTES) {
			System.out.println("Não enviada: a mensagem completa (N,mensagem) excede 1000 bytes UTF-8.");
			return false;
		}
		return true;
	}

	private static void sendMessage(InetAddress host, int number, String message) throws IOException {
		if (!fitsDatagram(number, message)) {
			return;
		}
		String text = number + "," + message;
		System.out.println("Envio: " + text);
		String response;
		try {
			response = sendAndReceive(host, text);
		} catch (IOException e) {
			System.out.println("Entrega não confirmada. Não houve reenvio automático; "
					+ "o servidor pode ter recebido a mensagem.");
			throw e;
		}
		if (response.equals(text)) {
			System.out.println("Resposta: " + response);
		} else if (response.startsWith("waitingfor,")) {
			long expectedNumber = parseNextNumber(response.substring("waitingfor,".length()));
			System.out.println("O servidor está à espera da mensagem número: " + expectedNumber);
		} else if (response.equals("malformed message")) {
			System.out.println("Resposta: mensagem malformada; estado do servidor preservado.");
		} else {
			throw new IOException("Resposta inesperada do servidor; entrega não confirmada.");
		}
		System.out.println();
	}

	private static String sendAndReceive(InetAddress host, String text) throws IOException {
		// Socket por pedido: uma resposta atrasada nao fica na fila do pedido seguinte.
		try (DatagramSocket socket = new DatagramSocket()) {
			socket.connect(host, SERVER_PORT);
			socket.setSoTimeout(TIMEOUT_MS);
			byte[] data = text.getBytes(StandardCharsets.UTF_8);
			socket.send(new DatagramPacket(data, data.length, host, SERVER_PORT));
			// O byte adicional permite detetar respostas maiores que o limite.
			byte[] buffer = new byte[MAX_DATAGRAM_BYTES + 1];
			DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
			socket.receive(reply);
			if (reply.getLength() > MAX_DATAGRAM_BYTES) {
				throw new IOException("Resposta demasiado longa.");
			}
			ByteBuffer receivedBytes = ByteBuffer.wrap(reply.getData(), reply.getOffset(), reply.getLength());
			return StandardCharsets.UTF_8.newDecoder().decode(receivedBytes).toString();
		} catch (SocketTimeoutException e) {
			throw new IOException("Sem resposta em " + TIMEOUT_MS + " ms.", e);
		}
	}
}
