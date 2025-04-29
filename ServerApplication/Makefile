JAVAC = javac
JAVA = java

CLASSES = MathServer.class MathClient.class

all: $(CLASSES)

%.class: %.java
	$(JAVAC) $<

run-server: MathServer.class
	$(JAVA) MathServer

run-client: MathClient.class
	$(JAVA) MathClient

clean:
	rm -f *.class
