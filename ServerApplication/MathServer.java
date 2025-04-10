import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MathServer {

	private static final int PORT = 1234;
    private static final int THREAD_POOL_SIZE = 5;

    private static final BlockingQueue<ClientRequest> requestQueue = new LinkedBlockingQueue<>();
    private static final ConcurrentHashMap<Long, String> responseMap = new ConcurrentHashMap<>();
    private static final ExecutorService workerPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static long requestCounter = 0;  // Ensures FCFS order

    public static void main(String argv[]) {
        try (ServerSocket welcomeSocket = new ServerSocket(PORT)) {
            System.out.println("The server is running.");
            
            // Start response sender thread (ensures responses are sent in order)
            new Thread(MathServer::sendResponses).start();

            while (true) {
                // Wait for a client to connect
                Socket connectionSocket = welcomeSocket.accept();
                
                // Assign unique ID to maintain order
                long requestId = requestCounter++;

                // Initial handshake
                BufferedReader in = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                DataOutputStream out = new DataOutputStream(connectionSocket.getOutputStream());

                String nameLine = in.readLine();
                if (nameLine != null && nameLine.startsWith("NAME ")) {
                    String clientName = nameLine.substring(5).trim();
                    System.out.println("Client connected: " + clientName);
                    out.writeBytes("ACK\n");

                    // Handle connected clients
                    requestQueue.add(new ClientRequest(requestId, connectionSocket, clientName));
                    
                    // Process the request concurrently
                    workerPool.execute(() -> processRequest());

                } else {
                    out.writeBytes("INVALID INIT\n");
                    connectionSocket.close();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
    
    // Processes client requests concurrently by fetching from the queue and computing results.
    private static void processRequest() {
        ClientRequest clientRequest = null;

        try {
            clientRequest = requestQueue.take();
            Socket socket = clientRequest.getSocket();
            long requestId = clientRequest.getRequestId();
            String clientName = clientRequest.getClientName();

            try (BufferedReader inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 DataOutputStream outFromServer = new DataOutputStream(socket.getOutputStream())) {

                String mathExpression;
                while ((mathExpression = inFromClient.readLine()) != null) {
                    if (mathExpression.equalsIgnoreCase("CLOSE")) {
                        System.out.println("Client " + clientName + " disconnected.");
                        break;
                    }

                    System.out.println("Received from " + clientName + ": " + mathExpression);
                    String result = processCalculation(mathExpression);
                    responseMap.put(requestId, result);  // Store result in FCFS order
                    outToClient.writeBytes(result + "\n");  // Echo result
                }

            } catch (IOException e) {
                System.err.println("IO error with client " + clientName);
                e.printStackTrace();
            }

        } catch (InterruptedException e) {
            System.err.println("Worker interrupted.");
        } finally {
            if (clientRequest != null) {
                try {
                    clientRequest.getSocket().close();
                } catch (IOException e) {
                    System.err.println("Error closing socket");
                }
            }
        }
    } 
    
    
    // Stores request details (Socket + Request ID for ordering)
    private static class ClientRequest {
        private final long requestId;
        private final Socket socket;
        private final String name;

        public ClientRequest(long requestId, Socket socket, String name) {
            this.requestId = requestId;
            this.socket = socket;
            this.name = name;
        }

        public long getRequestId() {
            return requestId;
        }

        public Socket getSocket() {
            return socket;
        }

        public String getClientName() {
            return name;
        }
    }
    
    
 // Ensures responses are sent in order (FCFS)
    private static void sendResponses() {
        long expectedRequestId = 0;

        while (true) {
            if (responseMap.containsKey(expectedRequestId)) {
                String response = responseMap.remove(expectedRequestId);
                System.out.println("Sending response for request " + expectedRequestId + ": " + response);
                expectedRequestId++;  // Move to the next response in order
            }
        }
    }

    
    // Calculate the result of infix expressions (without parentheses)
    private static String processCalculation(String mathExpression) {
        List<String> tokens = new ArrayList<>(Arrays.asList(mathExpression.split(" ")));
        if (tokens.size() != 3) return "INVALID FORMAT";

        // Iterate through operators and numbers and calculate the result of the expression.
        // First pass: handle multiplication and division
        for (int i = 0; i < tokens.size(); i++) {
            String operator = tokens.get(i);

            if (operator.equals("*") || operator.equals("/")) {
                double num1 = Double.parseDouble(tokens.get(i - 1));
                double num2 = Double.parseDouble(tokens.get(i + 1));
                double result;

                if (operator.equals("*")) result = num1 * num2;
                else {
                    if (num2 == 0) return "ERROR: Division by zero";
                    result = num1 / num2;
                }

                // Replace expression in the list: (num1 operator num2) with result
                tokens.set(i - 1, String.valueOf(result)); // Replace num1 with result
	            tokens.remove(i);  // Remove operator
	            tokens.remove(i);  // Remove second number
	            i--; // Move index back since we removed elements
            }
        }

        // Second pass: handle addition and subtraction
        double result = Double.parseDouble(tokens.get(0));

        for (int i = 1; i < tokens.size(); i += 2) { // Step by 2 to get operators
            String operator = tokens.get(i);
            double num2 = Double.parseDouble(tokens.get(i + 1));

            if (operator.equals("+")) result += num2;
            else result -= num2;
        }

        return "= " + result;
    }
}
