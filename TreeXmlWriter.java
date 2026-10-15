import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Writes the syntax tree as tree.xml.
 *   ROOT  : UNID, CONTENTS, CHILDREN
 *   INNER : UNID, CONTENTS, CHILDREN, PARENT
 *   LEAF  : UNID, CONTENTS, PARENT
 * Nodes are listed in pre-order (the same order as their IDs).
 */
public class TreeXmlWriter {

    public static void write(Parser.Node root, Path out) throws IOException {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<SYNTAXTREE>\n");
        Deque<Parser.Node> stack = new ArrayDeque<>(List.of(root));
        while (!stack.isEmpty()) {
            Parser.Node n = stack.pop();
            String tag = n.parent == null ? "ROOT" : n.leaf ? "LEAF" : "INNER";
            x.append("  <").append(tag).append(">\n");
            x.append("    <UNID>").append(n.id).append("</UNID>\n");
            x.append("    <CONTENTS>").append(esc(n.symbol)).append("</CONTENTS>\n");
            if (!n.leaf) {
                x.append("    <CHILDREN>");
                for (Parser.Node c : n.children) x.append("<ID>").append(c.id).append("</ID>");
                x.append("</CHILDREN>\n");
            }
            if (n.parent != null) x.append("    <PARENT>").append(n.parent.id).append("</PARENT>\n");
            x.append("  </").append(tag).append(">\n");
            for (int k = n.children.size() - 1; k >= 0; k--) stack.push(n.children.get(k));
        }
        x.append("</SYNTAXTREE>\n");
        Files.writeString(out, x.toString());
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
