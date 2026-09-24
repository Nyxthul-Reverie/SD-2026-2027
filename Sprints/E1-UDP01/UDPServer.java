import java.net.*;
import java.io.*;

public class UDPServer {

	public static void main(String args[]) {
		DatagramSocket aSocket = null;
		Integer L = 0;
		Integer N;

		try {
			aSocket = new DatagramSocket(6789);
			byte[] buffer = new byte[1000];

			while (true) {
				DatagramPacket request = new DatagramPacket(buffer, buffer.length);
				aSocket.receive(request);

				DatagramPacket reply = new DatagramPacket(request.getData(),
						request.getLength(), request.getAddress(), request.getPort());

				String[] parts = new String(request.getData(), 0, request.getLength()).split(",", 2);
				if (parts.length == 2) {
					try {
						N = Integer.parseInt(parts[0]);
					} catch (NumberFormatException e) {
						String message = "malformed message";
						byte[] data = message.getBytes();
						reply = new DatagramPacket(data, data.length,
								request.getAddress(), request.getPort());
						aSocket.send(reply);
						continue;
					}

					if (N != L + 1) {
						String message = "waitingfor," + (L + 1);
						byte[] data = message.getBytes();
						reply = new DatagramPacket(data, data.length,
								request.getAddress(), request.getPort());
					} else {
						L = N;
					}
				} else {
					String message = "malformed message";
					byte[] data = message.getBytes();
					reply = new DatagramPacket(data, data.length,
							request.getAddress(), request.getPort());
				}
				aSocket.send(reply);
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