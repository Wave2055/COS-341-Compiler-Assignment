import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class Driver {

    public static void main(String[] args) {
        Grammar grammar = new Grammar();
        SLRTable table = new SLRTable(grammar);

        if (!table.conflicts.isEmpty()) {             // should never happen for SPL
            System.err.println("Internal error: the grammar is not SLR(1):");
            table.conflicts.forEach(c -> System.err.println("  " + c));
            System.exit(3);
        }

        if (args.length > 0 && args[0].equals("--table")) {
            try {
                table.writeHtml(Path.of("slr_table.html"));
                System.out.println("Wrote slr_table.html (" + table.states.size() + " states, no conflicts).");
            } catch (IOException e) { fail(1, "Could not write slr_table.html: " + e.getMessage()); }
            return;
        }

        Path in = Path.of(args.length > 0 ? args[0] : "SPL.txt");
        Path out = Path.of("tree.xml");
        String source;
        try {
            source = Files.readString(in);
        } catch (IOException e) {
            fail(1, "Could not read input file '" + in + "': " + e.getMessage()); return;
        }

        try {
            List<Lexer.Token> tokens = new Lexer(source).tokenize();
            Parser.Node root = new Parser(grammar, table).parse(tokens);
            TreeXmlWriter.write(root, out);
            System.out.println("Syntax OK. Wrote " + out + ".");
        } catch (Lexer.LexicalError | Parser.SyntaxError e) {
            deleteQuietly(out);                         // never leave a stale tree.xml behind
            fail(2, e.getMessage());
        } catch (IOException e) {
            fail(1, "Could not write " + out + ": " + e.getMessage());
        }
    }

    private static void deleteQuietly(Path p) {
        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
    }

    private static void fail(int code, String msg) {
        System.err.println(msg);
        System.exit(code);
    }
}
