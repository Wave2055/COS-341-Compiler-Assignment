#!/usr/bin/env python3
"""Differential fuzzer for the code generator.

Generates random SPL programs (nested and / or / not conditions inside branches and
all four loop kinds, plus integer arithmetic), computes the expected output in
Python, compiles each program with the jar and g++, runs it, and compares.
usage: fuzz_codegen.py <jar> [count] [seed]
"""
import os, random, subprocess, sys, tempfile

JAR, COUNT, SEED = os.path.abspath(sys.argv[1]), int(sys.argv[2]) if len(sys.argv) > 2 else 200, int(sys.argv[3]) if len(sys.argv) > 3 else 1
rnd = random.Random(SEED)
VARS = ["#a", "#b", "#c", "#n"]


def cmod(x, y):                       # C++ % truncates towards zero
    r = abs(x) % abs(y)
    return -r if x < 0 else r


def term(d):
    """Returns (spl_text, evaluator)."""
    k = rnd.randrange(6 if d > 0 else 2)
    if k == 0:
        v = rnd.choice(VARS); return v, lambda e, v=v: e[v]
    if k == 1:
        n = rnd.randint(-3, 5); return str(n), lambda e, n=n: n
    if k == 5:
        s, f = term(d - 1); return f"neg ( {s} )", lambda e: -f(e)
    a, fa = term(d - 1); b, fb = term(d - 1)
    if k == 2: return f"add ( {a} {b} )", lambda e: fa(e) + fb(e)
    if k == 3: return f"sub ( {a} {b} )", lambda e: fa(e) - fb(e)
    m = rnd.choice([2, 3, -4])
    return f"mod ( {a} {m} )", lambda e: cmod(fa(e), m)


def cond(d):
    k = rnd.randrange(6 if d > 0 else 3)
    if k < 3:
        op = ["eq", "larger", "lesser"][k]
        a, fa = term(1); b, fb = term(1)
        py = {"eq": lambda x, y: x == y, "larger": lambda x, y: x > y, "lesser": lambda x, y: x < y}[op]
        return f"{op} ( {a} {b} )", lambda e: py(fa(e), fb(e))
    if k == 3:
        s, f = cond(d - 1); return f"not ( {s} )", lambda e: not f(e)
    a, fa = cond(d - 1); b, fb = cond(d - 1)
    if k == 4: return f"and ( {a} {b} )", lambda e: fa(e) and fb(e)
    return f"or ( {a} {b} )", lambda e: fa(e) or fb(e)


def program():
    """Returns (spl_source, expected_output_lines)."""
    lines, out = [], []
    env = {v: 0 for v in VARS}
    for _ in range(rnd.randint(3, 8)):
        for v in ("#a", "#b", "#c"):
            x = rnd.randint(-3, 4); env[v] = x; lines.append(f"{v} = {x} ;")
        kind = rnd.randrange(5)
        c, f = cond(rnd.randint(1, 3))
        if kind == 0:                                      # branch
            lines.append(f'if {c} then {{ print "t" ; }} else {{ print "f" ; }} ;')
            out.append("t" if f(env) else "f")
            continue
        lines.append("#n = 0 ;")
        env["#n"] = 0
        inc = "#n = add ( #n 1 ) ;"
        if kind == 1:                                      # while B and n < 6
            lines.append(f"while and ( lesser ( #n 6 ) {c} ) do {{ {inc} }} ;")
            while env["#n"] < 6 and f(env): env["#n"] += 1
        elif kind == 2:                                    # until n > 5 or B
            lines.append(f"until or ( larger ( #n 5 ) {c} ) do {{ {inc} }} ;")
            while not (env["#n"] > 5 or f(env)): env["#n"] += 1
        elif kind == 3:                                    # do .. while B and n < 6
            lines.append(f"do {{ {inc} }} while and ( lesser ( #n 6 ) {c} ) ;")
            env["#n"] += 1
            while env["#n"] < 6 and f(env): env["#n"] += 1
        else:                                              # do .. until n > 5 or B
            lines.append(f"do {{ {inc} }} until or ( larger ( #n 5 ) {c} ) ;")
            env["#n"] += 1
            while not (env["#n"] > 5 or f(env)): env["#n"] += 1
        lines.append("print ( #n ) ;")
        out.append(str(env["#n"]))
    return "#a #b #c #n :\n:\n#n = 0 ;\n" + "\n".join(lines) + "\n", out


failed = 0
with tempfile.TemporaryDirectory() as work:
    for i in range(COUNT):
        src, expected = program()
        path = os.path.join(work, "prog.txt")
        open(path, "w").write(src)
        r = subprocess.run(["java", "-jar", JAR, path], cwd=work, capture_output=True, text=True)
        if r.returncode != 0:
            failed += 1; print(f"#{i}: compiler rejected the program\n{r.stderr}\n{src}"); continue
        c = subprocess.run(["g++", "-pedantic-errors", "output.cpp", "-o", "prog"], cwd=work, capture_output=True, text=True)
        if c.returncode != 0:
            failed += 1; print(f"#{i}: g++ failed\n{c.stderr[:500]}\n{src}"); continue
        got = subprocess.run(["./prog"], cwd=work, capture_output=True, text=True, timeout=5).stdout.split()
        if got != expected:
            failed += 1; print(f"#{i}: wrong output\n  expected {expected}\n  got      {got}\n{src}")
print(f"fuzz: {COUNT - failed}/{COUNT} programs correct")
sys.exit(1 if failed else 0)
