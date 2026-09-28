import java.util.*;
import java.util.regex.*;

public class Lexer {

    /** Lexical category of a token. */
    public enum TokenType { KEYWORD, NAME, NUM, STRING, SYMBOL, EOF }

    public static class Token {
        public final TokenType type;
        public final String lexeme;   // exact text, WITHOUT the trailing blank
        public final int line, col;   // 1-based position of the first character

        public Token(TokenType type, String lexeme, int line, int col) {
            this.type = type; this.lexeme = lexeme; this.line = line; this.col = col;
        }

        /** The terminal symbol this token represents in the grammar. */
        public String terminal() {
            switch (type) {
                case NAME:   return Grammar.NAME;
                case NUM:    return Grammar.NUMBER;
                case STRING: return Grammar.STRING;
                case EOF:    return Grammar.EOF;
                default:     return lexeme;          // keywords and ( ) { } ; : =
            }
        }

        @Override public String toString() {
            return type == TokenType.EOF ? "end of file" : "'" + lexeme + "'";
        }
    }

    /** Thrown for input that does not form a valid SPL token. */
    public static class LexicalError extends Exception {
        public LexicalError(String msg) { super(msg); }
    }

    // ---- Regular expressions, taken directly from the specification ----------

    // #(0|..|9|a|..|z)*   -- note: '*' means a lone "#" is a legal name
    private static final Pattern NAME = Pattern.compile("#[0-9a-z]*");

    // "(,|.|:|-|?|!|0..9|a..z)*"
    private static final Pattern STRING = Pattern.compile("\"[,.:\\-?!0-9a-z]*\"");

    // 0  |  -?0.[0-9]*[1-9]  |  -?[1-9][0-9]*.[0-9]*[1-9]  |  -?[1-9][0-9]*
    private static final Pattern NUM = Pattern.compile(
        "0|-?0\\.[0-9]*[1-9]|-?[1-9][0-9]*\\.[0-9]*[1-9]|-?[1-9][0-9]*");

    public static final Set<String> KEYWORDS = Set.of(
        "void", "num", "return", "print", "nop", "comment",
        "if", "then", "else", "while", "until", "do",
        "not", "and", "or", "eq", "larger", "lesser",
        "add", "sub", "mul", "div", "mod", "neg");

    private static final Set<String> SYMBOLS = Set.of("(", ")", "{", "}", ";", ":", "=");

    public static boolean isBlank(char c) {
        return c == ' ' || c == '\r' || c == '\n' || c == '\t';
    }

    // ---------------------------------------------------------------------------

    private final String input;
    private int pos = 0, line = 1, col = 1;

    public Lexer(String input) { this.input = input; }

    /** Tokenises the whole input; the list always ends with an EOF token. */
    public List<Token> tokenize() throws LexicalError {
        List<Token> tokens = new ArrayList<>();
        Token t;
        do { t = next(); tokens.add(t); } while (t.type != TokenType.EOF);
        return tokens;
    }

    private void advance() {
        if (input.charAt(pos) == '\n') { line++; col = 1; } else { col++; }
        pos++;
    }

    private Token next() throws LexicalError {
        while (pos < input.length() && isBlank(input.charAt(pos))) advance();
        if (pos >= input.length()) return new Token(TokenType.EOF, "$", line, col);

        // Because every token must end in a blank, a token is exactly the
        // maximal run of non-blank characters. Classify that run as a whole.
        int end = pos;
        while (end < input.length() && !isBlank(input.charAt(end))) end++;
        String word = input.substring(pos, end);

        TokenType type = classify(word);
        if (type == null) {
            throw new LexicalError(String.format(
                "Lexical error at line %d, column %d: '%s' is not a valid SPL token.%n  Hint: %s",
                line, col, word, hintFor(word)));
        }
        Token tok = new Token(type, word, line, col);
        while (pos < end) advance();
        return tok;
    }

    private static TokenType classify(String w) {
        if (SYMBOLS.contains(w))             return TokenType.SYMBOL;
        if (KEYWORDS.contains(w))            return TokenType.KEYWORD;
        if (NAME.matcher(w).matches())       return TokenType.NAME;
        if (STRING.matcher(w).matches())     return TokenType.STRING;
        if (NUM.matcher(w).matches())        return TokenType.NUM;
        return null;
    }

    /** Best-effort explanation of why a word is not a token. */
    private static String hintFor(String w) {
        // Two or more tokens glued together, e.g. "print(" or "#x;" or "(#a"
        String split = w.startsWith("\"") && w.lastIndexOf('"') > 0
            ? w.substring(0, w.lastIndexOf('"') + 1) + " " + w.substring(w.lastIndexOf('"') + 1).replaceAll("([(){};:=])", " $1 ")
            : w.replaceAll("([(){};:=\"])", " $1 ").replaceAll(" \" ([^\"]*) \" ", " \"$1\" ");
        String[] parts = split.trim().split("\\s+");
        if (parts.length > 1 && Arrays.stream(parts).allMatch(x -> classify(x) != null))
            return "every SPL token must be followed by a blank space. Write '" + String.join(" ", parts) + "'.";
        if (!w.equals(w.toLowerCase()))
            return "SPL is lower-case only: keywords, names and strings may not contain capital letters.";
        char c = w.charAt(0);
        if (c == '"') {
            if (w.length() == 1 || w.charAt(w.length() - 1) != '"')
                return "unterminated string, or a string containing a blank. Strings may not contain spaces; "
                     + "only lower-case letters, digits and , . : - ? ! are allowed between the quotes.";
            return "strings may only contain lower-case letters, digits and the characters , . : - ? !";
        }
        if (c == '#')
            return "user-defined names are '#' followed only by lower-case letters and digits (e.g. #x1).";
        if (Character.isLetter(c))
            return "'" + w + "' is not a keyword. User-defined names must start with '#', e.g. #" + w.toLowerCase() + ".";
        if (Character.isDigit(c) || c == '-' || c == '.') {
            if (w.matches("-0+(\\.0*)?"))        return "negative zero is not allowed; write 0.";
            if (w.matches("-?0[0-9]+.*"))        return "numbers may not have leading zeros (write 7, not 07).";
            if (w.matches("-?[0-9]+\\.[0-9]*0")) return "decimal numbers may not end in 0 (write 1.5, not 1.50; write 2, not 2.0).";
            if (w.matches("-?[0-9]+\\."))        return "a decimal point must be followed by digits ending in a non-zero digit.";
            if (w.startsWith("."))               return "decimals need a leading digit (write 0.5, not .5).";
            return "numbers look like 0, 42, -7, 3.14 or -0.25.";
        }
        return "the character '" + c + "' (ASCII " + (int) c + ") cannot start any SPL token.";
    }
}