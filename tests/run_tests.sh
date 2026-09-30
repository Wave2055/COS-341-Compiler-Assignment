#!/usr/bin/env bash
# Runs compiler.jar on every test input and checks the outcome.
#   valid/           must exit 0 and produce a correct tree.xml
#   lexical_errors/  must exit 2 with a "Lexical error" and no tree.xml
#   syntax_errors/   must exit 2 with a "Syntax error" and no tree.xml
# usage: tests/run_tests.sh [-v]   (-v prints the compiler's messages)

cd "$(dirname "$0")"
TESTS=$PWD
JAR=${JAR:-$(ls "$TESTS"/../*.jar 2>/dev/null | head -1)}
[ -f "$JAR" ] || { echo "No jar found in the project root (or set JAR=path/to.jar)"; exit 1; }
JAR=$(realpath "$JAR"); echo "Testing $JAR"; echo
VERBOSE=$([ "$1" = "-v" ] && echo 1)
pass=0; fail=0

run() {  # dir expected_exit expected_message
    for f in "$TESTS/$1"/*.txt; do
        work=$(mktemp -d)
        out=$(cd "$work" && java -jar "$JAR" "$f" 2>&1); code=$?
        name="$1/$(basename "$f")"
        err=""
        [ "$code" -ne "$2" ] && err="exit code $code, expected $2"
        if [ -z "$err" ] && [ "$2" -eq 0 ]; then
            [ -f "$work/tree.xml" ] || err="no tree.xml produced"
            [ -z "$err" ] && err=$(python3 "$TESTS/check_tree.py" "$work/tree.xml" "$f")
        elif [ -z "$err" ]; then
            grep -q "$3" <<<"$out" || err="expected a '$3' message"
            [ -f "$work/tree.xml" ] && err="tree.xml was written despite the error"
        fi
        if [ -z "$err" ]; then
            pass=$((pass + 1)); echo "PASS  $name"
        else
            fail=$((fail + 1)); echo "FAIL  $name: $err"
        fi
        [ -n "$VERBOSE" ] || [ -n "$err" ] && sed 's/^/      /' <<<"$out"
        rm -rf "$work"
    done
}

run valid          0 ""
run lexical_errors 2 "Lexical error"
run syntax_errors  2 "Syntax error"

echo; echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
