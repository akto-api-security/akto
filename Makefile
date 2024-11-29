proto-gen:
	sh ./scripts/proto-gen.sh

build: proto-gen
	mvn install -DskipTests

build-clean: proto-gen
	mvn clean install -DskipTests
