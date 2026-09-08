package com.beeleza.pixflow.domain.model;

import java.util.regex.Pattern;

/**
 * Kinds of Pix key, each with the format its value must follow.
 */
public enum PixKeyType {

    CPF(Pattern.compile("\\d{11}")),
    CNPJ(Pattern.compile("\\d{14}")),
    EMAIL(Pattern.compile("[^@\\s]{1,64}@[^@\\s]{1,255}\\.[^@\\s]{2,}")),
    PHONE(Pattern.compile("\\+55\\d{10,11}")),
    RANDOM(Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));

    private final Pattern format;

    PixKeyType(Pattern format) {
        this.format = format;
    }

    public boolean matches(String value) {
        return value != null && format.matcher(value).matches();
    }
}
