package com.nuclei.userservice.util;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class EmailUtil {

    public String normalize(final String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}