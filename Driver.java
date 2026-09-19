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
--
            if (c == ' ' || c == '\r') {
                pos++; col++;
                continue;
            }
            if (c == '\n') {
                pos++; line++; col = 1;
                continue;
            }

            
            System.out.println("First non-whitespace char at line " + line + ", col " + col + ": '" + c + "'");
            break;
        }

        System.out.println("Tokens collected so far: " + tokens.size());
    }
}