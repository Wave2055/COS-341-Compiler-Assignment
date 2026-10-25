#!/usr/bin/env bash
# Runs compiler.jar on every test input and checks the outcome.
#   valid/            must exit 0 and produce a correct tree.xml and symbol_table.txt
#   lexical_errors/   must exit 2 with a "Lexical error" and no tree.xml
#   syntax_errors/    must exit 2 with a "Syntax error" and no tree.xml
#   semantic_errors/  must exit 2 with a "Semantic error" and no symbol_table.txt
#   type_errors/      must exit 2 with a "Type error" and no symbol_table.txt
#   valid/ + codegen/ the generated output.cpp must compile with g++; codegen/X.txt must also
#                     print exactly codegen/X.expected when run
#   fuzz_codegen.py   random programs (conditions, branches, loops) checked against Python
# then compiles and runs every tests/*Test.java unit test class against the jar.
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
            [ -z "$err" ] && { [ -f "$work/symbol_table.txt" ] || err="no symbol_table.txt produced"; }
            [ -z "$err" ] && err=$(python3 "$TESTS/check_symbols.py" "$work/tree.xml" "$work/symbol_table.txt")
            [ -z "$err" ] && { [ -f "$work/output.cpp" ] || err="no output.cpp produced"; }
            [ -z "$err" ] && { g++ -pedantic-errors "$work/output.cpp" -o "$work/prog" 2>"$work/gpp.txt" || err="g++ rejected output.cpp: $(head -3 "$work/gpp.txt")"; }
            if [ -z "$err" ] && [ -f "${f%.txt}.expected" ]; then
                got=$(cd "$work" && timeout 5 ./prog)
                [ "$got" = "$(cat "${f%.txt}.expected")" ] || err="wrong output: got [$(echo $got)], expected [$(echo $(cat "${f%.txt}.expected"))]"
            fi
        elif [ -z "$err" ]; then
            grep -q "$3" <<<"$out" || err="expected a '$3' message"
            # semantic and type errors happen after parsing, so only the symbol table must be missing
            [ "$1" != semantic_errors ] && [ "$1" != type_errors ] && [ -f "$work/tree.xml" ] && err="tree.xml was written despite the error"
            [ -f "$work/symbol_table.txt" ] && err="symbol_table.txt was written despite the error"
            [ -f "$work/output.cpp" ] && err="output.cpp was written despite the error"
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
run codegen        0 ""
run lexical_errors 2 "Lexical error"
run syntax_errors  2 "Syntax error"
run semantic_errors 2 "Semantic error"
run type_errors   2 "Type error"

classes=$(mktemp -d)
for src in "$TESTS"/*Test.java; do
    cls=$(basename "$src" .java)
    echo; echo "Unit tests: $cls"
    if javac -cp "$JAR" -d "$classes" "$src" && unit=$(java -cp "$JAR:$classes" "$cls"); then
        echo "$unit" | tail -1; pass=$((pass + 1)); echo "PASS  $cls"
    else
        echo "$unit" | grep -v '^PASS'; fail=$((fail + 1)); echo "FAIL  $cls"
    fi
    [ -n "$VERBOSE" ] && sed 's/^/      /' <<<"$unit"
done
rm -rf "$classes"

echo; echo "Code generation fuzzer (${FUZZ:-25} random programs, set FUZZ=n for more)"
if python3 "$TESTS/fuzz_codegen.py" "$JAR" "${FUZZ:-25}" "$RANDOM"; then pass=$((pass + 1)); else fail=$((fail + 1)); fi

echo; echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
