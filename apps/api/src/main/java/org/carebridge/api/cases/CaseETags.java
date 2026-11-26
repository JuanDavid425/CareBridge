package org.carebridge.api.cases;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Case versions on the wire: the ETag of version 3 is {@code "v3"}. Writes must send it back in
 * {@code If-Match}; a weak form ({@code W/"v3"}) is accepted as well.
 */
public final class CaseETags {

    private static final Pattern TAG = Pattern.compile("^(?:W/)?\"v([1-9][0-9]{0,8})\"$");

    private CaseETags() {
    }

    public static String of(int version) {
        return "\"v" + version + "\"";
    }

    /** The version in an If-Match / If-None-Match value, if it is a single well-formed case tag. */
    public static OptionalInt parse(String header) {
        if (header == null) {
            return OptionalInt.empty();
        }
        Matcher m = TAG.matcher(header.strip());
        return m.matches() ? OptionalInt.of(Integer.parseInt(m.group(1))) : OptionalInt.empty();
    }

    /** The If-Match version, or {@link PreconditionRequiredException} when missing or unusable. */
    public static int require(String ifMatch) {
        return parse(ifMatch).orElseThrow(PreconditionRequiredException::new);
    }
}
