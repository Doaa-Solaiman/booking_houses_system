#!/bin/bash
cd "$(dirname "$0")"
java -jar fewo-buchung-0.0.1-SNAPSHOT.jar 2>&1 > output.log &
