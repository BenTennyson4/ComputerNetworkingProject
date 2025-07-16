package mathCalc;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MathServer {

	private static final int PORT = 1234;
    private static final int THREAD_POOL_SIZE = 5;

    // LinkedBlockingQueue is based on linked nodes and orders client requests in FIFO. The BlockingQueue is thread-safe and supports operations like take() and put().
    private static final BlockingQueue<ClientRequest> requestQueue = new LinkedBlockingQueue<>();
    // Hash map to store the responses to the clients (key: clientID, value: ClientResponse object)
    private static final ConcurrentHashMap<Long, ClientResponse> responseMap = new ConcurrentHashMap<>();
    // Create a thread pool that creates new threads as needed for handling the clients connected to the server.
    private static final ExecutorService clientHandlerPool = Executors.newCachedThreadPool(); // For processClient
    // Create a thread pool that reuses a fixed number of threads operating off a shared unbounded queue.
    private static final ExecutorService queueWorkerPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE); //For processQueuedRequest
    private static long requestCounter = 0;  // Ensures FCFS order

    public static void main(String argv[]) {
    	// Create a socket for the server that listens for incoming clients
        try (ServerSocket welcomeSocket = new ServerSocket(PORT)) {
            System.out.println("The server is running.");
            
            // Start response sender thread (ensures responses are sent in order)
            new Thread(MathServer::sendResponses).start();
            
            /*
             *  Create threads that will process the requests in the queue. These threads will be automatically closed 
             *  when the server is stopped.
             */
            for (int i = 0; i < THREAD_POOL_SIZE; i++) {
            	queueWorkerPool.execute(MathServer::processQueuedRequest);
            }

            while (true) {
                // Create a unique socket for clients that have connected to the main server socket welcomeSocket
                Socket connectionSocket = welcomeSocket.accept();
                
                // Log client connection info (IP address, client port number, connection time)
                InetAddress clientAddress = connectionSocket.getInetAddress();
                int clientPort = connectionSocket.getPort();
                Date connectionTime = new Date();
                System.out.println("Client connected from " + clientAddress + ":" + clientPort + " at " + connectionTime);

                
                // Process the request concurrently
                clientHandlerPool.execute(() -> processClient(connectionSocket));

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
    
    // Processes client requests concurrently by fetching from the queue and computing results.
    private static void processClient(Socket socket) {
        try {
        	// Logging info: time connected and clientName
        	Date connectionTime = new Date();
        	String clientName = "UNKNOWN";
        	
        	BufferedReader inFromClient = null;
        	BufferedWriter outFromServer = null;

            try {
            	// Create buffers to store text-based input from the input and output streams of the client socket
            	inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            	outFromServer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            	String line;
                boolean connected = false;

                
                // Read lines from the client until the connection is closed or a "CLOSE" command is received
                while ((line = inFromClient.readLine()) != null) {
                	System.out.println("Received from client: " + line);

                    // If the client has not yet identified themselves with a NAME command
                    if (!connected) {
                        // Check if the message starts with "NAME "
                        if (line.startsWith("NAME ")) {
                            // Extract the client's name and acknowledge the connection as established
                            clientName = line.substring(5).trim(); // Remove "NAME " prefix and trim whitespace
                            outFromServer.write("ACK\n");
                            outFromServer.flush();
                            connected = true;
                            System.out.println("Client identified as: " + clientName);
                        } else {
                            outFromServer.write("ERROR: Missing NAME header\n");
                            outFromServer.flush();
                            System.out.println("Malformed initial message: " + line);
                            break; // Exit the loop and close the connection
                        }
                        continue; // Go to the next iteration of the loop
                    }

                    // If the client sends "CLOSE", log the event and disconnect
                    if (line.equalsIgnoreCase("CLOSE")) {
                        System.out.println("Client " + clientName + " requested to close connection.");

                        long requestId;
                        synchronized (MathServer.class) {
                            requestId = requestCounter++;
                        }

                        // Put a dummy request into the queue to signal end of session
                        requestQueue.add(new ClientRequest(requestId, socket, new Date(), clientName, "CLOSE", true));
                        break;  // exit the read loop
                    }

                    // If the line variable was not NAME command or CLOSE command from the client, then it should be a math expression
                    long requestId;
                    synchronized (MathServer.class) {
                        requestId = requestCounter++;
                    }
                                        
                    /*
                     *  Queue the actual math request. We need to add the isCloseCommand argument, so we double check that line is not CLOSE 
                     *  (it should be false at this point) and add it.
                     */
                    boolean isCloseCommand = line.equalsIgnoreCase("CLOSE");
                    requestQueue.add(new ClientRequest(requestId, socket, connectionTime, clientName, line, isCloseCommand));
                    System.out.println("Queued request " + requestId + " from " + clientName + ": " + line);
                }

            } catch (IOException e) {
                System.out.println("EXCEPTION in processClient()");
                e.printStackTrace();
            } finally {
            	// Close the socket and display the client log info (IP address, disconnection time, session duration)
            	Date disconnectTime = new Date();
                long sessionDurationMillis = disconnectTime.getTime() - connectionTime.getTime();
                System.out.println("Client " + socket.getInetAddress() + " processing done at " + disconnectTime +
                        ". Session processing duration: " + (sessionDurationMillis / 1000.0) + " seconds.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    // Stores request details (Socket + Request ID for ordering)
    private static class ClientRequest {
        private final long requestId;
        private final Socket socket;
        private final Date connectionTime;
        private final String clientName;
        private final String expression;
        private final boolean shouldCloseAfterResponse; 

        public ClientRequest(long requestId, Socket socket, Date connectionTime,
                             String clientName, String expression, boolean shouldCloseAfterResponse) {
            this.requestId = requestId;
            this.socket = socket;
            this.connectionTime = connectionTime;
            this.clientName = clientName;
            this.expression = expression;
            this.shouldCloseAfterResponse = shouldCloseAfterResponse;
        }

        public boolean shouldClose() { return shouldCloseAfterResponse; }
        public long getRequestId() { return requestId; }
        public Socket getSocket() { return socket; }
        public String getClientName() { return clientName; }
        public String getExpression() { return expression; }
    }



    
    // Stores information to be delivered to the client
    private static class ClientResponse {
        private final Socket socket;
        private final String clientName;
        private final String response;
        private final boolean shouldClose; 

        public ClientResponse(Socket socket, String clientName, String response, boolean shouldClose) {
            this.socket = socket;
            this.clientName = clientName;
            this.response = response;
            this.shouldClose = shouldClose;
        }

        public boolean shouldClose() { return shouldClose; }
        public Socket getSocket() { return socket; }
        public String getClientName() { return clientName; }
        public String getResponse() { return response; }
    }

    
    
    // Ensures responses are sent in order (FCFS)
    private static void sendResponses() {
    	long expectedRequestId = 0;

        while (true) {
        	// Check whether a response for the next expected request ID is ready
            if (responseMap.containsKey(expectedRequestId)) {
            	// Get the ClientResponse object from the responseMap
                ClientResponse cr = responseMap.remove(expectedRequestId);
                try {
                	// Get the client's Socket and then OutputStream associated with the socket
                	OutputStream rawOut = cr.getSocket().getOutputStream();
                	// Prepare to send the response back through the clients's socket using BufferedWriter
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(rawOut));
                    
                    // Send the response string to the client
                    out.write(cr.getResponse() + "\n");
                    // Ensure the data is pushed out immediately
                    out.flush();

                    System.out.println("Sending response for request " + expectedRequestId + " from " + cr.getClientName() + ": " + cr.getResponse());
                    
                    // Close socket if instructed
                    if (cr.shouldClose()) {
                        cr.getSocket().close();
                        System.out.println("Closed connection for " + cr.getClientName());
                    }
                } catch (IOException e) {
                	System.err.println("Failed to send response to " + cr.getClientName());
                    e.printStackTrace();
                }
                
                // Increment to get the next expected response
                expectedRequestId++;
            }
        }
    }
    
    
    private static void processQueuedRequest() {
        try {
            ClientRequest cr = requestQueue.take(); // FCFS!
            /*
             *  If the request was to close he client connection, then output that the client connection is closed.
             *  Otherwise compute the result.
             */
            String result = cr.getExpression().equalsIgnoreCase("CLOSE") ? "Session closed by client." : processCalculation(cr.getExpression());
            responseMap.put(cr.getRequestId(), new ClientResponse(cr.getSocket(), cr.getClientName(), result, cr.shouldClose()));

        } catch (Exception e) {
            e.printStackTrace();
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
