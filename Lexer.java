import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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

    /* the line and col will be used to provide the meaningful error message to the user 
       Unexpected token 'num' at line 18, column 3. Expected ';'
    */
    public List<Token> tokenize(String input){
        List<Token> tokens = new ArrayList<>();

        int line = 1;
        int col = 1;
        int index = 0;
        int length = input.length();

        while (index < length){

            char currentChar = input.charAt(index);
            // skip the leading whitespaces between token
            // \t to be ignored 
            // where the spaces, newlines (\n) and carriage returns (\r) should be treated as whitespace between tokens
            if (currentChar == ' ' || currentChar == '\t'){
                col++;
                index++;
                continue;
            } else if (currentChar == '\n'){
                line++;
                col = 1;
                index++;
                continue;
            } else if (currentChar == '\r'){
                index++;
                continue;
            }
        
        // takes all rest of the input (unparsed string)
        String currentBuffer = input.substring(index);

        // using Matcher takes the compiled Pattern and executes a search against the string currentBuffer 

        // get user-defined variables from the input file and create a token for it and advance the pointers 
        Matcher definedNames = USER_NAME_PATTERN.matcher(currentBuffer);
        if (definedNames.find()){

            // gotten a string matched the regex 
            String matched = definedNames.group();
            // create a new token object, including the actual string value (matched), its position (line and col - to help error reporting)
            // icrease the column pointer 
            tokens.add(new Token(TokenType.USER_DEFINED_NAME, matched, line, col));
            index += matched.length();
            col += matched.length();
            continue;
        }

        Matcher stringMatchers = STRING_PATTERN.matcher(currentBuffer);
        if (stringMatcher.find()){
            String matched = stringMatcher.group();
            tokens.add(new Token(TokenType.STRING, matched, line, col));
            index += matched.length();
            col += matched.length();
            continue;
        }

        Matcher numMatchers = NUM_PATTERN.matcher(currentBuffer);
        if (numMatchers.find()){
            String matched = numMatchers.group();
            tokens.add(new Token(TokenType.NUM, matched, line, col));
            index += matched.length();
            col += matched.length();
            continue;
        }

        if (currentBuffer.startsWith("(")){
            tokens.add(new Token(TokenType.LPARENTHESIS, "(", line, col));
            index++;
            col++;
            continue;
        } else if (currentBuffer.startsWith(")")){
            tokens.add(new Token(TokenType.RPARENTHESIS, ")", line, col));
            index++;
            col++;
            continue;
        } else if (currentBuffer.startsWith("{")){
            tokens.add(new Token(TokenType.L_BRACE, "{", line, col));
            index++;
            col++;
            continue;
        } else if (currentBuffer.startsWith("}")){
            tokens.add(new Token(TokenType.R_BRACE, "}", line, col));
            index++;
            col++;
            continue;
        } else if (currentBuffer.startsWith(";")){
            tokens.add(new Token(TokenType.R_BRACE, ";", line, col));
            index++;
            col++;
            continue;
        }

        int spaceIndex = -1;
        for (int i = 0; i < currentBuffer.length(); i++){
            char c = currentBuffer.charAt(i);
            if (c == ' ' || c == '\r' || c == '\n'){
                spaceIndex = i;
                break;
            }
        }


        if (spaceIndex != -1){
            String rawWord = currentBuffer.substring(0, spaceIndex);
            if (KEYWORDS.contains(rawWord)){
                String fullToken = currentBuffer.substring(0, spaceIndex + 1);
                tokens.add(new Token(TokenType.KEYWORD, fullToken, line, col));
                index += fullToken.length();
                col += fullToken.length();
                continue;
            }
        }

        throw new RuntimeException("Lexical Error: Unrecognised token or missing trailing space at " + line ":" + col);
    }

        tokens.add(new Token(TokenType.EOF, "$", line, col));
        return tokens;
    }
}