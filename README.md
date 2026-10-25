# COS-341-Compiler-Assignment
## Group 13

## Team Members
- Ayush Beekum u23596351
- Jaitin Moodally u23621372
- Kgaugelo Matsena u23658462
- Lusanda Mtembu u23602016

## Compiling the Jar

```
javac --release 17 -d build *.java && jar --create --file COCO-Group13.jar --main-class Driver -C build .
```

The command compiles our java files into the necessary class files and then bundles them into a jar

This jar can be executed on Java version 17 or higher

## Executing the Jar file

You execute the jar by using the command

```
java -jar COCO-Group13.jar 
```

This causes the jar to search for an SPL.txt in its current directory.
If the SPL.txt is not found it will throw an error

Should you wish to use the jar with a specific file you would run 

```
java -jar COCO-Group13.jar <path to file>
```

This overrides the search for SPL.txt and allows you to run the source code from any file on the system should file path be provided



## Output

On a syntactically correct program the compiler writes `tree.xml` (the syntax tree).
It then runs the scope and name analysis (Part 2a) and the type analysis (Part 2b).
If both pass, it writes `symbol_table.txt`: every declared variable, parameter and function
gets a unique internal name (`v1`, `f1`, ...) and its type (`numeric` or `procedure`),
and every name in the tree is mapped to the entity it refers to.
Finally it generates the target code (Parts 3a and 3b) and writes `output.cpp`.
Lexical, syntax, semantic and type errors are printed to stderr and exit with code 2;
in that case no `output.cpp` is written.

All output files are written to the directory the jar is run from.

## Compiling and running the generated code

`output.cpp` is C++ (it uses `std::cout`). **We prefer g++ for the run-tests:**

```
g++ output.cpp -o program
./program
```

gcc 13 also works, as long as the C++ standard library is linked:

```
gcc output.cpp -lstdc++ -o program
```

The generated code has been tested with g++ 13 under `-std=c++11`, `-std=c++17`,
`-std=c++20` and `-pedantic-errors`, and with gcc 13 as shown above.

## Structure of the generated code

1. **The code-mine** (non-executable). Between `/* begin of the non-executable code-mine ... */`
   and `/* end of the non-executable code-mine */` every function appears with its
   parameters, its body and (for `num` functions) its return variable `r`. Every line of the
   mine is a `//` comment, because the function bodies themselves contain `/* ... */`
   comments and C block comments cannot be nested.
2. **The executable `main`**: `#include <iostream>`, `int main()`, the declarations of the
   main program's variables, the translated algorithm, and `return 0 ;`.

Naming in the target code:

| Name | Meaning |
|------|---------|
| `v1`, `v2`, ... | SPL variables and parameters (internal names from the symbol table, Part 2a) |
| `t1`, `t2`, ... | temporaries created by the code generator (Fig.6.3 style) |
| `L1`, `L2`, ... | jump labels |
| `f1t7` | return value `r = f ++ n` of function `f1` |
| `X_k` | a name `X` belonging to the `k`-th inlined copy of a function |

The number type (`int` or `float`) is chosen once for the whole program, following
the Part 2b rules: `int` if the program uses `mod`, `float` if it uses `div` or contains a
decimal number, and `int` otherwise.

### Design decisions (where we deviate from the letter of the spec sheets)

- **Boolean conditions.** `trans(BOOL, T, F)` always jumps to `T` (true) or `F` (false) and
  never falls through: each comparison becomes `if ( x op y ) goto T ; goto F`. This is
  textbook Fig.6.8 with every `ELSE` replaced by a `goto`. `not` is then exactly the label
  swap of the spec, `or` / `and` use one intermediate label. The literal rules of
  sheet 3a would generate wrong code: with a fall-through to the else-label, `not ( eq ( a b ) )`
  would send *both* outcomes into the else-branch, and the given `and` rule expands to the
  same code as `or`. The target syntax stays as specified: only `if ( x op y ) goto L` with
  `op` in `== > <`, plain `goto L` and labels.
- **Parameter passing.** On a call we emit `a1 = p1` (formal parameter := evaluated
  argument), which is the intended semantics of the "p1 = a1" line on sheet 3b.
- **Inlining.** Every inlined copy renames all variables, temporaries and labels declared
  by the function (suffix `_k`), including copies of its sub-functions, which were already
  inlined into it inside the code-mine. So calling the same function several times, or a
  function whose sub-function contains loops, never produces duplicate labels or
  declarations. Names of enclosing scopes and globals are not renamed.
- **Calls with a different number of arguments** (accepted by the Part 2 rules):
  arguments and parameters are paired in order; extra arguments are evaluated and ignored,
  missing ones leave the parameter uninitialised.
- **Line breaks.** Every `print` ends with `<< std::endl` so that consecutive outputs do not
  run together on screen. Set `CodeGenerator.NEWLINE_AFTER_PRINT = false` to get the bare
  `std::cout << x` of the spec.
- **Declarations** of temporaries have no initialiser, so `goto` may legally jump over them in C++.

## Running the tests

```
tests/run_tests.sh # add -v to see every compiler message
FUZZ=300 tests/run_tests.sh # run more random code-generation programs
```

Requires Java 17+, Python 3 and g++. Besides the front-end tests, every valid program
must produce an `output.cpp` that g++ accepts, the programs in `tests/codegen/` must print
exactly their `.expected` output, and `tests/fuzz_codegen.py` checks random programs
(nested `and`/`or`/`not` conditions, branches and all four loop kinds) against Python.
