package net.shattergui.tools;

import com.sun.tools.javac.parser.Scanner;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.util.Context;
import groovyjarjarantlr4.v4.runtime.CharStreams;
import groovyjarjarantlr4.v4.runtime.Token;
import org.apache.groovy.parser.antlr4.GroovyLangLexer;
import org.apache.groovy.parser.antlr4.GroovyLexer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CommentCheck {
    private CommentCheck() {
    }

    public static void main(String[] args) throws IOException {
        List<String> found = new ArrayList<>();
        for (String name : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            if (name.isBlank()) {
                continue;
            }
            Path file = Path.of(name);
            String text = Files.readString(file, StandardCharsets.UTF_8);
            for (int offset : comments(file.getFileName().toString(), text)) {
                found.add(file + ":" + line(text, offset));
            }
        }
        if (!found.isEmpty()) {
            System.err.println("Comments in code (" + found.size() + "), the project writes none:");
            found.forEach(f -> System.err.println("  " + f));
            System.exit(1);
        }
    }

    static List<Integer> comments(String name, String text) {
        if (name.endsWith(".java")) {
            return java(text);
        }
        if (name.endsWith(".gradle")) {
            return groovy(text);
        }
        if (name.endsWith(".properties")) {
            return properties(text);
        }
        throw new IllegalArgumentException(name);
    }

    static List<Integer> java(String text) {
        List<Integer> out = new ArrayList<>();
        Scanner scanner = ScannerFactory.instance(new Context()).newScanner(text, false);
        int gap = 0;
        while (true) {
            Tokens.Token token = scanner.token();
            int end = token.kind == Tokens.TokenKind.EOF ? text.length() : token.pos;
            for (int i = gap; i < end; i++) {
                if (text.startsWith("//", i) || text.startsWith("/*", i)) {
                    out.add(i);
                    i = text.startsWith("//", i) ? lineEnd(text, i) : text.indexOf("*/", i + 2) + 1;
                }
            }
            if (token.kind == Tokens.TokenKind.EOF) {
                return out;
            }
            gap = token.endPos;
            scanner.nextToken();
        }
    }

    static List<Integer> groovy(String text) {
        List<Integer> out = new ArrayList<>();
        try {
            for (Token token : new GroovyLangLexer(CharStreams.fromString(text)).getAllTokens()) {
                String t = token.getText();
                if (token.getType() == GroovyLexer.NL && (t.startsWith("//") || t.startsWith("/*"))) {
                    out.add(token.getStartIndex());
                }
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("cannot lex: " + e.getMessage(), e);
        }
        return out;
    }

    static List<Integer> properties(String text) {
        List<Integer> out = new ArrayList<>();
        boolean continues = false;
        int start = 0;
        while (start < text.length()) {
            int end = lineEnd(text, start);
            int first = start;
            while (first < end && " \t\f".indexOf(text.charAt(first)) >= 0) {
                first++;
            }
            if (!continues && first < end && (text.charAt(first) == '#' || text.charAt(first) == '!')) {
                out.add(first);
                continues = false;
            } else {
                int slashes = 0;
                for (int k = end - 1; k >= first && text.charAt(k) == '\\'; k--) {
                    slashes++;
                }
                continues = first < end && slashes % 2 == 1;
            }
            start = end + 1;
            if (start < text.length() && text.charAt(start - 1) == '\r' && text.charAt(start) == '\n') {
                start++;
            }
        }
        return out;
    }

    static int lineEnd(String text, int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
            i++;
        }
        return i;
    }

    static int line(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
