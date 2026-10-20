#!/bin/sh
# Compila e roda os testes da lógica do firmware no computador.
set -e
cd "$(dirname "$0")"
g++ -std=c++11 -Wall -Wextra -O1 -o /tmp/stepvolt_logic_test logic_test.cpp -lm
/tmp/stepvolt_logic_test
