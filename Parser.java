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

    public Parser(Grammar g, SLRTable table) { this.g = g; this.table = table; }

}