import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;



public class Driver {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Driver <path-to-SPL.txt>");
            System.exit(1);
        }

        String input;
        try {
            input = Files.readString(Path.of(args[0]));
        } catch (IOException e) {
            System.err.println("Could not read file: " + args[0] + " (" + e.getMessage() + ")");
            System.exit(1);
            return;
        }

        List<Lexer.Token> tokens = new ArrayList<>();
        int pos = 0, line = 1, col = 1;

        while (pos < input.length()) {
            char c = input.charAt(pos);

            
            if (c == ' ' || c == '\r') {
                pos++; col++;
                continue;
            }
            if (c == '\n') {
                pos++; line++; col = 1;
                continue;
            }

            // delegate the actual matching to lexer
            Lexer.Token token = Lexer.nextToken(input, pos, line, col); // LEXER_HOOK — unconfirmed contract

            if (token == null) {
                System.err.printf(
                    "Lexical error at line %d, col %d: illegal character '%c'%n",
                    line, col, c
                );
                System.exit(1);
                return;
            }

            tokens.add(token);

            // advance cursor past the matched lexeme
            for (int i = 0; i < token.value.length(); i++) {
                if (token.value.charAt(i) == '\n') {
                    line++; col = 1;
                } else {
                    col++;
                }
            }
            pos += token.value.length();

            if (token.type == Lexer.TokenType.EOF) {
                break;
            }
        }

        try {
            writeTokenXml(tokens, "token.xml");
        } catch (IOException e) {
            System.err.println("Could not write token.xml: " + e.getMessage());
            System.exit(1);
        }

        System.out.println("Wrote " + tokens.size() + " tokens to token.xml");
    }

   
    private static void writeTokenXml(List<Lexer.Token> tokens, String outPath) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<TOKENS>\n");
        for (Lexer.Token t : tokens) {
            xml.append("  <TOKEN>\n");
            xml.append("    <TYPE>").append(t.type).append("</TYPE>\n");
            xml.append("    <VALUE>").append(escapeXml(t.value.strip())).append("</VALUE>\n");
            xml.append("    <LINE>").append(t.line).append("</LINE>\n");
            xml.append("    <COL>").append(t.col).append("</COL>\n");
            xml.append("  </TOKEN>\n");
        }
        xml.append("</TOKENS>\n");
        Files.writeString(Path.of(outPath), xml.toString());
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}