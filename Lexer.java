import java.util.*;
import java.util.regex.*;

public class Lexer {

    public enum TokenType {
        KEYWORD, USER_DEFINED_NAME, NUM, STRING, 
        LPARENTHESIS, RPARENTHESIS, L_BRACE, R_BRACE, 
        SEMICOLON, COLON, ASSIGN, EOF
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

        @Override
        public String toString() {
            return String.format("%s(%s) [%d:%d]", 
                type, 
                value.replace("\r", "\\r").replace("\n", "\\n"), 
                line, 
                col
            );
        }
    }

    public static class TokenResult {
        public final Token token;
        public final int newPos;
        public final int newLine;
        public final int newCol;

        public TokenResult(Token token, int newPos, int newLine, int newCol) {
            this.token = token;
            this.newPos = newPos;
            this.newLine = newLine;
            this.newCol = newCol;
        }
    }

    private static final String TRAILING_SPACE = "(?=[ \\t\\r\\n\\u00A0]|$)";

    // Changed * to + so at least one alphanumeric character after '#' is required
    private static final Pattern USER_NAME_PATTERN = 
        Pattern.compile("^#[0-9a-z]+" + TRAILING_SPACE);

    private static final Pattern STRING_PATTERN = 
        Pattern.compile("^\"[,.:\\-?!0-9a-z]*\"" + TRAILING_SPACE);

    private static final Pattern NUM_PATTERN = 
        Pattern.compile("^(0|-?(0|[1-9][0-9]*)\\.[0-9]*[1-9]|-?[1-9][0-9]*)" + TRAILING_SPACE);

    private static final Set<String> KEYWORDS = Set.of(
        "void", "num", "return", "print", "nop", "comment", 
        "if", "then", "else", "while", "until", "do", "input",
        "not", "and", "or", "eq", "larger", "lesser", 
        "add", "sub", "mul", "div", "mod", "neg"
    );

    public static TokenResult nextToken(String input, int pos, int line, int col) {
        int currentPos = pos;
        int currentLine = line;
        int currentCol = col;

        // 1. Skip inter-token whitespace
        while (currentPos < input.length()) {
            char c = input.charAt(currentPos);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\u00A0') {
                if (c == '\n') {
                    currentLine++;
                    currentCol = 1;
                } else {
                    currentCol++;
                }
                currentPos++;
            } else {
                break;
            }
        }

        // Handle End-Of-File 
        if (currentPos >= input.length()) {
            Token eofToken = new Token(TokenType.EOF, "$", currentLine, currentCol);
            return new TokenResult(eofToken, currentPos, currentLine, currentCol);
        }

        String currentBuffer = input.substring(currentPos);
        Token matchedToken = null;

=        Matcher definedNames = USER_NAME_PATTERN.matcher(currentBuffer);
        if (definedNames.find()) {
            matchedToken = new Token(TokenType.USER_DEFINED_NAME, definedNames.group(), currentLine, currentCol);
        }

        if (matchedToken == null) {
            Matcher stringMatcher = STRING_PATTERN.matcher(currentBuffer);
            if (stringMatcher.find()) {
                matchedToken = new Token(TokenType.STRING, stringMatcher.group(), currentLine, currentCol);
            }
        }

        if (matchedToken == null) {
            Matcher numMatchers = NUM_PATTERN.matcher(currentBuffer);
            if (numMatchers.find()) {
                matchedToken = new Token(TokenType.NUM, numMatchers.group(), currentLine, currentCol);
            }
        }

        if (matchedToken == null && currentBuffer.length() >= 1) {
            char symbol = currentBuffer.charAt(0);
            boolean hasTrailingSpace = false;

            if (currentBuffer.length() == 1) {
                hasTrailingSpace = true; 
            } else {
                char trailing = currentBuffer.charAt(1);
                if (trailing == ' ' || trailing == '\t' || trailing == '\r' || trailing == '\n' || trailing == '\u00A0') {
                    hasTrailingSpace = true;
                }
            }

            if (hasTrailingSpace) {
                String lexeme = String.valueOf(symbol);
                switch (symbol) {
                    case '(': matchedToken = new Token(TokenType.LPARENTHESIS, lexeme, currentLine, currentCol); break;
                    case ')': matchedToken = new Token(TokenType.RPARENTHESIS, lexeme, currentLine, currentCol); break;
                    case '{': matchedToken = new Token(TokenType.L_BRACE, lexeme, currentLine, currentCol); break;
                    case '}': matchedToken = new Token(TokenType.R_BRACE, lexeme, currentLine, currentCol); break;
                    case ';': matchedToken = new Token(TokenType.SEMICOLON, lexeme, currentLine, currentCol); break;
                    case ':': matchedToken = new Token(TokenType.COLON, lexeme, currentLine, currentCol); break;
                    case '=': matchedToken = new Token(TokenType.ASSIGN, lexeme, currentLine, currentCol); break;
                }
            }
        }

        if (matchedToken == null) {
            int spaceIndex = -1;
            for (int i = 0; i < currentBuffer.length(); i++) {
                char c = currentBuffer.charAt(i);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\u00A0') {
                    spaceIndex = i;
                    break;
                }
            }

            if (spaceIndex != -1) {
                String rawWord = currentBuffer.substring(0, spaceIndex);
                if (KEYWORDS.contains(rawWord)) {
                    String fullToken = currentBuffer.substring(0, spaceIndex + 1);
                    matchedToken = new Token(TokenType.KEYWORD, fullToken, currentLine, currentCol);
                }
            }
        }

        if (matchedToken == null) {
            return null;
        }

        int nextPos = currentPos + matchedToken.value.length();
        int nextLine = currentLine;
        int nextCol = currentCol;

        for (int i = 0; i < matchedToken.value.length(); i++) {
            if (matchedToken.value.charAt(i) == '\n') {
                nextLine++;
                nextCol = 1;
            } else {
                nextCol++;
            }
        }

        return new TokenResult(matchedToken, nextPos, nextLine, nextCol);
    }
}