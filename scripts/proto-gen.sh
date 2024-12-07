#!/bin/bash

# Check if buf is installed or not
if ! command -v buf &> /dev/null
then
    echo "buf is not installed. Please install buf by following the instructions at https://docs.buf.build/installation"
    exit
fi

buf lint protobuf
rm -rf ./libs/protobuf/src
buf generate protobuf