# MathServer: A Multi-threaded TCP Math Expression Server

## Overview

MathServer is a multi-threaded TCP server implemented in Java that processes simple infix math expressions sent by clients. The server supports multiple simultaneous client connections and evaluates expressions such as `2 + 3` or `10 * 4`, returning the result in first-come, first-served (FCFS) order. Clients can gracefully terminate the session using the `CLOSE` command.

## Features

- TCP-based communication using `Socket` and `ServerSocket`
- Concurrent client support via a cached thread pool
- Multi-threaded request processing using a fixed thread pool and blocking queue
- Response dispatching ensures FCFS response order using a shared response map
- Client session logging: IP address, port, connection time, and session duration
- Graceful disconnection via the `CLOSE` protocol command

## Components

### mathCalc/MathServer.java
- Accepts client connections on port 1234
- Processes math expressions or CLOSE commands
- Queues requests for processing
- Sends responses back in the order they were received
- Closes sockets when the session ends

### mathCalc/MathClient.java
- Connects to the server via TCP
- Sends a `NAME <clientName>` command to identify itself
- Sends 3 randomly chosen expressions (e.g., `10 * 4`, `9 / 0`)
- Prints server responses
- Ends the session with a `CLOSE` command

### Makefile
Includes commands to compile, run, and clean:
make # Compiles all Java files
make run-server # Runs MathServer
make run-client # Runs MathClient
make clean # Deletes all .class files
