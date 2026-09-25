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

		if (nCurrentMessage == nLastMessageInOrder + 1) {
			String deliveredMessage = nCurrentMessage + "," + currentMessage;
			receivedMessages.add(deliveredMessage);
			deliveredThisStep.add(deliveredMessage);
			nLastMessageInOrder = nCurrentMessage;

			while (temporaryMessages.containsKey(nLastMessageInOrder + 1)) {
				int nextMessageNumber = nLastMessageInOrder + 1;
				String nextMessage = temporaryMessages.remove(nextMessageNumber);
				deliveredMessage = nextMessageNumber + "," + nextMessage;
				receivedMessages.add(deliveredMessage);
				deliveredThisStep.add(deliveredMessage);
				nLastMessageInOrder = nextMessageNumber;
			}
		} else {
			temporaryMessages.put(nCurrentMessage, currentMessage);
		}

		return nLastMessageInOrder;
	}

	public static void main(String args[]) {
		DatagramSocket aSocket = null;
		int L = 0;

		try {
			aSocket = new DatagramSocket(6789);
			byte[] buffer = new byte[1000];

			System.out.println("Servidor UDP iniciado na porta 6789.");

			while (true) {
				DatagramPacket request = new DatagramPacket(buffer, buffer.length);
				aSocket.receive(request);

				String received = new String(
						request.getData(), 0, request.getLength(), StandardCharsets.UTF_8);
				String[] parts = received.split(",", 2);

				if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].isEmpty()) {
					sendReply(aSocket, request, "malformed message");
					System.out.println("Recebido: " + received);
					System.out.println("Mensagem malformada; estado preservado.");
					continue;
				}

				int N;
				try {
					N = Integer.parseInt(parts[0].trim());
				} catch (NumberFormatException e) {
					sendReply(aSocket, request, "malformed message");
					System.out.println("Recebido: " + received);
					System.out.println("Mensagem malformada; estado preservado.");
					continue;
				}

				int oldL = L;
				L = processDeliveredMessages(L, N, parts[1]);

				if (L == oldL) {
					sendReply(aSocket, request, "waitingfor," + (L + 1));
				} else {
					sendReply(aSocket, request, received);
				}

				System.out.println("Recebido: " + received);
				System.out.println("L: " + L);
				System.out.println("Temporarias: " + temporaryMessages);
				System.out.println("Entregues neste passo: " + deliveredThisStep);
				System.out.println("Lista de rececao: " + receivedMessages);
				System.out.println();
			}
		} catch (SocketException e) {
			System.out.println("Socket: " + e.getMessage());
		} catch (IOException e) {
			System.out.println("IO: " + e.getMessage());
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

		byte[] data = message.getBytes(StandardCharsets.UTF_8);
		DatagramPacket reply = new DatagramPacket(
				data, data.length, request.getAddress(), request.getPort());
		socket.send(reply);
	}
}
