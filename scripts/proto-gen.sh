#!/bin/bash

# Check if buf is installed or not
# Please install buf if not already installed by following the instructions at https://docs.buf.build/installation
if ! command -v buf >/dev/null 2>&1; then
    echo "Please install buf if not already installed by following the instructions at https://docs.buf.build/installation"
    exit 1
fi

buf lint protobuf
rm -rf libs/protobuf/src/main/java/com/akto/proto/generated/
buf generate protobuf