#!/bin/bash
# Check if buf is already installed
if ! command -v buf >/dev/null 2>&1; then
    brew install buf
fi

# Check if protoc is already installed
if command -v protoc &> /dev/null; then
    echo "protoc is already installed"
    exit 0
fi

PROTOBUF_VERSION=29.2
wget https://github.com/protocolbuffers/protobuf/releases/download/v$PROTOBUF_VERSION/protoc-$PROTOBUF_VERSION-osx-universal_binary.zip -O protoc.zip
unzip protoc.zip -d $HOME/protoc
rm -rf protoc.zip

# Add protoc to PATH
echo "export PATH=\"$HOME/protoc/bin:\$PATH\"" >> ~/.zshrc
echo "export PATH=\"$HOME/protoc/bin:\$PATH\"" >> ~/.bashrc
