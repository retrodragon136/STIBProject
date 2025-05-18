# Makefile pour le projet STIB

# Configuration
JAVAC = javac
JAVA = java
JAVAC_FLAGS = -encoding UTF-8 -d bin -sourcepath src
CLASSPATH = bin
SRC_DIR = src
BIN_DIR = bin
DATA_DIR = data/GTFS

# Classes principales
MAIN_CLASS = Main
MODEL_CLASSES = $(SRC_DIR)/model/TransportGraph.java $(SRC_DIR)/model/GraphNode.java \
                $(SRC_DIR)/model/Route.java $(SRC_DIR)/model/Stop.java \
                $(SRC_DIR)/model/StopTime.java $(SRC_DIR)/model/Trip.java
LOADER_CLASS = $(SRC_DIR)/loader/CsvLoader.java

# Règles principales
all: compile

compile:
	@mkdir -p $(BIN_DIR)
	$(JAVAC) $(JAVAC_FLAGS) -cp $(CLASSPATH) $(SRC_DIR)/$(MAIN_CLASS).java $(MODEL_CLASSES) $(LOADER_CLASS)

run: compile
	$(JAVA) -cp $(CLASSPATH) $(MAIN_CLASS)

clean:
	rm -rf $(BIN_DIR)

.PHONY: all compile run clean