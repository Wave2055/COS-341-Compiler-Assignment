import java.util.*;

// Table SLR(1) shift-reduce parser that builds the syntax tree while it parses 
public class Parser {

    public static class Node {
        public int id; // assigned after parsing, in preorder
        public final String symbol; // non-terminal name, or the token's lexeme for leaves
        public final boolean leaf;
        public Node parent;
        public final List<Node> children = new ArrayList<>();
        Node(String symbol, boolean leaf) { this.symbol = symbol; this.leaf = leaf; }
    }

    public static class SyntaxError extends Exception {
        public SyntaxError(String msg) { super(msg); }
    }

    private final Grammar g;
    private final SLRTable table;

    public Parser(Grammar g, SLRTable table) { 
        this.g = g; this.table = table; 
    }

    public Node parse(List<Lexer.Token> tokens) throws SyntaxError {
        Deque<Integer> states = new ArrayDeque<>();
        Deque<Node> nodes = new ArrayDeque<>();
        states.push(0);
        int i = 0;

        while (true) {
            Lexer.Token tok = tokens.get(i);
            int s = states.peek();
            SLRTable.Action a = table.action.get(s).get(tok.terminal());

            if (a == null) throw error(states, tokens, i);

            switch (a.kind()) {
                case SHIFT -> {
                    nodes.push(new Node(tok.lexeme, true));
                    states.push(a.target());
                    i++;
                }
                case REDUCE -> {
                    Grammar.Production p = g.productions.get(a.target());
                    Node n = new Node(p.lhs, false);
                    for (int k = 0; k < p.rhs.size(); k++) {
                        states.pop();
                        Node child = nodes.pop();
                        child.parent = n;
                        n.children.add(0, child);          // popped right-to-left
                    }
                    nodes.push(n);
                    states.push(table.gotoTable.get(states.peek()).get(p.lhs));
                }
                case ACCEPT -> {
                    Node root = new Node(Grammar.START, false);
                    Node program = nodes.pop();
                    program.parent = root;
                    root.children.add(program);
                    assignIds(root);
                    return root;
                }
            }
        }
    }

    private static void assignIds(Node root) {
        int next = 1;
        Deque<Node> stack = new ArrayDeque<>(List.of(root));
        while (!stack.isEmpty()) {
            Node n = stack.pop();
            n.id = next++;
            for (int k = n.children.size() - 1; k >= 0; k--) stack.push(n.children.get(k));
        }
    }

    //errors
    private SyntaxError error(Deque<Integer> stack, List<Lexer.Token> tokens, int i) {
        Lexer.Token tok = tokens.get(i);
        Lexer.Token prev = i > 0 ? tokens.get(i - 1) : null;

        // In SLR the error is often detected only after some reductions (reduce actions
        // use the whole FOLLOW set). So instead of listing the raw table row, simulate
        // every terminal on a copy of the stack and keep only those that really lead to
        // a shift: that is the exact set of tokens that could legally come next.
        Map<String, Integer> expected = new TreeMap<>();   // terminal -> state it would shift from
        for (String t : g.terminals) {
            Integer from = shiftState(stack, t);
            if (from != null) expected.put(t, from);
        }

        StringBuilder m = new StringBuilder();
        if (tok.type == Lexer.TokenType.EOF)
            m.append("Syntax error: the file ended unexpectedly")
             .append(prev == null ? "." : " after " + prev + " (line " + prev.line + ", column " + prev.col + ").");
        else
            m.append(String.format("Syntax error at line %d, column %d: unexpected %s.", tok.line, tok.col, tok));
        m.append("\n  Expected: ").append(describe(expected.keySet()));

        SLRTable.Item ctx = expected.isEmpty() ? null : context(expected.values().iterator().next());
        if (ctx != null) m.append("\n  While parsing: ").append(table.itemToString(ctx).replace("\u2022", "^"));

        for (String h : hints(expected.keySet(), tok, prev, ctx)) m.append("\n  Hint: ").append(h);
        return new SyntaxError(m.toString());
    }

    /** Runs the reductions terminal t would trigger; returns the state t is shifted from, or null. */
    private Integer shiftState(Deque<Integer> real, String t) {
        Deque<Integer> st = new ArrayDeque<>(real);
        while (true) {
            SLRTable.Action a = table.action.get(st.peek()).get(t);
            if (a == null) return null;
            if (a.kind() != SLRTable.Kind.REDUCE) return st.peek();
            Grammar.Production p = g.productions.get(a.target());
            for (int k = 0; k < p.rhs.size(); k++) st.pop();
            st.push(table.gotoTable.get(st.peek()).get(p.lhs));
        }
    }

    private String describe(Set<String> expected) {
        List<String> out = new ArrayList<>();
        for (String t : expected) {
            switch (t) {
                case Grammar.NAME   -> out.add("a name (e.g. #x)");
                case Grammar.NUMBER -> out.add("a number");
                case Grammar.STRING -> out.add("a string (e.g. \"hello\")");
                case Grammar.EOF    -> out.add("end of file");
                default             -> out.add("'" + t + "'");
            }
        }
        return out.isEmpty() ? "(nothing)" : String.join(", ", out);
    }

    private SLRTable.Item context(int state) {
        SLRTable.Item best = null;
        for (SLRTable.Item it : table.states.get(state))
            if (it.dot() > 0 && it.prod() != 0 && (best == null || it.dot() > best.dot())) best = it;
        return best;
    }

    private List<String> hints(Set<String> exp, Lexer.Token tok, Lexer.Token prev, SLRTable.Item ctx) {
        List<String> h = new ArrayList<>();
        String t = tok.terminal();
        String lhs = ctx == null ? "" : g.productions.get(ctx.prod()).lhs;
        List<String> rhs = ctx == null ? List.of() : g.productions.get(ctx.prod()).rhs;
        String after = ctx != null && ctx.dot() > 0 ? rhs.get(ctx.dot() - 1) : "";

        if (exp.contains(":"))
            h.add("a program (and every function body) has the shape  <variables> : <functions> : <algorithm>"
                + "  - both ':' are required even if a section is empty, e.g.  : : print \"hi\" ; ");
        boolean infix = tok.type == Lexer.TokenType.KEYWORD && prev != null
                && Set.of("add", "sub", "mul", "div", "mod").contains(tok.lexeme)
                && (prev.type == Lexer.TokenType.NAME || prev.type == Lexer.TokenType.NUM || prev.lexeme.equals(")"));
        if (exp.equals(Set.of(";")) && !infix)
            h.add("every instruction must be followed by ' ; ' - including the last one before '}' or 'return'."
                + (prev != null ? " Probably missing after " + prev + " on line " + prev.line + "." : ""));
        if (exp.contains("else"))
            h.add("every 'if' needs an 'else' branch; write  else { }  if it should do nothing.");
        if (lhs.equals("F_TYPE") && after.equals("return") && rhs.get(0).equals("num"))
            h.add("a num function must end with  return ( <term> ) } , e.g.  return ( #result ) } .");
        if (lhs.equals("F_TYPE") && after.equals("return") && rhs.get(0).equals("void"))
            h.add("a void function ends with  return }  and returns no value.");
        if (lhs.equals("F_TYPE") && exp.contains("return") && !exp.contains(":"))
            h.add("a function body must finish with 'return' before its closing '}'.");
        if (t.equals("=") && prev != null && prev.type == Lexer.TokenType.KEYWORD)
            h.add("'" + prev.lexeme + "' is a reserved keyword and cannot be assigned to; user-defined names start with '#'.");
        else if (tok.type == Lexer.TokenType.KEYWORD && exp.contains(Grammar.NAME) && !exp.contains(tok.lexeme))
            h.add("'" + tok.lexeme + "' is a reserved keyword; user-defined names start with '#'.");
        if (lhs.equals("TERM") || lhs.equals("BOOL"))
            if (exp.contains("(") || exp.contains(")") || after.equals("("))
                h.add("operators use prefix notation with exactly the right number of arguments: "
                    + "add ( #a #b ) , neg ( #a ) , eq ( #a 0 ) , not ( eq ( #a 0 ) ) .");
        if (infix)
            h.add("SPL has no infix operators: write  " + tok.lexeme + " ( #a #b )  instead of  #a " + tok.lexeme + " #b .");
        if (lhs.equals("INSTR") && after.equals("print"))
            h.add("print takes a string or a bracketed term:  print \"text\"  or  print ( #x ) .");
        if (exp.contains("then"))
            h.add("write  if <condition> then { ... } else { ... } , e.g.  if eq ( #a 0 ) then { nop ; } else { } .");
        if (exp.contains("do") && exp.size() == 1)
            h.add("a pre-test loop is  while <condition> do { ... }  or  until <condition> do { ... } .");
        if (exp.equals(Set.of("while", "until")))
            h.add("a post-test loop is  do { ... } while <condition>  or  do { ... } until <condition> .");
        if (lhs.equals("BRANCH") || lhs.equals("LOOP"))
            if (after.equals("if") || after.equals("while") || after.equals("until") || lhs.equals("LOOP") && after.equals("COND"))
                h.add("conditions must be boolean operators: eq, larger, lesser, not, and, or - e.g.  larger ( #x 0 ) .");
        if (t.equals(Grammar.EOF) && prev != null)
            h.add("check that every '(' has a matching ')' and every '{' a matching '}'.");
        return h;
}