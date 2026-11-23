package dev.larpclient.launcher.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the user's "JVM arguments" setting into separate arguments, honouring quotes so values with
 * spaces survive: {@code -Dname="a b"} and {@code '-Dpath=/My Games'} are one argument each.
 * <ul>
 *   <li>Whitespace separates arguments outside quotes.</li>
 *   <li>Single quotes are literal. Inside double quotes, {@code \"} and {@code \\} are escapes.</li>
 *   <li>Backslashes outside quotes are literal, so Windows paths work unquoted.</li>
 * </ul>
 */
final class JvmArgs {
    private JvmArgs() {
    }

    /** @throws IllegalArgumentException on an unclosed quote, with a message fit for the user */
    static List<String> split(String text) {
        List<String> args = new ArrayList<>();
        if (text == null) {
            return args;
        }
        StringBuilder current = new StringBuilder();
        boolean inArg = false;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote == '\'') {
                if (c == '\'') {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (quote == '"') {
                if (c == '"') {
                    quote = 0;
                } else if (c == '\\' && i + 1 < text.length() && (text.charAt(i + 1) == '"' || text.charAt(i + 1) == '\\')) {
                    current.append(text.charAt(++i));
                } else {
                    current.append(c);
                }
            } else if (Character.isWhitespace(c)) {
                if (inArg) {
                    args.add(current.toString());
                    current.setLength(0);
                    inArg = false;
                }
            } else {
                inArg = true;
                if (c == '"' || c == '\'') {
                    quote = c;
                } else {
                    current.append(c);
                }
            }
        }
        if (quote != 0) {
            throw new IllegalArgumentException("JVM arguments: unclosed " + (quote == '"' ? "double" : "single")
                    + " quote. Fix it in Settings → Java.");
        }
        if (inArg) {
            args.add(current.toString());
        }
        return args;
    }
}
