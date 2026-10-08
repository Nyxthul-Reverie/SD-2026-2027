package tcp01;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class TCPClient {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n=== TCP01 - Demo menu ===");
            System.out.println("1 - Enviar pessoa default");
            System.out.println("2 - Enviar pessoa customizada");
            System.out.println("3 - Enviar pessoa com Place vazio");
            System.out.println("4 - Enviar pessoa incompatível (UID diferente)");
            System.out.println("5 - Sair");

            String option = readMenuOption(scanner);
            if (option.equals("5")) {
                System.out.println("Sair da demo.");
                break;
            }

            if (!option.matches("[1-4]")) {
                System.out.println("Opção inválida. Escolha uma opção entre 1 e 5.");
                continue;
            }

            try {
                int serverPort = 7896;
                Socket s = new Socket("localhost", serverPort);
                ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
                DataInputStream in = new DataInputStream(s.getInputStream());

                Object payload = buildPayload(option, scanner);
                oos.writeObject(payload);
                oos.flush();

                String response = in.readUTF();
                System.out.println("Resposta do servidor: " + response);
                s.close();
            } catch (UnknownHostException e) {
                System.out.println("Sock: " + e.getMessage());
            } catch (EOFException e) {
                System.out.println("EOF: " + e.getMessage());
            } catch (ConnectException e) {
                System.out.println("Erro de ligação: servidor não está a correr.");
            } catch (IOException e) {
                System.out.println("IO: " + e.getMessage());
            }
        }

        scanner.close();
    }

    private static String readMenuOption(Scanner scanner) {
        while (true) {
            System.out.print("Escolha uma opção: ");
            String option = scanner.nextLine().trim();
            if (option.isEmpty()) {
                System.out.println("Opção vazia. Escolha uma opção entre 1 e 5.");
                continue;
            }
            return option;
        }
    }

    private static String readRequiredText(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("Campo obrigatório. Tenta outra vez.");
        }
    }

    private static int readRequiredInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (value.isEmpty()) {
                System.out.println("Ano inválido. Introduz um número.");
                continue;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.out.println("Ano inválido. Introduz um número.");
            }
        }
    }

    private static Object buildPayload(String option, Scanner scanner) {
        switch (option) {
            case "1":
                return new Person("Ana", new Place("4000-001", "Porto"), 1995);
            case "2":
                String nome = readRequiredText(scanner, "Nome: ");
                String localidade = readRequiredText(scanner, "Localidade: ");
                int ano = readRequiredInt(scanner, "Ano: ");
                return new Person(nome, new Place("9999-999", localidade), ano);
            case "3":
                return new Person("Sem localidade", new Place("0000-000", ""), 2000);
            case "4":
                return new tcp01.demo.BrokenPerson("Pessoa incompatível", new Place("0000-000", "Lisboa"), 1980);
            default:
                System.out.println("Opção inválida. Escolha uma opção entre 1 e 5.");
                return new Person("Fallback", new Place("1111-111", "Fallback"), 1990);
        }
    }
}
