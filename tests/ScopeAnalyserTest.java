import java.util.*;

/**
 * Unit tests for phase 2a (ScopeAnalyser + SymbolTable).
 * Compile and run against the compiler jar:
 *   javac -cp COCO-Group13.jar -d out tests/ScopeAnalyserTest.java
 *   java -cp COCO-Group13.jar:out ScopeAnalyserTest
 *
 * Renaming is checked with "patterns": for every occurrence of a name, in source
 * order, a letter saying which entity it is. "a b b a" means the 1st and 4th
 * occurrences are one entity and the 2nd and 3rd are another, different one.
 */
public class ScopeAnalyserTest {

    private static final Grammar GRAMMAR = new Grammar();
    private static final SLRTable TABLE = new SLRTable(GRAMMAR);
    private static int pass = 0, fail = 0;

    public static void main(String[] args) {
        // ---- variables -------------------------------------------------------
        test("global variable used in main", () -> {
            Result r = ok("#x : : #x = 1 ; print ( #x ) ;");
            expectPattern(r, "#x", "a a a");
            expectEntry(r, "#x", 0, SymbolTable.Kind.VARIABLE, 0, null);
        });
        test("parameter shadows global", () -> {
            Result r = ok("#x : num #f ( #x ) { : : nop ; return ( #x ) } : #x = #f ( #x ) ;");
            // decl(global), param, return term, main assign, main argument
            expectPattern(r, "#x", "a b b a a");
            expectEntry(r, "#x", 1, SymbolTable.Kind.PARAMETER, 1, "#f");
        });
        test("local shadows global, global still visible inside function", () -> {
            Result r = ok("#x #y : void #f ( ) { #x : : #x = #y ; return } : #x = 1 ; #f ( ) ;");
            expectPattern(r, "#x", "a b b a");
            expectPattern(r, "#y", "a a");
        });
        test("nearest of several enclosing declarations wins", () -> {
            Result r = ok("#v : void #f ( #v ) { : void #g ( ) { #v : : #v = 1 ; return } : #v = 2 ; #g ( ) ; return } : #v = 3 ; #f ( #v ) ;");
            // global, f's param, g's local, use in g, use in f, use in main x2
            expectPattern(r, "#v", "a b c c b a a");
        });
        test("deeply nested function sees every enclosing scope", () -> {
            Result r = ok("#a : void #f ( #b ) { : num #g ( #c ) { : : #a = add ( #b #c ) ; return ( #a ) } : #a = #g ( 1 ) ; return } : #f ( 2 ) ;");
            expectPattern(r, "#a", "a a a a");
            expectPattern(r, "#b", "a a");
            expectPattern(r, "#c", "a a");
            expectEntry(r, "#c", 0, SymbolTable.Kind.PARAMETER, 2, "#g");
        });
        test("same local name in two functions = two entities", () -> {
            Result r = ok(": void #f ( ) { #t : : #t = 1 ; return } void #g ( ) { #t : : #t = 2 ; return } : #f ( ) ; #g ( ) ;");
            expectPattern(r, "#t", "a a b b");
        });
        test("variable and function may share a name", () -> {
            Result r = ok("#f : num #f ( ) { : : nop ; return ( 1 ) } : #f = #f ( ) ;");
            expectPattern(r, "#f", "a b a b");
            expectEntry(r, "#f", 1, SymbolTable.Kind.FUNCTION, 0, null);
        });
        test("names inside branches and loops resolve in the enclosing P", () -> {
            Result r = ok("#i : : while lesser ( #i 3 ) do { if eq ( #i 1 ) then { #i = 2 ; } else { } ; } ; do { #i = add ( #i 1 ) ; } until larger ( #i 9 ) ;");
            expectPattern(r, "#i", "a a a a a a a");
        });
        test("undeclared variable", () -> err(": : #x = 1 ;", "line 1, column 5", "variable '#x' is used but has no declaration"));
        test("undeclared variable deep in a term", () -> err("#x : : #x = add ( 1 mul ( #x neg ( #q ) ) ) ;", "'#q'"));
        test("undeclared variable in boolean condition", () -> err("#i : : if eq ( #i #k ) then { } else { } ;", "'#k'"));
        test("undeclared variable in call argument", () -> err(": void #f ( #a ) { : : nop ; return } : #f ( #z ) ;", "'#z'"));
        test("undeclared variable in returned term", () -> err(": num #f ( ) { : : nop ; return ( #r ) } : #f ( ) ;", "'#r'"));
        test("function's local not visible in main", () -> err(": void #f ( ) { #t : : nop ; return } : #t = 1 ;", "'#t' is used but has no declaration"));
        test("function's parameter not visible in main", () -> err(": void #f ( #p ) { : : nop ; return } : print ( #p ) ;", "'#p'"));
        test("sibling function's local not visible", () -> err(": void #f ( ) { #t : : nop ; return } void #g ( ) { : : #t = 1 ; return } : nop ;", "'#t'"));
        test("inner function's local not visible in outer function", () ->
            err(": void #f ( ) { : void #g ( ) { #t : : nop ; return } : #t = 1 ; return } : nop ;", "'#t'"));
        test("duplicate global variable", () -> err("#x #y #x : : nop ;", "line 1, column 7", "declared twice at scope level 0"));
        test("duplicate local variable", () -> err(": void #f ( ) { #a #a : : nop ; return } : nop ;", "declared twice at scope level 1"));
        test("duplicate parameter", () -> err(": void #f ( #a #b #a ) { : : nop ; return } : nop ;", "parameter '#a' appears twice"));
        test("local masks parameter", () -> err(": void #f ( #a ) { #a : : nop ; return } : nop ;", "masks the parameter", "'#f'"));
        test("function used as variable gets a hint", () -> err(": num #f ( ) { : : nop ; return ( 1 ) } : print ( #f ) ;", "is a function"));

        // ---- functions -------------------------------------------------------
        test("call resolves to function in same level's F_DECL", () -> {
            Result r = ok(": void #f ( ) { : : nop ; return } : #f ( ) ; #f ( ) ;");
            expectPattern(r, "#f", "a a a");
        });
        test("helper written twice, once per caller = two entities", () -> {
            Result r = ok(": void #f ( ) { : void #h ( ) { : : nop ; return } : #h ( ) ; return }"
                        + " void #g ( ) { : void #h ( ) { : : nop ; return } : #h ( ) ; return } : #f ( ) ; #g ( ) ;");
            expectPattern(r, "#h", "a a b b");
        });
        test("call in returned term resolves to own sub-function", () -> {
            Result r = ok(": num #f ( ) { : num #g ( ) { : : nop ; return ( 1 ) } : nop ; return ( #g ( ) ) } : print ( #f ( ) ) ;");
            expectPattern(r, "#g", "a a");
        });
        test("function entry records return type", () -> {
            Result r = ok(": num #f ( ) { : : nop ; return ( 1 ) } void #g ( ) { : : nop ; return } : nop ;");
            check(entry(r, "#f", 0).returnType.equals("num"), "#f should be num");
            check(entry(r, "#g", 0).returnType.equals("void"), "#g should be void");
        });
        test("undeclared function", () -> err(": : #f ( ) ;", "call to function '#f'", "declare the function"));
        test("direct recursion", () -> err(": void #f ( ) { : : #f ( ) ; return } : #f ( ) ;", "calls itself"));
        test("recursion in returned term", () -> err(": num #f ( #n ) { : : nop ; return ( #f ( #n ) ) } : print ( #f ( 1 ) ) ;", "calls itself"));
        test("indirect recursion through sub-function", () ->
            err(": void #f ( ) { : void #g ( ) { : : #f ( ) ; return } : #g ( ) ; return } : #f ( ) ;", "indirect recursion"));
        test("call to sibling function", () ->
            err(": void #h ( ) { : : nop ; return } void #f ( ) { : : #h ( ) ; return } : #f ( ) ;", "declared at scope level 0", "own sub-functions"));
        test("main calls a nested function", () ->
            err(": void #f ( ) { : void #g ( ) { : : nop ; return } : #g ( ) ; return } : #g ( ) ;", "call to function '#g'"));
        test("duplicate function at level 0", () ->
            err(": void #f ( ) { : : nop ; return } num #f ( ) { : : nop ; return ( 1 ) } : nop ;", "function '#f' is declared twice"));
        test("duplicate nested function", () ->
            err(": void #o ( ) { : void #f ( ) { : : nop ; return } void #f ( ) { : : nop ; return } : nop ; return } : nop ;", "declared twice at scope level 1"));
        test("variable called as function", () -> err("#x : : #x ( ) ;", "is a variable, not a function"));

        // ---- reporting ---------------------------------------------------------
        test("all errors are reported, not just the first", () -> {
            String msg = errorOf("#a #a : : #b = 1 ; #c ( ) ;");
            check(msg.split("Semantic error").length - 1 == 3, "expected 3 errors, got:\n" + msg);
        });
        test("symbol table types start unknown", () -> {
            Result r = ok("#x : num #f ( #p ) { : : nop ; return ( #p ) } : #x = #f ( 1 ) ;");
            for (SymbolTable.Entry e : r.table.entries) check(e.type.equals("unknown"), e + " has type " + e.type);
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
            System.out.println("FAIL  " + name + "\n      " + t.getMessage().replace("\n", "\n      "));
        }
    }

    private static Parser.Node parse(String src) throws Exception {
        return new Parser(GRAMMAR, TABLE).parse(new Lexer(src).tokenize());
    }

    private static Result ok(String src) throws Exception {
        Parser.Node root = parse(src);
        try {
            return new Result(root, ScopeAnalyser.analyse(root));
        } catch (ScopeAnalyser.SemanticError e) {
            throw new AssertionError("unexpected semantic error:\n" + e.getMessage());
        }
    }

    private static String errorOf(String src) throws Exception {
        try {
            ScopeAnalyser.analyse(parse(src));
        } catch (ScopeAnalyser.SemanticError e) {
            return e.getMessage();
        }
        throw new AssertionError("expected a semantic error, but the program was accepted");
    }

    private static void err(String src, String... fragments) throws Exception {
        String msg = errorOf(src);
        check(msg.startsWith("Semantic error"), "message should start with 'Semantic error':\n" + msg);
        for (String f : fragments) check(msg.contains(f), "message lacks \"" + f + "\":\n" + msg);
    }

    private static List<Parser.Node> occurrences(Parser.Node n, String name, List<Parser.Node> out) {
        if (n.leaf && n.symbol.equals(name)) out.add(n);
        for (Parser.Node c : n.children) occurrences(c, name, out);
        return out;
    }

    private static void expectPattern(Result r, String name, String pattern) {
        List<Parser.Node> occ = occurrences(r.root, name, new ArrayList<>());
        String[] want = pattern.split(" ");
        check(occ.size() == want.length, name + ": expected " + want.length + " occurrences, found " + occ.size());
        Map<String, SymbolTable.Entry> letterToEntity = new HashMap<>();
        Map<SymbolTable.Entry, String> entityToLetter = new IdentityHashMap<>();
        List<String> got = new ArrayList<>();
        for (int k = 0; k < occ.size(); k++) {
            SymbolTable.Entry e = r.table.lookup(occ.get(k));
            check(e != null, name + " occurrence " + (k + 1) + " is not bound to any entity");
            got.add(e.newName);
            SymbolTable.Entry seen = letterToEntity.putIfAbsent(want[k], e);
            String letter = entityToLetter.putIfAbsent(e, want[k]);
            check((seen == null || seen == e) && (letter == null || letter.equals(want[k])),
                  name + ": expected pattern [" + pattern + "] but got " + got + "...");
        }
    }

    private static SymbolTable.Entry entry(Result r, String name, int occurrence) {
        return r.table.lookup(occurrences(r.root, name, new ArrayList<>()).get(occurrence));
    }

    private static void expectEntry(Result r, String name, int occurrence, SymbolTable.Kind kind, int level, String owner) {
        SymbolTable.Entry e = entry(r, name, occurrence);
        check(e.kind == kind, e + ": kind " + e.kind + ", expected " + kind);
        check(e.level == level, e + ": level " + e.level + ", expected " + level);
        String actualOwner = e.owner == null ? null : e.owner.name;
        check(Objects.equals(actualOwner, owner), e + ": owner " + actualOwner + ", expected " + owner);
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}
