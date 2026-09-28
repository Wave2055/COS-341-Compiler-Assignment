import java.util.*;

// The cfg, from spec
public class Grammar {

    // Terminal names for the three open lexical categories, and end-of-file
    public static final String NAME   = "USER-DEFINED-NAME";
    public static final String NUMBER = "NUM"; // keyword is lower-case "num"
    public static final String STRING = "STRING";
    public static final String EOF    = "$";

    public static final String START = "SPL_PROG";

    public static class Production {
        public final int index;
        public final String lhs;
        public final List<String> rhs; // empty list = epsilon
        Production(int index, String lhs, List<String> rhs) {
            this.index = index; this.lhs = lhs; this.rhs = rhs;
        }
        @Override public String toString() {
            return lhs + " -> " + (rhs.isEmpty() ? "ε" : String.join(" ", rhs));
        }
    }

    public final List<Production> productions = new ArrayList<>();
    public final Set<String> nonTerminals = new LinkedHashSet<>();
    public final Set<String> terminals = new LinkedHashSet<>();

    private void rule(String lhs, String rhs) {
        List<String> symbols = rhs.isBlank() ? List.of() : List.of(rhs.trim().split("\\s+"));
        productions.add(new Production(productions.size(), lhs, symbols));
    }

    public Grammar() {
        // Rule 0: "SPL_PROG -> P $", the $ is handled by the ACCEPT action
        rule("SPL_PROG", "P");

        rule("P", "V_DECL : F_DECL : ALGO");
        rule("V_DECL", "");
        rule("V_DECL", NAME + " V_DECL");
        rule("F_DECL", "");
        rule("F_DECL", "F_TYPE F_DECL");
        rule("F_TYPE", "void " + NAME + " ( V_DECL ) { P return }");
        rule("F_TYPE", "num "  + NAME + " ( V_DECL ) { P return ( TERM ) }");
        rule("ALGO", "");
        rule("ALGO", "INSTR ; ALGO");
        rule("OUTP", "( TERM )");
        rule("OUTP", STRING);
        rule("INSTR", "print OUTP");
        rule("INSTR", "nop");
        rule("INSTR", "comment " + STRING);
        rule("INSTR", "ASSIGN");
        rule("INSTR", "BRANCH");
        rule("INSTR", "LOOP");
        rule("INSTR", "CALL");
        rule("CALL", NAME + " ( INPUT )");
        rule("INPUT", "");
        rule("INPUT", "TERM INPUT");
        rule("ASSIGN", NAME + " = TERM");
        rule("TERM", NAME);
        rule("TERM", NUMBER);
        rule("TERM", "CALL");
        rule("TERM", "mod ( TERM TERM )");
        rule("TERM", "add ( TERM TERM )");
        rule("TERM", "sub ( TERM TERM )");
        rule("TERM", "mul ( TERM TERM )");
        rule("TERM", "div ( TERM TERM )");
        rule("TERM", "neg ( TERM )");
        rule("BRANCH", "if BOOL then { ALGO } else { ALGO }");
        rule("BOOL", "not ( BOOL )");
        rule("BOOL", "and ( BOOL BOOL )");
        rule("BOOL", "or ( BOOL BOOL )");
        rule("BOOL", "eq ( TERM TERM )");
        rule("BOOL", "larger ( TERM TERM )");
        rule("BOOL", "lesser ( TERM TERM )");
        rule("LOOP", "COND BOOL do { ALGO }");
        rule("LOOP", "do { ALGO } COND BOOL");
        rule("COND", "while");
        rule("COND", "until");

        for (Production p : productions) nonTerminals.add(p.lhs);
        for (Production p : productions)
            for (String s : p.rhs) if (!nonTerminals.contains(s)) terminals.add(s);
        terminals.add(EOF);
    }

    public boolean isTerminal(String s) { 
        return !nonTerminals.contains(s); 
    }
}
