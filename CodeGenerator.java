import java.util.*;
import java.util.regex.*;

/**
 * Phase 3: code generation 

 * Translates a type-checked syntax tree into human-legible, executable C++ that
 * follows the target syntax of the spec sheets: assignments, std::cout, labels,
 * goto and  if ( x op y ) goto L  - no else, no and / or / not

 * A code fragment is a String of statements separated by " ;\n" with NO trailing
 * semicolon; the caller appends it
 */
public class CodeGenerator {

    /** End every print with std::endl, so that consecutive prints do not run together. */
    public static final boolean NEWLINE_AFTER_PRINT = true;

    private static final String SEP = " ;\n";

    /** The code-mine entry of one function: everything needed to inline it. */
    private static class Mine {
        final List<String> params;  // internal names of the formal parameters, in order
        final String body;          // local declarations, body ALGO and the return computation
        final String result;        // the return variable r, null for a void procedure
        final Set<String> locals;   // every name the template declares: renamed on each copy
        Mine(List<String> params, String body, String result, Set<String> locals) {
            this.params = params; this.body = body; this.result = result; this.locals = locals;
        }
    }

    private final SymbolTable symbols;
    private final String typ; // int or float, for the whole program
    private final Map<SymbolTable.Entry, Mine> mines = new HashMap<>();
    private int temps = 0, labels = 0, inlines = 0;
    private Set<String> collector = null; // names declared by the template being built

    private CodeGenerator(SymbolTable symbols, String typ) { this.symbols = symbols; this.typ = typ; }

    public static String generate(Parser.Node root, SymbolTable symbols) {
        CodeGenerator g = new CodeGenerator(symbols, numberType(root));
        return g.program(root.children.get(0));
    }

    //  SPL_PROG and P 

    /** trans(SPL_PROG) for the main program P -> V_DECL : F_DECL : ALGO */
    private String program(Parser.Node p) {
        String mine = fdecl(p.children.get(2));// first: main's ALGO inlines from it
        String vars = vdecl(p.children.get(0));
        String algo = algo(p.children.get(4));

        StringBuilder s = new StringBuilder();
        s.append("/* begin of the non-executable code-mine for later inlining */\n");
        s.append(commentOut(mine)).append('\n');
        s.append("/* end of the non-executable code-mine */\n");
        s.append("/* begin of executable C code */\n");
        s.append("#include <iostream>\n");
        s.append("int main()\n");
        s.append("{\n");
        s.append(indent(vars)).append('\n');
        s.append(indent(algo)).append('\n');
        s.append("    return 0 ;\n");
        s.append("} /* end of executable C */\n");
        return s.toString();
    }

    /** V_DECL -> ε | NAME V_DECL :  typ internalName ; per declared name */
    private String vdecl(Parser.Node v) {
        StringBuilder s = new StringBuilder();
        for (String n : names(v)) s.append(typ).append(' ').append(n).append(SEP);
        return s.length() == 0 ? " " : s.toString();
    }

    /** F_DECL -> ε | F_TYPE F_DECL : the code-mine text of these functions */
    private String fdecl(Parser.Node f) {
        if (f.children.isEmpty()) return " ";
        return ftype(f.children.get(0)) + "\n" + fdecl(f.children.get(1));
    }

    /**
     * F_TYPE -> void NAME ( V_DECL ) { P return }  |  num NAME ( V_DECL ) { P return ( TERM ) }
     * Builds the inlining template of the function and returns its code-mine text.
     */
    private String ftype(Parser.Node f) {
        boolean num = f.children.get(0).symbol.equals("num");
        String name = symbols.lookup(f.children.get(1)).newName;
        Parser.Node params = f.children.get(3), body = f.children.get(6);

        Set<String> outer = collector;
        collector = new LinkedHashSet<>();
        List<String> paramNames = names(params);
        collector.addAll(paramNames);

        String nested = fdecl(body.children.get(2));// sub-functions first: inlined below
        collector.addAll(names(body.children.get(0)));
        String locals = vdecl(body.children.get(0)).strip();
        String code = (locals.isEmpty() ? "" : locals + "\n") + algo(body.children.get(4));

        String result = null;
        if (num) {
            result = name + "t" + ++temps;// r = f ++ n
            collector.add(result);
            code = join(code, typ + " " + result, term(f.children.get(9), result));
        }
        mines.put(symbols.lookup(f.children.get(1)), new Mine(paramNames, code, result, collector));
        collector = outer;

        String kind = num ? "Function " : "void-procedure ";
        StringBuilder s = new StringBuilder();
        s.append("begin of code-mine for ").append(kind).append(name).append('\n');
        s.append("Parameters for inlining:\n").append(vdecl(params).strip()).append('\n');
        s.append("Body of the procedure:\n");
        if (!nested.isBlank()) s.append(nested.strip()).append('\n');
        s.append(code).append('\n');
        s.append("end of code-mine for ").append(kind).append(name);
        if (num) s.append(":\nThe result is found in the return value ").append(result).append(':');
        return s.toString();
    }

    //  instructions 

    /** ALGO -> ε | INSTR ; ALGO */
    private String algo(Parser.Node a) {
        if (a.children.isEmpty()) return "/* end */";
        return instr(a.children.get(0)) + SEP + algo(a.children.get(2));
    }

    private String instr(Parser.Node i) {
        Parser.Node c = i.children.get(0);
        switch (c.symbol) {
            case "print": {
                Parser.Node out = i.children.get(1);
                if (out.children.size() == 1) return print(cString(out.children.get(0).symbol));
                String v = newTemp();
                return join(typ + " " + v, term(out.children.get(1), v), print(v));
            }
            case "nop":     return "/* nop */";
            case "comment": return "/* " + i.children.get(1).symbol + " */";
            case "ASSIGN": { // ASSIGN -> NAME = TERM
                String v = newTemp();
                String intern = symbols.lookup(c.children.get(0)).newName;
                return join(typ + " " + v, term(c.children.get(2), v), intern + " = " + v);
            }
            case "BRANCH":  return branch(c);
            case "LOOP":    return loop(c);
            case "CALL":    return inline(c, null);
            default: throw new IllegalStateException("unexpected instruction " + c.symbol);
        }
    }

    private static String print(String what) {
        return "std::cout << " + what + (NEWLINE_AFTER_PRINT ? " << std::endl" : "");
    }

    /** BRANCH -> if BOOL then { ALGO } else { ALGO } : no else needed in the target code */
    private String branch(Parser.Node b) {
        String la = newLabel(), lb = newLabel(), lc = newLabel();
        String decision = bool(b.children.get(1), la, lb);
        String trueCode = algo(b.children.get(4));
        String falseCode = algo(b.children.get(8));
        return "/* if */\n"
             + decision + SEP
             + lb + " : /* else */\n"
             + falseCode + SEP
             + "goto " + lc + SEP
             + la + " : /* then */\n"
             + trueCode + SEP
             + lc + " : /* continue */";
    }

    /** LOOP -> COND BOOL do { ALGO }  |  do { ALGO } COND BOOL */
    private String loop(Parser.Node l) {
        boolean pre = l.children.get(0).symbol.equals("COND");
        boolean until = l.children.get(pre ? 0 : 4).children.get(0).symbol.equals("until");
        Parser.Node cond = l.children.get(pre ? 1 : 5);

        if (pre) {
            String ls = newLabel(), le = newLabel(), d = newLabel();
            // while B do { .. }  ==  until not ( B ) do { .. }  ;  trans(not B, le, d) = trans(B, d, le)
            String decision = until ? bool(cond, le, d) : bool(cond, d, le);
            String body = algo(l.children.get(4));
            return ls + " : /* " + (until ? "until" : "while") + " */\n"
                 + decision + SEP
                 + d + " : /* do */\n"
                 + body + SEP
                 + "goto " + ls + SEP
                 + le + " :\n"
                 + "/* exit */";
        }
        String ld = newLabel(), le = newLabel();
        // do { .. } until B  ==  do { .. } while not ( B )  ;  trans(not B, ld, le) = trans(B, le, ld)
        String decision = until ? bool(cond, le, ld) : bool(cond, ld, le);
        String body = algo(l.children.get(2));
        return ld + " : /* do */\n"
             + body + SEP
             + "/* " + (until ? "until" : "while") + " */\n"
             + decision + SEP
             + le + " : /* exit */";
    }

    //  conditions 

    /**
     * trans(BOOL, t, f): code that jumps to t if the condition is true and to f if it
     * is false - it never falls through. This is Fig.6.8 of the textbook with every
     * "IF c THEN t ELSE f" written as  if ( c ) goto t ; goto f  , so "not" is just
     * swapping the two labels, and and / or need one intermediate label.
     */
    private String bool(Parser.Node b, String t, String f) {
        String op = b.children.get(0).symbol;
        switch (op) {
            case "not":
                return bool(b.children.get(2), f, t);
            case "or": { // first true -> t, else try the second
                String next = newLabel();
                return bool(b.children.get(2), t, next) + SEP
                     + next + " : /* or */\n"
                     + bool(b.children.get(3), t, f);
            }
            case "and": { // first false -> f, else try the second
                String next = newLabel();
                return bool(b.children.get(2), next, f) + SEP
                     + next + " : /* and */\n"
                     + bool(b.children.get(3), t, f);
            }
            default: { // eq | larger | lesser ( TERM TERM )
                String cmp = op.equals("eq") ? "==" : op.equals("larger") ? ">" : "<";
                String x = newTemp(), y = newTemp();
                return join(typ + " " + x, term(b.children.get(2), x),
                            typ + " " + y, term(b.children.get(3), y),
                            "if ( " + x + " " + cmp + " " + y + " ) goto " + t,
                            "goto " + f);
            }
        }
    }

    //  terms 

    /** trans(TERM, place): code that leaves the value of the term in place (Fig.6.3). */
    private String term(Parser.Node t, String place) {
        Parser.Node c = t.children.get(0);
        if (c.leaf && t.children.size() == 1) // TERM -> NAME | NUM
            return place + " = " + (isName(c) ? symbols.lookup(c).newName : c.symbol);
        if (c.symbol.equals("CALL")) return inline(c, place); // TERM -> CALL

        if (c.symbol.equals("neg")) { // TERM -> neg ( TERM )
            String p1 = newTemp();
            return join(typ + " " + p1, term(t.children.get(2), p1), place + " = - " + p1);
        }
        String p1 = newTemp(), p2 = newTemp(); // TERM -> op ( TERM TERM )
        return join(typ + " " + p1, typ + " " + p2,
                    term(t.children.get(2), p1), term(t.children.get(3), p2),
                    place + " = " + p1 + " " + binop(c.symbol) + " " + p2);
    }

    private static String binop(String op) {
        switch (op) {
            case "mod": return "%";
            case "div": return "/";
            case "add": return "+";
            case "sub": return "-";
            case "mul": return "*";
            default: throw new IllegalStateException("unexpected operator " + op);
        }
    }

    //  calls: inlining -

    /**
     * CALL -> NAME ( INPUT ). Evaluates the arguments into fresh variables p1..pN,
     * copies the function's template out of the code-mine with all its own names
     * renamed (suffix _k for the k-th inlining), assigns the parameters, and finally
     * connects the return value r with the caller's continuation variable c.
     */
    private String inline(Parser.Node call, String cont) {
        SymbolTable.Entry f = symbols.lookup(call.children.get(0));
        Mine m = mines.get(f);
        List<String> code = new ArrayList<>();
        code.add("/* begin inlining " + f.newName + " */");

        List<String> ps = new ArrayList<>(); // evaluated in the caller's context
        for (Parser.Node arg : terms(call.children.get(2))) {
            String p = newTemp();
            ps.add(p);
            code.add(typ + " " + p);
            code.add(term(arg, p));
        }

        String suffix = "_" + ++inlines;
        Map<String, String> ren = new HashMap<>();
        for (String n : m.locals) ren.put(n, n + suffix);
        if (collector != null) collector.addAll(ren.values()); // inlined into a template: rename again later

        for (String a : m.params) code.add(typ + " " + ren.get(a));
        for (int k = 0; k < Math.min(ps.size(), m.params.size()); k++)
            code.add(ren.get(m.params.get(k)) + " = " + ps.get(k));
        code.add(rename(m.body, ren));
        if (cont != null && m.result != null) code.add(cont + " = " + ren.get(m.result));
        code.add("/* end inlining " + f.newName + " */");
        return String.join(SEP, code);
    }

    // identifiers outside string literals and comments
    private static final Pattern TOKENS = Pattern.compile("\"[^\"]*\"|/\\*.*?\\*/|[A-Za-z_][A-Za-z0-9_]*", Pattern.DOTALL);

    private static String rename(String code, Map<String, String> ren) {
        Matcher m = TOKENS.matcher(code);
        StringBuilder s = new StringBuilder();
        while (m.find()) {
            String tok = m.group();
            m.appendReplacement(s, Matcher.quoteReplacement(ren.getOrDefault(tok, tok)));
        }
        m.appendTail(s);
        return s.toString();
    }

    //  helpers 

    private String newTemp() {
        String t = "t" + ++temps;
        if (collector != null) collector.add(t);
        return t;
    }

    private String newLabel() {
        String l = "L" + ++labels;
        if (collector != null) collector.add(l);
        return l;
    }

    /** Joins code fragments with " ;\n" (no trailing semicolon, the caller adds it). */
    private static String join(String... parts) {
        return String.join(SEP, parts);
    }

    /** Internal names of the variables of a V_DECL list. */
    private List<String> names(Parser.Node v) {
        List<String> out = new ArrayList<>();
        for (; !v.children.isEmpty(); v = v.children.get(1)) out.add(symbols.lookup(v.children.get(0)).newName);
        return out;
    }

    /** The TERM children of an INPUT list. */
    private static List<Parser.Node> terms(Parser.Node in) {
        List<Parser.Node> out = new ArrayList<>();
        for (; !in.children.isEmpty(); in = in.children.get(1)) out.add(in.children.get(0));
        return out;
    }

    private static boolean isName(Parser.Node leaf) { return leaf.symbol.startsWith("#"); }

    /** SPL strings are already valid C string literals; only "??" could form a trigraph. */
    private static String cString(String s) {
        return s.contains("??") ? s.replace("?", "\\?") : s;
    }

    /**
     * int or float for the whole program, as decided by the type analysis rules of 2b:
     * mod forces integers, div or any decimal number forces floats (mod together with
     * div or decimals was already rejected by the type analyser). Otherwise int.
     */
    private static String numberType(Parser.Node root) {
        boolean[] seen = new boolean[3];// mod, div, decimal
        scan(root, seen);
        if (seen[0]) return "int";
        return seen[1] || seen[2] ? "float" : "int";
    }

    private static void scan(Parser.Node n, boolean[] seen) {
        if (n.leaf) {
            if (n.parent.symbol.equals("TERM")) {
                if (n.symbol.equals("mod")) seen[0] = true;
                else if (n.symbol.equals("div")) seen[1] = true;
                else if (!isName(n) && n.symbol.contains(".")) seen[2] = true;
            }
            return;
        }
        for (Parser.Node c : n.children) scan(c, seen);
    }

    /** The code-mine is not executable: every line becomes a // comment */
    private static String commentOut(String text) {
        StringBuilder s = new StringBuilder();
        for (String line : text.strip().split("\n", -1))
            s.append(line.isBlank() ? "//" : "// " + line.stripTrailing()).append('\n');
        return s.length() == 0 ? "//" : s.substring(0, s.length() - 1);
    }

    private static String indent(String text) {
        StringBuilder s = new StringBuilder();
        for (String line : text.strip().split("\n", -1))
            if (!line.isBlank()) s.append("    ").append(line.stripTrailing()).append('\n');
        return s.length() == 0 ? "" : s.substring(0, s.length() - 1);
    }
}
