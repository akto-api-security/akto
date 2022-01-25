package com.akto.utils;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;

public class RandomString {
    public String nextString() {
        for (int idx = 0; idx < buf.length; ++idx)
            buf[idx] = symbols[random.nextInt(symbols.length)];
        return new String(buf);
    }

    public static final String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static final String lower = upper.toLowerCase(Locale.ROOT);

    public static final String digits = "0123456789";

    public static final String alphanum = upper + lower + digits;

    private final Random random;

    private final char[] symbols;

    private final char[] buf;

    public RandomString(int length) {
        if (length < 1) throw new IllegalArgumentException();
        this.random = new SecureRandom();
        this.symbols = alphanum.toCharArray();
        this.buf = new char[length];
    }


}
