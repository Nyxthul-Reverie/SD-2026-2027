import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

public class UDPServer {

	private static final int MAX_DATAGRAM_BYTES = 1000;
	private static final int SERVER_PORT = Integer.getInteger("udp.port", 6789);

	private static final ArrayList<String> receivedMessages = new ArrayList<>();
	private static final HashMap<Integer, String> temporaryMessages = new HashMap<>();
	private static final ArrayList<String> deliveredThisStep = new ArrayList<>();

	/**
	 * Processes delivered messages
	 * 
	 * @return the last message processed in order
	 */
	public static int processDeliveredMessages(
			int nLastMessageInOrder,
			int nCurrentMessage,
			String currentMessage) {

		deliveredThisStep.clear();

		// A mensagem recebida é exatamente a próxima esperada.
		if (nCurrentMessage == (long) nLastMessageInOrder + 1) {

			String deliveredMessage = nCurrentMessage + "," + currentMessage;

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
			while (nLastMessageInOrder < Integer.MAX_VALUE
					&& temporaryMessages.containsKey(nLastMessageInOrder + 1)) {

				int nextMessageNumber = nLastMessageInOrder + 1;

				String nextMessage = temporaryMessages.remove(nextMessageNumber);

				deliveredMessage = nextMessageNumber + "," + nextMessage;

				receivedMessages.add(deliveredMessage);
				deliveredThisStep.add(deliveredMessage);

				nLastMessageInOrder = nextMessageNumber;
			}

		}

		// CASO 2:
		// A mensagem chegou adiantada.
		else if (nCurrentMessage > (long) nLastMessageInOrder + 1) {

			/*
			 * putIfAbsent evita substituir uma mensagem
			 * temporária caso o mesmo número chegue novamente.
			 */
			temporaryMessages.putIfAbsent(
					nCurrentMessage,
					currentMessage);
		}

		// CASO 3:
		// nCurrentMessage <= L
		// A mensagem já foi entregue anteriormente.
		else {

			System.out.println(
					"Mensagem duplicada/antiga ignorada: "
							+ nCurrentMessage + "," + currentMessage);
		}

		return nLastMessageInOrder;
	}

	public static void main(String[] args) {
		if (SERVER_PORT < 1 || SERVER_PORT > 65535) {
			System.out.println("Porta inválida. Use uma porta entre 1 e 65535.");
			return;
		}
		// L = numero da ultima mensagem entregue em ordem.
		int L = 0;
		try (DatagramSocket aSocket = new DatagramSocket(SERVER_PORT)) {
			// O byte extra permite detetar datagramas maiores que o limite.
			byte[] buffer = new byte[MAX_DATAGRAM_BYTES + 1];
			System.out.println("Servidor UDP iniciado na porta " + SERVER_PORT + ".");
			System.out.println("Estado inicial: L = 0");
			while (true) {
				DatagramPacket request = new DatagramPacket(buffer, buffer.length);
				aSocket.receive(request);
				deliveredThisStep.clear();
				String received = "[datagrama inválido]";
				String response = "malformed message";
				try {
					if (request.getLength() > MAX_DATAGRAM_BYTES) {
						throw new IllegalArgumentException("O datagrama excede 1000 bytes.");
					}
					ByteBuffer receivedBytes = ByteBuffer.wrap(request.getData(), request.getOffset(), request.getLength());
					received = StandardCharsets.UTF_8.newDecoder().decode(receivedBytes).toString();
					String[] parts = received.split(",", 2);
					if (parts.length != 2 || parts[1].isBlank()) {
						throw new IllegalArgumentException("Formato esperado: N,mensagem.");
					}
					int N = Integer.parseInt(parts[0].trim());
					if (N < 1) {
						throw new IllegalArgumentException("N deve ser positivo.");
					}
					int oldL = L;
					L = processDeliveredMessages(L, N, parts[1]);
					response = L == oldL ? "waitingfor," + ((long) L + 1) : received;
				} catch (CharacterCodingException | IllegalArgumentException e) {
					System.out.println("Mensagem malformada; estado preservado. " + e.getMessage());
				}
				try {
					sendReply(aSocket, request, response);
				} catch (IOException e) {
					System.out.println("Não foi possível responder: " + e.getMessage());
				}
				printState(received, response, L);
			}
		} catch (IOException e) {
			System.out.println("Erro de entrada/saída: " + e.getMessage());
		}
	}

	private static void printState(String received, String response, int L) {
		System.out.println("---------------------------------------");
		System.out.println("Recebido: " + received);
		System.out.println("Resposta: " + response);
		System.out.println("L: " + L);
		System.out.println("Temporárias: " + temporaryMessages);
		System.out.println("Entregues neste passo: " + deliveredThisStep);
		System.out.println("Lista de receção: " + receivedMessages);
		System.out.println("---------------------------------------");
		System.out.println();
	}

	private static void sendReply(DatagramSocket socket, DatagramPacket request, String message)
			throws IOException {
		byte[] data = message.getBytes(StandardCharsets.UTF_8);
		socket.send(new DatagramPacket(data, data.length, request.getAddress(), request.getPort()));
	}
}
