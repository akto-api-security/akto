#!/bin/bash

exec java \
  -XX:+ExitOnOutOfMemoryError \
  -jar /app/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar
