import java.io.*;
import java.net.*;
import java.util.*;

public class MathClient {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1234;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedReader inFromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             DataOutputStream outToServer = new DataOutputStream(socket.getOutputStream());
             Scanner scanner = new Scanner(System.in)) {

            // Step 1: Send name to server and wait for ACK
            System.out.print("Enter your name: ");
            String name = scanner.nextLine();
            outToServer.writeBytes("NAME " + name + "\n");

            String ack = inFromServer.readLine();
            if (!"ACK".equals(ack)) {
                System.out.println("Server did not acknowledge connection. Exiting.");
                return;
            }
            System.out.println("Connected successfully as " + name);

            // Step 2: Send 3 math expressions at random intervals
            String[] expressions = {"2 + 3", "10 * 4", "100 / 5", "8 - 3", "15 + 20", "9 / 0"};
            Random rand = new Random();

            for (int i = 0; i < 3; i++) {
                String expr = expressions[rand.nextInt(expressions.length)];
                System.out.println("Sending: " + expr);
                outToServer.writeBytes(expr + "\n");

                // Optional: Read result from server (if server supports it)
                String result = inFromServer.readLine();
                if (result != null) {
                    System.out.println("Received: " + result);
                }

                Thread.sleep((rand.nextInt(3) + 1) * 1000); // 1-3 seconds delay
            }

            // Step 4: Send CLOSE request to server
            System.out.println("Sending CLOSE request.");
            outToServer.writeBytes("CLOSE\n");

        } catch (IOException | InterruptedException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }
}
