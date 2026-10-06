package tcp01;

import java.io.*;
import java.net.*;
import tcp01.demo.BrokenPerson;

public class Connection extends Thread {
    ObjectInputStream in;
    DataOutputStream out;
    Socket clientSocket;

    public Connection(Socket aClientSocket) {
        try {
            clientSocket = aClientSocket;
            in = new ObjectInputStream(clientSocket.getInputStream());
            out = new DataOutputStream(clientSocket.getOutputStream());
            this.start();
        } catch (IOException e) {
            System.out.println("Connection: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            Object object = in.readObject();

            if (object instanceof BrokenPerson) {
                out.writeUTF("Erro: serialVersionUID incompatível - classe em versão alternativa detectada.");
                out.flush();
                return;
            }

            if (!(object instanceof Person)) {
                out.writeUTF("Erro: objeto recebido não é uma Person válida.");
                out.flush();
                return;
            }

            Person person = (Person) object;
            if (person.getPlace() == null || person.getPlace().getLocality() == null || person.getPlace().getLocality().trim().isEmpty()) {
                out.writeUTF("Erro: Place vazio ou nulo. Não foi possível obter a localidade.");
                out.flush();
                return;
            }

            out.writeUTF(person.getPlace().getLocality());
            out.flush();
        } catch (EOFException e) {
            System.out.println("EOF: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFound: " + e.getMessage());
        } catch (ClassCastException e) {
            try {
                out.writeUTF("Erro: tipo incompatível. O servidor esperava Person e recebeu outra classe.");
                out.flush();
            } catch (IOException io) {
                System.out.println("IO: " + io.getMessage());
            }
        } catch (IOException e) {
            System.out.println("IO: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                /* falha ao fechar */
            }
        }
    }
}
