package com.mandi.core;

import java.util.Locale;
import java.util.Map;

/** Groups Agmarknet commodity names into categories for filtering. Unknown names fall back to "Other". */
public final class Categories {

    private static final Map<String, String> KNOWN = Map.ofEntries(
            Map.entry("onion", "Vegetables"), Map.entry("tomato", "Vegetables"), Map.entry("potato", "Vegetables"),
            Map.entry("green chilli", "Vegetables"), Map.entry("cabbage", "Vegetables"),
            Map.entry("cauliflower", "Vegetables"), Map.entry("brinjal", "Vegetables"),
            Map.entry("bhindi(ladies finger)", "Vegetables"), Map.entry("garlic", "Vegetables"),
            Map.entry("ginger(green)", "Vegetables"),
            Map.entry("wheat", "Cereals"), Map.entry("rice", "Cereals"), Map.entry("jowar(sorghum)", "Cereals"),
            Map.entry("bajra(pearl millet/cumbu)", "Cereals"), Map.entry("maize", "Cereals"),
            Map.entry("soyabean", "Oilseeds"), Map.entry("groundnut", "Oilseeds"), Map.entry("mustard", "Oilseeds"),
            Map.entry("arhar (tur/red gram)(whole)", "Pulses"), Map.entry("bengal gram(gram)(whole)", "Pulses"),
            Map.entry("green gram (moong)(whole)", "Pulses"),
            Map.entry("turmeric", "Spices"), Map.entry("dry chillies", "Spices"), Map.entry("coriander(leaves)", "Spices"),
            Map.entry("pomegranate", "Fruits"), Map.entry("banana", "Fruits"), Map.entry("grapes", "Fruits"),
            Map.entry("orange", "Fruits"), Map.entry("mango", "Fruits"),
            Map.entry("cotton", "Fibre"), Map.entry("gur(jaggery)", "Sugar"));

    private Categories() {
    }

    public static String of(String commodity) {
        return KNOWN.getOrDefault(commodity.trim().toLowerCase(Locale.ROOT), "Other");
    }
}
