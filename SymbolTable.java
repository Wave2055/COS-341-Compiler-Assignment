import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * The static symbol table. Filled by the scope analyser (phase 2a): every declared
 * entity gets a unique system-generated name, and every user-defined name in the
 * syntax tree is bound to the entity it denotes. The type field starts out as
 * "unknown" and is filled in by the type analyser (phase 2b).
 */
public class SymbolTable {

    public enum Kind { VARIABLE, PARAMETER, FUNCTION }

    public static class Entry {
        public final String newName;     // unique internal name, e.g. v3 or f1
        public final String name;        // name as written by the user, e.g. #x
        public final Kind kind;
        public final int level;          // scope level of the declaration
        public final Entry owner;        // function whose body declares it, null for the main program
        public final Parser.Node decl;   // the NAME leaf of the declaration
        public String returnType;        // "num" or "void", for functions only
        public String type = "unknown";

        Entry(String newName, String name, Kind kind, int level, Entry owner, Parser.Node decl) {
            this.newName = newName; this.name = name; this.kind = kind;
            this.level = level; this.owner = owner; this.decl = decl;
        }

        @Override public String toString() { return newName + " (" + name + ")"; }
    }

    public final List<Entry> entries = new ArrayList<>();
    private final Map<Integer, Entry> byNode = new TreeMap<>(); // NAME leaf id -> entity
    private int vars = 0, funcs = 0;

    /** Creates a new entity declared by the given NAME leaf. */
    Entry declare(Parser.Node decl, Kind kind, int level, Entry owner) {
        String newName = kind == Kind.FUNCTION ? "f" + ++funcs : "v" + ++vars;
        Entry e = new Entry(newName, decl.symbol, kind, level, owner, decl);
        entries.add(e);
        bind(decl, e);
        return e;
    }

    /** Records that the NAME leaf n refers to entity e. */
    void bind(Parser.Node n, Entry e) { byNode.put(n.id, e); }

    /** The entity a NAME leaf refers to, or null if it was never resolved. */
    public Entry lookup(Parser.Node n) { return byNode.get(n.id); }

    public void write(Path out) throws IOException {
        StringBuilder s = new StringBuilder("SYMBOL TABLE\n\n");
        s.append(String.format("%-8s %-12s %-10s %-6s %-6s %-9s %-8s %s%n",
                "NEWNAME", "NAME", "KIND", "RETURN", "LEVEL", "SCOPE", "DECLARED", "TYPE"));
        for (Entry e : entries)
            s.append(String.format("%-8s %-12s %-10s %-6s %-6d %-9s %-8s %s%n",
                    e.newName, e.name, e.kind.name().toLowerCase(), e.returnType == null ? "-" : e.returnType,
                    e.level, e.owner == null ? "main" : e.owner.newName, e.decl.line + ":" + e.decl.col, e.type));
        s.append("\nNAME OCCURRENCES (tree node id -> new name)\n\n");
        for (Map.Entry<Integer, Entry> b : byNode.entrySet())
            s.append(String.format("%-8d %-12s %s%n", b.getKey(), b.getValue().name, b.getValue().newName));
        Files.writeString(out, s.toString());
    }
}
