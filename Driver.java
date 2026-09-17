import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

//Driver + Output. Reads SPL.txt

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

        System.out.println("Read " + input.length() + " characters from " + args[0]);
    }
}