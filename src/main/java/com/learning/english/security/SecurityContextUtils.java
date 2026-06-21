package com.learning.english.security;

public class SecurityContextUtils {
    // ScopedValue represents a lightweight, modern Java 25 replacement for ThreadLocal
    public static final ScopedValue<UserPrincipal> CURRENT_USER = ScopedValue.newInstance();
}
