#!/bin/bash

# Check if buf is installed or not
# Please install buf if not already installed by following the instructions at https://docs.buf.build/installation
if ! command -v buf >/dev/null 2>&1; then
    echo "Please install buf if not already installed by following the instructions at https://docs.buf.build/installation"
    exit
fi

buf lint protobuf
rm -rf ./libs/protobuf/src
buf generate protobuf