proto-gen:
	sh ./scripts/proto-gen.sh

build-j8: proto-gen
	# Switch to Java 8
	mvn clean install -DskipTests -pl \!apps/threat-detection,\!apps/threat-detection-backend -am -T 4

build-j17: proto-gen
	# Switch to Java 17
	mvn clean install -DskipTests -pl apps/threat-detection,apps/threat-detection-backend -am
