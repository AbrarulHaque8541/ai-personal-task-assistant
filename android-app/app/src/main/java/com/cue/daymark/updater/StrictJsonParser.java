package com.cue.daymark.updater;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON reader for the bounded GitHub release response; it has no runtime dependencies. */
final class StrictJsonParser {
    private static final int MAX_DEPTH = 64;

    private StrictJsonParser() { }

    static Object parse(String json) {
        if (json == null) throw new IllegalArgumentException("JSON input is missing.");
        Parser parser = new Parser(json);
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) throw parser.invalid("Unexpected trailing JSON data.");
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value, String description) {
        if (!(value instanceof Map)) throw new IllegalArgumentException(description + " must be an object.");
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object value, String description) {
        if (!(value instanceof List)) throw new IllegalArgumentException(description + " must be an array.");
        return (List<Object>) value;
    }

    static String requiredString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String)) throw new IllegalArgumentException(key + " must be a string.");
        return (String) value;
    }

    static String optionalString(Map<String, Object> values, String key, String fallback) {
        if (!values.containsKey(key) || values.get(key) == null) return fallback;
        Object value = values.get(key);
        if (!(value instanceof String)) throw new IllegalArgumentException(key + " must be a string or null.");
        return (String) value;
    }

    static boolean requiredBoolean(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Boolean)) throw new IllegalArgumentException(key + " must be a boolean.");
        return (Boolean) value;
    }

    static long requiredLong(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof BigInteger)) throw new IllegalArgumentException(key + " must be an integer.");
        BigInteger integer = (BigInteger) value;
        // BigInteger.longValueExact() requires API level 31, but the app supports API 26;
        // on older devices it would throw NoSuchMethodError. bitLength() <= 63 holds exactly
        // when the value fits in a signed long, so this conversion is exact on all APIs.
        if (integer.bitLength() > 63) {
            throw new IllegalArgumentException(key + " is outside the supported integer range.");
        }
        return integer.longValue();
    }

    static int requiredInt(Map<String, Object> values, String key) {
        long value = requiredLong(values, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " is outside the supported integer range.");
        }
        return (int) value;
    }

    private static final class Parser {
        private final String input;
        private int position;

        Parser(String input) {
            this.input = input;
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) throw invalid("JSON nesting is too deep.");
            skipWhitespace();
            if (atEnd()) throw invalid("JSON value is missing.");
            char next = input.charAt(position);
            switch (next) {
                case '{': return readObject(depth + 1);
                case '[': return readArray(depth + 1);
                case '"': return readString();
                case 't': readLiteral("true"); return Boolean.TRUE;
                case 'f': readLiteral("false"); return Boolean.FALSE;
                case 'n': readLiteral("null"); return null;
                default:
                    if (next == '-' || (next >= '0' && next <= '9')) return readNumber();
                    throw invalid("Unexpected character in JSON value.");
            }
        }

        private Map<String, Object> readObject(int depth) {
            position++;
            skipWhitespace();
            Map<String, Object> result = new LinkedHashMap<>();
            if (consume('}')) return result;
            while (true) {
                skipWhitespace();
                if (atEnd() || input.charAt(position) != '"') throw invalid("JSON object key must be a string.");
                String key = readString();
                if (result.containsKey(key)) throw invalid("JSON object contains a duplicate key.");
                skipWhitespace();
                require(':');
                Object value = readValue(depth);
                result.put(key, value);
                skipWhitespace();
                if (consume('}')) return result;
                require(',');
            }
        }

        private List<Object> readArray(int depth) {
            position++;
            skipWhitespace();
            List<Object> result = new ArrayList<>();
            if (consume(']')) return result;
            while (true) {
                result.add(readValue(depth));
                skipWhitespace();
                if (consume(']')) return result;
                require(',');
            }
        }

        private String readString() {
            require('"');
            StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                char value = input.charAt(position++);
                if (value == '"') {
                    validateSurrogates(result);
                    return result.toString();
                }
                if (value < 0x20) throw invalid("JSON string contains a control character.");
                if (value != '\\') {
                    result.append(value);
                    continue;
                }
                if (atEnd()) throw invalid("JSON string ends in an incomplete escape.");
                char escape = input.charAt(position++);
                switch (escape) {
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    case '/': result.append('/'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'u': result.append(readUnicodeEscape()); break;
                    default: throw invalid("JSON string contains an invalid escape.");
                }
            }
            throw invalid("JSON string is not terminated.");
        }

        private char readUnicodeEscape() {
            if (input.length() - position < 4) throw invalid("JSON Unicode escape is incomplete.");
            int value = 0;
            for (int index = 0; index < 4; index++) {
                char digit = input.charAt(position++);
                int hex = digit >= '0' && digit <= '9' ? digit - '0'
                        : digit >= 'a' && digit <= 'f' ? digit - 'a' + 10
                        : digit >= 'A' && digit <= 'F' ? digit - 'A' + 10 : -1;
                if (hex < 0) throw invalid("JSON Unicode escape is invalid.");
                value = (value << 4) | hex;
            }
            return (char) value;
        }

        private Object readNumber() {
            int start = position;
            if (consume('-') && atEnd()) throw invalid("JSON number is incomplete.");
            if (consume('0')) {
                if (!atEnd() && isDigit(input.charAt(position))) throw invalid("JSON number has a leading zero.");
            } else {
                if (atEnd() || input.charAt(position) < '1' || input.charAt(position) > '9') {
                    throw invalid("JSON number is invalid.");
                }
                while (!atEnd() && isDigit(input.charAt(position))) position++;
            }
            if (consume('.')) {
                if (atEnd() || !isDigit(input.charAt(position))) throw invalid("JSON fraction is invalid.");
                while (!atEnd() && isDigit(input.charAt(position))) position++;
            }
            if (consume('e') || consume('E')) {
                if (!consume('+')) consume('-');
                if (atEnd() || !isDigit(input.charAt(position))) throw invalid("JSON exponent is invalid.");
                while (!atEnd() && isDigit(input.charAt(position))) position++;
            }
            try {
                String token = input.substring(start, position);
                return token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0
                        ? new BigDecimal(token) : new BigInteger(token);
            } catch (NumberFormatException exception) {
                throw invalid("JSON number is invalid.");
            }
        }

        private void readLiteral(String literal) {
            if (!input.startsWith(literal, position)) throw invalid("JSON literal is invalid.");
            position += literal.length();
        }

        private void validateSurrogates(StringBuilder value) {
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                if (Character.isHighSurrogate(current)) {
                    if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                        throw invalid("JSON string contains an unpaired Unicode surrogate.");
                    }
                    index++;
                } else if (Character.isLowSurrogate(current)) {
                    throw invalid("JSON string contains an unpaired Unicode surrogate.");
                }
            }
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char value = input.charAt(position);
                if (value == ' ' || value == '\t' || value == '\r' || value == '\n') position++;
                else return;
            }
        }

        private boolean consume(char expected) {
            if (!atEnd() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void require(char expected) {
            if (!consume(expected)) throw invalid("JSON syntax is invalid.");
        }

        private boolean atEnd() {
            return position >= input.length();
        }

        private IllegalArgumentException invalid(String message) {
            return new IllegalArgumentException(message + " (offset " + position + ")");
        }

        private static boolean isDigit(char value) {
            return value >= '0' && value <= '9';
        }
    }
}
