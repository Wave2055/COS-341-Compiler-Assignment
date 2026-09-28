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
    

}