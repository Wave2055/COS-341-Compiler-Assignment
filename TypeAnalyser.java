import java.util.*;

/**
 * Phase 2b: type analysis. A tree-crawler that applies the type rules of the spec
 * bottom-up: every node starts out "unknown" and becomes "ok", "numeric", "boolean"
 * or "procedure" once its rule is satisfied. The types of names are stored in the
 * symbol table built by phase 2a.
 *
 * An error is reported where a rule first fails because a child has the wrong type.
 * A node whose child is still "unknown" (because of an error already reported below
 * it) just stays "unknown" itself, so one mistake gives one message.
 */
public class TypeAnalyser {

    public static class TypeError extends Exception {
        public TypeError(String msg) { super(msg); }
    }

    public static final String UNKNOWN = "unknown", OK = "ok", NUMERIC = "numeric",
                               BOOLEAN = "boolean", PROCEDURE = "procedure";

    private final SymbolTable symbols;
    private final List<String> errors = new ArrayList<>();

    private TypeAnalyser(SymbolTable symbols) { this.symbols = symbols; }

    public static void analyse(Parser.Node root, SymbolTable symbols) throws TypeError {
        TypeAnalyser a = new TypeAnalyser(symbols);
        a.checkNumbers(root);                     // the crude mod/div rule rejects outright
        if (a.errors.isEmpty() && a.program(root.children.get(0)).equals(OK)) root.type = OK;
        if (!a.errors.isEmpty()) throw new TypeError(String.join("\n", a.errors));
        if (!root.type.equals(OK)) throw new TypeError("Type error: the program could not be typed as \"ok\".");
    }

    // ---- integers vs floats ---------------------------------------------------

    /** mod forces integers and div forces floats, so they may not appear in the same program. */
    private void checkNumbers(Parser.Node root) {
        List<Parser.Node> mods = new ArrayList<>(), divs = new ArrayList<>(), decimals = new ArrayList<>();
        collect(root, mods, divs, decimals);
        if (!mods.isEmpty() && !divs.isEmpty()) {
            Parser.Node m = mods.get(0), d = divs.get(0);
            errors.add(String.format("Type error: A float-integer-conflict might perhaps be possible.%n"
                + "  The program uses both mod (line %d, column %d) and div (line %d, column %d).%n"
                + "  Hint: mod works only on integers and div only on floating point numbers;"
                + " a program may use one of them, but not both.", m.line, m.col, d.line, d.col));
        } else if (!mods.isEmpty()) {
            Parser.Node m = mods.get(0);
            for (Parser.Node n : decimals)
                error(n, "the decimal number " + n.symbol + " is not allowed, because the program uses mod (line "
                        + m.line + ", column " + m.col + ") which works only on integers.",
                      "in a program that uses mod, every number must be an integer (no decimal point).");
        }
    }

    private static void collect(Parser.Node n, List<Parser.Node> mods, List<Parser.Node> divs, List<Parser.Node> decimals) {
        if (n.leaf) {
            if (n.parent.symbol.equals("TERM")) {
                if (n.symbol.equals("mod")) mods.add(n);
                else if (n.symbol.equals("div")) divs.add(n);
                else if (isNumber(n) && n.symbol.contains(".")) decimals.add(n);
            }
            return;
        }
        for (Parser.Node c : n.children) collect(c, mods, divs, decimals);
    }

    // ---- declarations ---------------------------------------------------------

    /** P -> V_DECL : F_DECL : ALGO */
    private String program(Parser.Node p) {
        String v = vdecl(p.children.get(0));      // variables first: the functions and ALGO use them
        String f = fdecl(p.children.get(2));      // functions next: ALGO calls them
        String a = algo(p.children.get(4));
        return set(p, all(OK, v, f, a) ? OK : UNKNOWN);
    }

    /** V_DECL -> ε | NAME V_DECL : every declared variable is numeric. */
    private String vdecl(Parser.Node v) {
        if (v.children.isEmpty()) return set(v, OK);
        if (!vdecl(v.children.get(1)).equals(OK)) return set(v, UNKNOWN);
        name(v.children.get(0), NUMERIC);
        return set(v, OK);
    }

    /** F_DECL -> ε | F_TYPE F_DECL */
    private String fdecl(Parser.Node f) {
        if (f.children.isEmpty()) return set(f, OK);
        String t = ftype(f.children.get(0)), rest = fdecl(f.children.get(1));
        return set(f, all(OK, t, rest) ? OK : UNKNOWN);
    }

    /** F_TYPE -> void NAME ( V_DECL ) { P return }  |  num NAME ( V_DECL ) { P return ( TERM ) } */
    private String ftype(Parser.Node f) {
        boolean num = f.children.get(0).symbol.equals("num");
        Parser.Node name = f.children.get(1);
        String params = vdecl(f.children.get(3));
        String body = program(f.children.get(6));
        boolean returns = !num || expect(term(f.children.get(9)), NUMERIC, f.children.get(9),
                "the value returned by " + name.symbol + " is not numeric.", "a num function must return a numeric term.");
        if (!all(OK, params, body) || !returns) return set(f, UNKNOWN);
        name(name, num ? NUMERIC : PROCEDURE);
        return set(f, OK);
    }

    // ---- instructions ---------------------------------------------------------

    /** ALGO -> ε | INSTR ; ALGO */
    private String algo(Parser.Node a) {
        if (a.children.isEmpty()) return set(a, OK);
        String i = instr(a.children.get(0)), rest = algo(a.children.get(2));
        return set(a, all(OK, i, rest) ? OK : UNKNOWN);
    }

    private String instr(Parser.Node i) {
        Parser.Node c = i.children.get(0);
        boolean ok = switch (c.symbol) {
            case "print" -> outp(i.children.get(1)).equals(OK);
            case "ASSIGN" -> assign(c).equals(OK);
            case "BRANCH" -> branch(c).equals(OK);
            case "LOOP" -> loop(c).equals(OK);
            case "CALL" -> expect(call(c), PROCEDURE, c.children.get(0),
                    "function " + c.children.get(0).symbol + " returns a value, so it cannot be called as an instruction.",
                    "use its result, e.g.  #x = " + c.children.get(0).symbol + " ( ... ) ;  or  print ( "
                        + c.children.get(0).symbol + " ( ... ) ) ;");
            default -> true;                     // nop, comment STRING
        };
        return set(i, ok ? OK : UNKNOWN);
    }

    /** OUTP -> ( TERM ) | STRING */
    private String outp(Parser.Node o) {
        if (o.children.size() == 1) return set(o, OK);
        boolean ok = expect(term(o.children.get(1)), NUMERIC, o.children.get(1),
                "only numeric terms can be printed.", "print a string or a numeric term.");
        return set(o, ok ? OK : UNKNOWN);
    }

    /** ASSIGN -> NAME = TERM */
    private String assign(Parser.Node a) {
        Parser.Node name = a.children.get(0);
        boolean t = expect(term(a.children.get(2)), NUMERIC, a.children.get(2),
                "the value assigned to " + name.symbol + " is not numeric.", "only numeric terms can be assigned.");
        boolean n = expect(nameType(name), NUMERIC, name,
                name.symbol + " is not a numeric variable, so it cannot be assigned to.", "only variables can be assigned to.");
        return set(a, t && n ? OK : UNKNOWN);
    }

    /** CALL -> NAME ( INPUT ) : the call has the type of the function. */
    private String call(Parser.Node c) {
        String in = input(c.children.get(2));
        String f = nameType(c.children.get(0));
        return set(c, in.equals(OK) && !f.equals(UNKNOWN) ? f : UNKNOWN);
    }

    /** INPUT -> ε | TERM INPUT : every argument is numeric. */
    private String input(Parser.Node i) {
        if (i.children.isEmpty()) return set(i, OK);
        boolean t = expect(term(i.children.get(0)), NUMERIC, i.children.get(0),
                "function arguments must be numeric.", "pass numeric terms as arguments.");
        boolean rest = input(i.children.get(1)).equals(OK);
        return set(i, t && rest ? OK : UNKNOWN);
    }

    /** BRANCH -> if BOOL then { ALGO } else { ALGO } */
    private String branch(Parser.Node b) {
        boolean cond = expect(bool(b.children.get(1)), BOOLEAN, b.children.get(1),
                "the condition of an if must be boolean.", "use eq, larger, lesser, not, and or or.");
        String thenPart = algo(b.children.get(4)), elsePart = algo(b.children.get(8));
        return set(b, cond && all(OK, thenPart, elsePart) ? OK : UNKNOWN);
    }

    /** LOOP -> COND BOOL do { ALGO }  |  do { ALGO } COND BOOL */
    private String loop(Parser.Node l) {
        boolean pre = l.children.get(0).symbol.equals("COND");
        String cond = set(l.children.get(pre ? 0 : 4), OK);       // COND -> while | until
        Parser.Node b = l.children.get(pre ? 1 : 5);
        boolean test = expect(bool(b), BOOLEAN, b,
                "the condition of a loop must be boolean.", "use eq, larger, lesser, not, and or or.");
        String body = algo(l.children.get(pre ? 4 : 2));
        return set(l, test && all(OK, cond, body) ? OK : UNKNOWN);
    }

    // ---- terms and conditions -------------------------------------------------

    private String term(Parser.Node t) {
        Parser.Node c = t.children.get(0);
        if (c.leaf && t.children.size() == 1) {              // TERM -> NAME | NUM
            if (isNumber(c)) return set(t, NUMERIC);
            boolean ok = expect(nameType(c), NUMERIC, c,
                    c.symbol + " is not a numeric variable.", "only numeric variables can be used in a term.");
            return set(t, ok ? NUMERIC : UNKNOWN);
        }
        if (c.symbol.equals("CALL")) {                        // TERM -> CALL
            Parser.Node f = c.children.get(0);
            boolean ok = expect(call(c), NUMERIC, f,
                    f.symbol + " is a void function: it returns no value, so it cannot be used as a term.",
                    "call it as an instruction on its own (" + f.symbol + " ( ... ) ;) or make it a num function.");
            return set(t, ok ? NUMERIC : UNKNOWN);
        }
        boolean ok = true;                                    // TERM -> op ( TERM TERM ) | neg ( TERM )
        for (Parser.Node arg : t.children)
            if (!arg.leaf) ok &= expect(term(arg), NUMERIC, arg,
                    "the operands of " + c.symbol + " must be numeric.", "use numeric terms as operands.");
        return set(t, ok ? NUMERIC : UNKNOWN);
    }

    private String bool(Parser.Node b) {
        String op = b.children.get(0).symbol;
        boolean compare = op.equals("eq") || op.equals("larger") || op.equals("lesser");
        boolean ok = true;
        for (Parser.Node arg : b.children)
            if (!arg.leaf) ok &= compare
                ? expect(term(arg), NUMERIC, arg, "the operands of " + op + " must be numeric.", "compare numeric terms.")
                : expect(bool(arg), BOOLEAN, arg, "the operands of " + op + " must be boolean.", "combine conditions only.");
        return set(b, ok ? BOOLEAN : UNKNOWN);
    }

    // ---- helpers --------------------------------------------------------------

    /** Gives a declared name its type, in the tree and in the symbol table. */
    private void name(Parser.Node n, String type) {
        n.type = type;
        symbols.lookup(n).type = type;
    }

    /** The type of a used name, looked up in the symbol table. */
    private String nameType(Parser.Node n) {
        SymbolTable.Entry e = symbols.lookup(n);
        return n.type = e == null ? UNKNOWN : e.type;
    }

    /** True if actual is wanted; reports an error unless actual is "unknown" (already reported). */
    private boolean expect(String actual, String wanted, Parser.Node at, String msg, String hint) {
        if (actual.equals(wanted)) return true;
        if (!actual.equals(UNKNOWN)) error(at, msg + " (found " + actual + ", expected " + wanted + ")", hint);
        return false;
    }

    private static boolean all(String wanted, String... types) {
        for (String t : types) if (!t.equals(wanted)) return false;
        return true;
    }

    private static String set(Parser.Node n, String type) { return n.type = type; }

    private static boolean isNumber(Parser.Node leaf) {
        char c = leaf.symbol.charAt(0);
        return Character.isDigit(c) || c == '-';
    }

    private static Parser.Node firstLeaf(Parser.Node n) {
        while (!n.leaf) n = n.children.get(0);
        return n;
    }

    private void error(Parser.Node at, String msg, String hint) {
        Parser.Node pos = firstLeaf(at);
        errors.add(String.format("Type error at line %d, column %d: %s%n  Hint: %s", pos.line, pos.col, msg, hint));
    }
}
