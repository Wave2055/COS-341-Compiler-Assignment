
import java.util.Set;
import java.util.regex.Pattern;

public class Lexer {

    // Assumed to keep the space in the context node (Option A)

    public enum TokenType {
        KEYWORD, 
        USER_DEFINED_NAME,
        NUM,
        STRING,
        LPARENTHESIS, // (
        RPARENTHESIS, // )
        L_BRACE,      // {
        R_BRACE,      // }
        SEMICOLON,    // ;
        COLON,        // :
        ASSIGN,       // =
        EOF           // $
    }

    public static class Token {
        public final TokenType type;
        public final String value;
        public final int line;
        public final int col;

        public Token(TokenType type, String value, int line, int col) {
            this.type = type;
            this.value = value;
            this.line = line;
            this.col = col;
        }

        // Prevents multi-line string pollution in your terminal and logs
        @Override
        public String toString() {
            return String.format("%s('%s') at %d:%d", type, value.replace("\n", "\\n").replace("\r", "\\r"), line, col);
        }   
    }

    // Token Patterns 

    // Adding the space - OPTION A
    private static final String TRAILING_SPACE = "[ \\r\\n]";

    /* 
       Using Pattern: compiles the regular expression string into a Pattern 
       object to allow Java to analyze and optimize the regular expression once 
       when the program starts. 

       Instead of re-parsing the regex text every time the Lexer reads a token, 
       the pre-compiled Pattern object matches text against the input buffer 
       using a Matcher.
    */
    
    // User defined names: starts with '#'
    private static final Pattern USER_NAME_PATTERN = Pattern.compile("^#[0-9a-z]*" + TRAILING_SPACE);

    // Strings: escaped the hyphen (\\-) so it does not create an invalid regex range
    private static final Pattern STRING_PATTERN = Pattern.compile("^\"[,.:\\-?!0-9a-z]*\"" + TRAILING_SPACE);

    // Numbers: Fixed string concatenation and decimal regex
    private static final Pattern NUM_PATTERN = Pattern.compile("^(0" + TRAILING_SPACE + "|(-|)(0|[1-9][0-9]*)\\.[0-9]*[1-9]" + TRAILING_SPACE + "|(-|)[1-9][0-9]*" + TRAILING_SPACE + ")");

    private static final Set<String> KEYWORDS = Set.of( "void", "num", "return", "print", "nop", "comment", "if", "then", "else", "while", "until", "do", "not", "and", "or", "eq", "larger", "lesser", "add", "sub", "mul", "div", "mod", "neg");

    
}