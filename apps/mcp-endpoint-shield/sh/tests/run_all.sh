#!/bin/bash
# Runs every shell-hook test suite. Run: bash sh/tests/run_all.sh
cd "$(dirname "$0")" || exit 1
rc=0
for suite in test_json.sh test_setkey.sh test_hooks.sh test_hardening.sh test_install.sh test_parity.sh; do
    echo "== $suite"
    bash "$suite" || rc=1
done
echo
if [ $rc -eq 0 ]; then echo "ALL SUITES PASSED"; else echo "SOME SUITES FAILED"; fi
exit $rc
