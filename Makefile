proto-gen:
	buf lint protobuf && \
	rm -rf ./libs/protobuf/src/main/java/com/akto && \
	buf generate protobuf
