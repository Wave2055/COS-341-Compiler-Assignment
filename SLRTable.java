import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class SLRTable {

    // An LR(0) item: production index + dot position
    record Item(int prod, int dot) {}

    public enum Kind { SHIFT, REDUCE, ACCEPT }
    public record Action(Kind kind, int target) { // target = state (SHIFT) or production (REDUCE)
        @Override public String toString() {
            return switch (kind) { case SHIFT -> "s" + target; case REDUCE -> "r" + target; case ACCEPT -> "acc"; };
        }
    }

    public final Grammar g;
    public final List<Set<Item>> states = new ArrayList<>();
    public final List<Map<String, Action>> action = new ArrayList<>();
    public final List<Map<String, Integer>> gotoTable = new ArrayList<>();
    public final Map<String, Set<String>> first = new HashMap<>();
    public final Map<String, Set<String>> follow = new HashMap<>();
    public final Set<String> nullable = new HashSet<>();
    public final List<String> conflicts = new ArrayList<>();

    public SLRTable(Grammar g) {
        this.g = g;
        computeFirst();
        computeFollow();
        buildStates();
    }

    //FIRST/FOLLOW

    private void computeFirst() {
        for (String t : g.terminals) first.put(t, new HashSet<>(Set.of(t)));
        for (String n : g.nonTerminals) first.put(n, new HashSet<>());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Grammar.Production p : g.productions) 
                {
                Set<String> f = first.get(p.lhs);
                boolean allNullable = true;
                for (String s : p.rhs) 
                    {
                    changed |= f.addAll(first.get(s));
                    if (!nullable.contains(s)) { 
                        allNullable = false; break; 
                    }
                }
                if (allNullable) 
                    changed |= nullable.add(p.lhs);
            }
        }
    }

    private void computeFollow() {
        for (String n : g.nonTerminals) follow.put(n, new HashSet<>());
        follow.get(Grammar.START).add(Grammar.EOF);
        boolean changed = true;
        while (changed) 
            {
            changed = false;
            for (Grammar.Production p : g.productions) 
                {
                for (int i = 0; i < p.rhs.size(); i++) 
                    {
                    String b = p.rhs.get(i);
                    if (g.isTerminal(b)) continue;
                    boolean restNullable = true;
                    for (int j = i + 1; j < p.rhs.size(); j++) 
                        {
                        String s = p.rhs.get(j);
                        changed |= follow.get(b).addAll(first.get(s));
                        if (!nullable.contains(s)) { 
                            restNullable = false; break; 
                        }
                    }
                    if (restNullable) 
                        changed |= follow.get(b).addAll(follow.get(p.lhs));
                }
            }
        }
    }

    // LR(0) automaton

    private Set<Item> closure(Set<Item> items) {
        Set<Item> result = new LinkedHashSet<>(items);
        Deque<Item> work = new ArrayDeque<>(items);
        while (!work.isEmpty()) 
            {
            Item it = work.pop();
            List<String> rhs = g.productions.get(it.prod()).rhs;
            if (it.dot() < rhs.size() && !g.isTerminal(rhs.get(it.dot()))) 
                {
                String nt = rhs.get(it.dot());
                for (Grammar.Production p : g.productions)
                    if (p.lhs.equals(nt)) 
                        {
                        Item n = new Item(p.index, 0);
                        if (result.add(n)) 
                            work.push(n);
                    }
            }
        }
        return result;
    }

    private Set<Item> goTo(Set<Item> items, String x) {
        Set<Item> kernel = new LinkedHashSet<>();
        for (Item it : items) 
            {
            List<String> rhs = g.productions.get(it.prod()).rhs;
            if (it.dot() < rhs.size() && rhs.get(it.dot()).equals(x))
                kernel.add(new Item(it.prod(), it.dot() + 1));
        }
        return kernel.isEmpty() ? kernel : closure(kernel);
    }

    private void buildStates() {
        Map<Set<Item>, Integer> index = new HashMap<>();
        Set<Item> start = closure(Set.of(new Item(0, 0)));
        states.add(start); index.put(start, 0);

        List<String> symbols = new ArrayList<>(g.terminals);
        symbols.addAll(g.nonTerminals);

        for (int i = 0; i < states.size(); i++) 
            { // states grows while we iterate
            action.add(new TreeMap<>());
            gotoTable.add(new TreeMap<>());
            Set<Item> state = states.get(i);

            for (String x : symbols) 
                {
                if (x.equals(Grammar.EOF)) continue;
                Set<Item> target = goTo(state, x);
                if (target.isEmpty()) continue;
                Integer j = index.get(target);
                if (j == null) { j = states.size(); states.add(target); index.put(target, j); }
                if (g.isTerminal(x)) put(i, x, new Action(Kind.SHIFT, j));
                else gotoTable.get(i).put(x, j);
            }

            for (Item it : state) 
                {
                Grammar.Production p = g.productions.get(it.prod());
                if (it.dot() != p.rhs.size()) 
                    continue;
                if (p.index == 0)
                    put(i, Grammar.EOF, new Action(Kind.ACCEPT, 0));
                else for (String t : follow.get(p.lhs)) put(i, t, new Action(Kind.REDUCE, p.index));
            }
        }
    }

    private void put(int state, String terminal, Action a) {
        Action old = action.get(state).get(terminal);
        if (old != null && !old.equals(a)) 
            {
            conflicts.add("State " + state + " on '" + terminal + "': " + old + " vs " + a);
            return;
        }
        action.get(state).put(terminal, a);
    }

    // reporting

    public String itemToString(Item it) {
        Grammar.Production p = g.productions.get(it.prod());
        List<String> s = new ArrayList<>(p.rhs);
        s.add(it.dot(), "•");
        return p.lhs + " -> " + String.join(" ", s);
    }

    // Writes the grammar, FIRST/FOLLOW, all item sets and the full table as one HTML page
    public void writeHtml(Path out) throws IOException {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>SPL SLR(1) table</title><style>")
         .append("body{font-family:sans-serif;margin:20px}table{border-collapse:collapse;font-size:12px}")
         .append("td,th{border:1px solid #bbb;padding:2px 5px;text-align:center}th{background:#eee;position:sticky;top:0}")
         .append(".s{color:#1a5fb4}.r{color:#a51d2d}.a{color:#26a269;font-weight:bold}.g{background:#f6f6f6}")
         .append("pre{background:#f6f6f6;padding:8px}</style></head><body>");
        h.append("<h1>SPL SLR(1) parse table</h1><p>").append(states.size()).append(" states, ")
         .append(conflicts.isEmpty() ? "<b>no conflicts</b> — the grammar is SLR(1)." : conflicts.size() + " CONFLICTS")
         .append("</p><h2>Productions</h2><pre>");
        for (Grammar.Production p : g.productions) h.append(p.index).append(": ").append(esc(p.toString())).append('\n');
        h.append("</pre><h2>FIRST / FOLLOW</h2><table><tr><th>Non-terminal</th><th>Nullable</th><th>FIRST</th><th>FOLLOW</th></tr>");
        for (String n : g.nonTerminals)
            h.append("<tr><td>").append(n).append("</td><td>").append(nullable.contains(n) ? "yes" : "")
             .append("</td><td>").append(esc(String.join(" ", new TreeSet<>(first.get(n)))))
             .append("</td><td>").append(esc(String.join(" ", new TreeSet<>(follow.get(n))))).append("</td></tr>");
        h.append("</table><h2>ACTION / GOTO</h2><div style='overflow:auto;max-height:80vh'><table><tr><th>State</th>");
        for (String t : g.terminals) h.append("<th>").append(esc(t)).append("</th>");
        for (String n : g.nonTerminals) if (!n.equals(Grammar.START)) h.append("<th class='g'>").append(n).append("</th>");
        h.append("</tr>");
        for (int i = 0; i < states.size(); i++) {
            h.append("<tr><th>").append(i).append("</th>");
            for (String t : g.terminals) {
                Action a = action.get(i).get(t);
                String cls = a == null ? "" : a.kind() == Kind.SHIFT ? "s" : a.kind() == Kind.REDUCE ? "r" : "a";
                h.append("<td class='").append(cls).append("'>").append(a == null ? "" : a).append("</td>");
            }
            for (String n : g.nonTerminals) if (!n.equals(Grammar.START)) {
                Integer j = gotoTable.get(i).get(n);
                h.append("<td class='g'>").append(j == null ? "" : j).append("</td>");
            }
            h.append("</tr>");
        }
        h.append("</table></div><h2>LR(0) item sets</h2>");
        for (int i = 0; i < states.size(); i++) {
            h.append("<h3>I").append(i).append("</h3><pre>");
            for (Item it : states.get(i)) h.append(esc(itemToString(it))).append('\n');
            h.append("</pre>");
        }
        h.append("</body></html>");
        Files.writeString(out, h.toString());
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
