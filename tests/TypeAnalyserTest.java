import java.util.*;

/**
 * Unit tests for phase 2b (TypeAnalyser).
 * Compile and run against the compiler jar:
 *   javac -cp COCO-Group13.jar -d out tests/TypeAnalyserTest.java
 *   java -cp COCO-Group13.jar:out TypeAnalyserTest
 */
public class TypeAnalyserTest {

    private static final Grammar GRAMMAR = new Grammar();
    private static final SLRTable TABLE = new SLRTable(GRAMMAR);
    private static int pass = 0, fail = 0;

    public static void main(String[] args) {
        // ---- well-typed programs ----------------------------------------------
        test("empty program is ok", () -> {
            Result r = ok(": :");
            expectNode(r, "SPL_PROG", 0, "ok");
            expectNode(r, "P", 0, "ok");
        });
        test("variables and parameters are numeric", () -> {
            Result r = ok("#x : void #f ( #p ) { #l : : nop ; return } : nop ;");
            expectSymbol(r, "#x", "numeric");
            expectSymbol(r, "#p", "numeric");
            expectSymbol(r, "#l", "numeric");
        });
        test("num function is numeric, void function is procedure", () -> {
            Result r = ok(": num #n ( ) { : : nop ; return ( 1 ) } void #v ( ) { : : nop ; return } : nop ;");
            expectSymbol(r, "#n", "numeric");
            expectSymbol(r, "#v", "procedure");
        });
        test("nested function types are recorded", () -> {
            Result r = ok(": void #o ( ) { : num #i ( #a ) { : : nop ; return ( #a ) } : print ( #i ( 1 ) ) ; return } : #o ( ) ;");
            expectSymbol(r, "#i", "numeric");
            expectSymbol(r, "#o", "procedure");
        });
        test("terms are numeric", () -> {
            Result r = ok("#x : : #x = add ( 1 sub ( #x mul ( 2 neg ( #x ) ) ) ) ;");
            for (Parser.Node t : nodes(r.root, "TERM")) check(t.type.equals("numeric"), "TERM " + t.id + " is " + t.type);
        });
        test("conditions are boolean", () -> {
            Result r = ok("#x : : if and ( not ( eq ( #x 1 ) ) or ( larger ( #x 2 ) lesser ( #x 3 ) ) ) then { nop ; } else { nop ; } ;");
            for (Parser.Node b : nodes(r.root, "BOOL")) check(b.type.equals("boolean"), "BOOL " + b.id + " is " + b.type);
        });
        test("instructions, branches and loops are ok", () -> {
            Result r = ok("#i : : while lesser ( #i 3 ) do { #i = add ( #i 1 ) ; } ; do { comment \"x\" ; } until eq ( #i 0 ) ;"
                        + " if eq ( #i 1 ) then { print \"a\" ; } else { print ( #i ) ; } ;");
            for (String sym : List.of("INSTR", "ALGO", "LOOP", "COND", "BRANCH", "ASSIGN", "OUTP"))
                for (Parser.Node n : nodes(r.root, sym)) check(n.type.equals("ok"), sym + " " + n.id + " is " + n.type);
        });
        test("call types: void call instruction is procedure, num call term is numeric", () -> {
            Result r = ok("#x : num #n ( ) { : : nop ; return ( 1 ) } void #v ( ) { : : nop ; return } : #v ( ) ; #x = #n ( ) ;");
            List<Parser.Node> calls = nodes(r.root, "CALL");
            check(calls.get(0).type.equals("procedure"), "first call is " + calls.get(0).type);
            check(calls.get(1).type.equals("numeric"), "second call is " + calls.get(1).type);
        });
        test("num calls inside terms, arguments and conditions", () ->
            ok("#x : num #sq ( #a ) { : : nop ; return ( mul ( #a #a ) ) } : #x = #sq ( #sq ( 2 ) ) ;"
             + " while larger ( #sq ( #x ) 1 ) do { #x = div ( #x 2 ) ; } ;"));
        test("mod with integers only", () -> ok("#x : : #x = mod ( 17 -5 ) ; #x = add ( #x 0 ) ;"));
        test("div with decimals", () -> ok("#x : : #x = div ( 1.5 -0.25 ) ;"));
        test("symbol table has no unknown types left", () -> {
            Result r = ok("#g : num #f ( #p ) { #l : void #h ( ) { : : nop ; return } : #h ( ) ; return ( #p ) } : #g = #f ( 1 ) ;");
            for (SymbolTable.Entry e : r.table.entries) check(!e.type.equals("unknown"), e + " is still unknown");
        });

        // ---- the crude mod/div rule -------------------------------------------
        test("mod and div together", () -> err("#a : : #a = mod ( 7 2 ) ; #a = div ( 7 2 ) ;",
                "A float-integer-conflict might perhaps be possible", "line 1, column 13", "line 1, column 32"));
        test("mod and div in different functions", () -> err(
                ": num #f ( ) { : : nop ; return ( mod ( 4 3 ) ) } num #g ( ) { : : nop ; return ( div ( 4 3 ) ) } : nop ;",
                "float-integer-conflict"));
        test("mod/div conflict rejects outright, no other errors", () -> {
            String msg = errorOf("#a : void #v ( ) { : : nop ; return } : #a = mod ( 7 2 ) ; #a = div ( 7 2 ) ; #a = #v ( ) ;");
            check(count(msg, "Type error") == 1, "expected only the conflict error:\n" + msg);
        });
        test("mod with a decimal", () -> err("#a : : #a = mod ( 7.5 2 ) ;", "decimal number 7.5", "line 1, column 19"));
        test("mod with a decimal elsewhere in the program", () -> err("#a : : #a = mod ( 7 2 ) ; print ( -0.25 ) ;", "-0.25"));
        test("each decimal is reported", () -> {
            String msg = errorOf("#a : : #a = mod ( 7 2 ) ; #a = add ( 0.5 1.5 ) ;");
            check(count(msg, "Type error") == 2, "expected 2 errors:\n" + msg);
        });

        // ---- calls ------------------------------------------------------------
        test("num function called as instruction", () ->
            err(": num #f ( ) { : : nop ; return ( 1 ) } : #f ( ) ;", "returns a value", "found numeric, expected procedure"));
        test("void function assigned", () ->
            err("#x : void #f ( ) { : : nop ; return } : #x = #f ( ) ;", "void function", "found procedure, expected numeric"));
        test("void function printed", () -> err(": void #f ( ) { : : nop ; return } : print ( #f ( ) ) ;", "#f is a void function"));
        test("void function as argument", () ->
            err(": void #f ( ) { : : nop ; return } void #g ( #a ) { : : nop ; return } : #g ( #f ( ) ) ;", "#f is a void function"));
        test("void function in comparison", () ->
            err(": void #f ( ) { : : nop ; return } : if larger ( 1 #f ( ) ) then { } else { } ;", "#f is a void function"));
        test("void function in arithmetic", () ->
            err("#x : void #f ( ) { : : nop ; return } : #x = neg ( #f ( ) ) ;", "#f is a void function", "line 1, column 52"));
        test("void function returned by num function", () ->
            err(": num #f ( ) { : void #g ( ) { : : nop ; return } : nop ; return ( #g ( ) ) } : nop ;", "#g is a void function"));

        // ---- reporting --------------------------------------------------------
        test("independent errors are all reported", () -> {
            String msg = errorOf("#x : void #v ( ) { : : nop ; return } num #n ( ) { : : nop ; return ( 1 ) } : #x = #v ( ) ; #n ( ) ;");
            check(count(msg, "Type error") == 2, "expected 2 errors:\n" + msg);
        });
        test("one error is not repeated by the enclosing nodes", () -> {
            String msg = errorOf("#x : void #v ( ) { : : nop ; return } : if eq ( add ( 1 #v ( ) ) 2 ) then { #x = 1 ; } else { } ;");
            check(count(msg, "Type error") == 1, "expected 1 error:\n" + msg);
        });
        test("error inside a function body leaves the program untyped", () -> {
            Parser.Node root = parse(": void #f ( ) { #x : void #v ( ) { : : nop ; return } : #x = #v ( ) ; return } : nop ;");
            try { TypeAnalyser.analyse(root, ScopeAnalyser.analyse(root)); } catch (TypeAnalyser.TypeError expected) { }
            check(root.type.equals("unknown"), "SPL_PROG should stay unknown, is " + root.type);
        });

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    // ---- harness ----------------------------------------------------------------

    private record Result(Parser.Node root, SymbolTable table) {}

    private interface Body { void run() throws Exception; }

    private static void test(String name, Body body) {
        try {
            body.run();
            pass++;
            System.out.println("PASS  " + name);
        } catch (Throwable t) {
            fail++;
            System.out.println("FAIL  " + name + "\n      " + String.valueOf(t.getMessage()).replace("\n", "\n      "));
        }
    }

    private static Parser.Node parse(String src) throws Exception {
        return new Parser(GRAMMAR, TABLE).parse(new Lexer(src).tokenize());
    }

    private static Result ok(String src) throws Exception {
        Parser.Node root = parse(src);
        SymbolTable table = ScopeAnalyser.analyse(root);
        try {
            TypeAnalyser.analyse(root, table);
        } catch (TypeAnalyser.TypeError e) {
            throw new AssertionError("unexpected type error:\n" + e.getMessage());
        }
        check(root.type.equals("ok"), "SPL_PROG is " + root.type + ", expected ok");
        return new Result(root, table);
    }

    private static String errorOf(String src) throws Exception {
        Parser.Node root = parse(src);
        SymbolTable table = ScopeAnalyser.analyse(root);   // these programs are all scope-correct
        try {
            TypeAnalyser.analyse(root, table);
        } catch (TypeAnalyser.TypeError e) {
            return e.getMessage();
        }
        throw new AssertionError("expected a type error, but the program was accepted");
    }

    private static void err(String src, String... fragments) throws Exception {
        String msg = errorOf(src);
        check(msg.startsWith("Type error"), "message should start with 'Type error':\n" + msg);
        for (String f : fragments) check(msg.contains(f), "message lacks \"" + f + "\":\n" + msg);
    }

    private static List<Parser.Node> nodes(Parser.Node n, String symbol) {
        List<Parser.Node> out = new ArrayList<>();
        Deque<Parser.Node> stack = new ArrayDeque<>(List.of(n));
        while (!stack.isEmpty()) {
            Parser.Node m = stack.pop();
            if (!m.leaf && m.symbol.equals(symbol)) out.add(m);
            for (int k = m.children.size() - 1; k >= 0; k--) stack.push(m.children.get(k));
        }
        return out;
    }

    private static void expectNode(Result r, String symbol, int index, String type) {
        Parser.Node n = nodes(r.root, symbol).get(index);
        check(n.type.equals(type), symbol + " #" + index + " is " + n.type + ", expected " + type);
    }

    private static void expectSymbol(Result r, String name, String type) {
        for (SymbolTable.Entry e : r.table.entries)
            if (e.name.equals(name)) {
                check(e.type.equals(type), e + " is " + e.type + ", expected " + type);
                return;
            }
        throw new AssertionError("no symbol " + name);
    }

    private static int count(String s, String sub) { return s.split(sub, -1).length - 1; }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}
