import java.util.*;

/**
 * Phase 2a: semantic analysis for scopes and names. Crawls the syntax tree, checks
 * the SPL naming rules, and binds every user-defined name to a uniquely renamed
 * entity in the symbol table. All violations are collected and reported together.
 *
 * Scope levels follow Fig.1 of the spec: the main program P is level 0, and the
 * parameters and body P of a function declared at level l are at level l+1.
 */
public class ScopeAnalyser {

    public static class SemanticError extends Exception {
        public SemanticError(String msg) { super(msg); }
    }

    // One P node: its variables (parameters + V_DECL) and the functions of its F_DECL
    private static class Scope {
        final Scope outer;
        final int level;
        final SymbolTable.Entry owner;  // function this is the body of, null for the main program
        final Map<String, SymbolTable.Entry> vars = new HashMap<>();
        final Map<String, SymbolTable.Entry> funcs = new HashMap<>();
        Scope(Scope outer, SymbolTable.Entry owner) {
            this.outer = outer; this.owner = owner; this.level = outer == null ? 0 : outer.level + 1;
        }
    }

    private final SymbolTable table = new SymbolTable();
    private final List<String> errors = new ArrayList<>();

    public static SymbolTable analyse(Parser.Node root) throws SemanticError {
        ScopeAnalyser a = new ScopeAnalyser();
        a.program(root.children.get(0), null, null, null);   // SPL_PROG -> P
        if (!a.errors.isEmpty()) throw new SemanticError(String.join("\n", a.errors));
        return a.table;
    }

    /** P -> V_DECL : F_DECL : ALGO.  params is the function's ( V_DECL ), null for the main program. */
    private Scope program(Parser.Node p, Scope outer, SymbolTable.Entry owner, Parser.Node params) {
        Scope s = new Scope(outer, owner);
        if (params != null) for (Parser.Node n : names(params)) declareVariable(s, n, SymbolTable.Kind.PARAMETER);
        for (Parser.Node n : names(p.children.get(0))) declareVariable(s, n, SymbolTable.Kind.VARIABLE);

        // F_TYPE -> void NAME ( V_DECL ) { P return }  |  num NAME ( V_DECL ) { P return ( TERM ) }
        List<Parser.Node> functions = functions(p.children.get(2));
        List<SymbolTable.Entry> declared = new ArrayList<>();
        for (Parser.Node f : functions) declared.add(declareFunction(s, f));
        for (int k = 0; k < functions.size(); k++) {
            Parser.Node f = functions.get(k);
            Scope body = program(f.children.get(6), s, declared.get(k), f.children.get(3));
            if (f.children.size() > 9) resolve(f.children.get(9), body);   // the returned TERM
        }

        resolve(p.children.get(4), s);
        return s;
    }

    // ---- declarations ---------------------------------------------------------

    private void declareVariable(Scope s, Parser.Node n, SymbolTable.Kind kind) {
        SymbolTable.Entry prev = s.vars.get(n.symbol);
        SymbolTable.Entry e = table.declare(n, kind, s.level, s.owner);
        if (prev == null) { s.vars.put(n.symbol, e); return; }

        String where = " (first declared at line " + prev.decl.line + ", column " + prev.decl.col + ")";
        if (prev.kind == SymbolTable.Kind.PARAMETER && kind == SymbolTable.Kind.VARIABLE)
            error(n, "local variable '" + n.symbol + "' masks the parameter of the same name of function '"
                    + s.owner.name + "'" + where + ".",
                  "a function's local variables may not have the same names as its parameters; rename the local variable.");
        else if (kind == SymbolTable.Kind.PARAMETER)
            error(n, "parameter '" + n.symbol + "' appears twice in the parameter list of function '" + s.owner.name + "'" + where + ".",
                  "all parameters of a function must have different names.");
        else
            error(n, "variable '" + n.symbol + "' is declared twice at scope level " + s.level + where + ".",
                  "all variables declared at the same scope level must have different names.");
    }

    private SymbolTable.Entry declareFunction(Scope s, Parser.Node f) {
        Parser.Node n = f.children.get(1);
        SymbolTable.Entry prev = s.funcs.get(n.symbol);
        SymbolTable.Entry e = table.declare(n, SymbolTable.Kind.FUNCTION, s.level, s.owner);
        e.returnType = f.children.get(0).symbol;
        if (prev == null) s.funcs.put(n.symbol, e);
        else error(n, "function '" + n.symbol + "' is declared twice at scope level " + s.level
                    + " (first declared at line " + prev.decl.line + ", column " + prev.decl.col + ").",
                   "SPL has no overloading: all functions declared in the same function list must have different names.");
        return e;
    }

    // ---- uses -----------------------------------------------------------------

    /** Binds every name used inside an ALGO (or a returned TERM) to its declaration. */
    private void resolve(Parser.Node n, Scope s) {
        switch (n.symbol) {
            case "CALL" -> {                         // CALL -> NAME ( INPUT )
                useFunction(n.children.get(0), s);
                resolve(n.children.get(2), s);
                return;
            }
            case "ASSIGN" -> {                       // ASSIGN -> NAME = TERM
                useVariable(n.children.get(0), s);
                resolve(n.children.get(2), s);
                return;
            }
            case "TERM" -> {                         // TERM -> NAME
                Parser.Node c = n.children.get(0);
                if (c.leaf && isName(c)) { useVariable(c, s); return; }
            }
        }
        for (Parser.Node c : n.children) if (!c.leaf) resolve(c, s);
    }

    // The nearest declaration on the chain of enclosing scopes is the relevant one
    private void useVariable(Parser.Node n, Scope s) {
        for (Scope t = s; t != null; t = t.outer) {
            SymbolTable.Entry e = t.vars.get(n.symbol);
            if (e != null) { table.bind(n, e); return; }
        }
        boolean isFunction = false;
        for (Scope t = s; t != null; t = t.outer) isFunction |= t.funcs.containsKey(n.symbol);
        error(n, "variable '" + n.symbol + "' is used but has no declaration in scope.",
              isFunction
                ? "'" + n.symbol + "' is a function; call it with  " + n.symbol + " ( ... ) ."
                : "declare it in the variable list before the first ':' of this program or of an enclosing function, or as a parameter.");
    }

    // A function may only be called from the ALGO of the P whose own F_DECL declares it
    private void useFunction(Parser.Node n, Scope s) {
        SymbolTable.Entry e = s.funcs.get(n.symbol);
        if (e != null) { table.bind(n, e); return; }

        String msg = "call to function '" + n.symbol + "', which is not declared in the function list of this scope (level " + s.level + ").";
        for (Scope t = s; t.owner != null; t = t.outer)
            if (t.owner.name.equals(n.symbol)) {
                error(n, msg, t == s
                    ? "'" + n.symbol + "' calls itself; recursion is not allowed in SPL."
                    : "'" + n.symbol + "' is called from inside one of its own sub-functions; indirect recursion is not allowed in SPL.");
                return;
            }
        for (Scope t = s.outer; t != null; t = t.outer)
            if (t.funcs.containsKey(n.symbol)) {
                error(n, msg, "'" + n.symbol + "' is declared at scope level " + t.level
                    + ", but functions can only call their own sub-functions (no sibling or outer functions);"
                    + " declare a copy of it inside this function instead.");
                return;
            }
        boolean isVariable = false;
        for (Scope t = s; t != null; t = t.outer) isVariable |= t.vars.containsKey(n.symbol);
        error(n, msg, isVariable
            ? "'" + n.symbol + "' is a variable, not a function."
            : "declare the function between the two ':' of the program (or function body) that calls it.");
    }

    // ---- helpers --------------------------------------------------------------

    /** The NAME leaves of a V_DECL list (V_DECL -> NAME V_DECL | ε). */
    private static List<Parser.Node> names(Parser.Node vdecl) {
        List<Parser.Node> out = new ArrayList<>();
        for (Parser.Node v = vdecl; !v.children.isEmpty(); v = v.children.get(1)) out.add(v.children.get(0));
        return out;
    }

    /** The F_TYPE nodes of an F_DECL list (F_DECL -> F_TYPE F_DECL | ε). */
    private static List<Parser.Node> functions(Parser.Node fdecl) {
        List<Parser.Node> out = new ArrayList<>();
        for (Parser.Node f = fdecl; !f.children.isEmpty(); f = f.children.get(1)) out.add(f.children.get(0));
        return out;
    }

    private static boolean isName(Parser.Node leaf) { return leaf.symbol.startsWith("#"); }

    private void error(Parser.Node at, String msg, String hint) {
        errors.add(String.format("Semantic error at line %d, column %d: %s%n  Hint: %s", at.line, at.col, msg, hint));
    }
}
