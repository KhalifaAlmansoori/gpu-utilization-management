package gpu.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Anything that is saved to a data file as one text line.
 * Fields are separated by '|'. Characters that would break a line ('|', '\', new lines)
 * are escaped, so descriptions and reasons can contain anything.
 * <p>
 * Each implementing class also offers a static {@code fromLine(String)} that rebuilds the object
 * (an interface cannot require a static method). The storage code can then save every
 * Storable with one loop (polymorphism).
 */
public interface Storable {

    /** One line of text describing this object; never contains a line break. */
    String toLine();

    /** Joins fields into one escaped line. Null fields are written as empty text. */
    static String join(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(escape(fields[i]));
        }
        return sb.toString();
    }

    /** Splits a line produced by {@link #join} back into its (unescaped) fields. */
    static List<String> split(String line) {
        List<String> fields = new ArrayList<>();
        for (String part : line.split("\\|", -1)) {
            fields.add(unescape(part));
        }
        return fields;
    }

    static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("|", "\\p")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    static String unescape(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                switch (next) {
                    case 'p': sb.append('|'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(c).append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
