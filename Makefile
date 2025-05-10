BUF_TEMPLATE ?= buf.gen.yaml

proto-gen:
	sh ./scripts/proto-gen.sh $(BUF_TEMPLATE)

build: proto-gen
	mvn install -DskipTests

build-clean: proto-gen
	mvn clean install -DskipTests
