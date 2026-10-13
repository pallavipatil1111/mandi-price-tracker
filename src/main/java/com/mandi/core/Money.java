package com.mandi.core;

/** Indian-style rupee formatting for messages: 125000 becomes "₹1,25,000" (lakh grouping). */
public final class Money {

    private Money() {
    }

    public static String rupees(long amount) {
        String sign = amount < 0 ? "-" : "";
        String digits = Long.toString(Math.abs(amount));
        if (digits.length() <= 3) {
            return sign + "₹" + digits;
        }
        String last3 = digits.substring(digits.length() - 3);
        String rest = digits.substring(0, digits.length() - 3);
        StringBuilder out = new StringBuilder();
        // Java's DecimalFormat cannot do the 2-2-3 grouping, so build it by hand.
        int first = rest.length() % 2 == 0 ? 2 : 1;
        out.append(rest, 0, first);
        for (int i = first; i < rest.length(); i += 2) {
            out.append(',').append(rest, i, i + 2);
        }
        return sign + "₹" + out + "," + last3;
    }
}
