package dev.apexcraft.core;

import java.util.Arrays;
import java.util.Locale;

public record GoalRequest(String item,int amount) {
    public GoalRequest {
        if (amount<1 || amount>4096 || item.isBlank()) throw new IllegalArgumentException("Specify an item and quantity 1..4096");
    }
    public static GoalRequest parse(String text) {
        String normalized=text.toLowerCase(Locale.ROOT).trim().replaceAll("[.!]+$", "")
            .replaceFirst("^(get|obtain|collect|craft)\\s+", "");
        String[] words=normalized.split("\\s+");
        int amount=1,first=0;
        if (words.length>1 && words[0].matches("[0-9]+")) { amount=Integer.parseInt(words[0]);first=1; }
        String item=String.join("_",Arrays.copyOfRange(words,first,words.length));
        if (item.equals("diamonds")) item="diamond";
        return new GoalRequest(ProgressionCatalog.canonical(item),amount);
    }
}
