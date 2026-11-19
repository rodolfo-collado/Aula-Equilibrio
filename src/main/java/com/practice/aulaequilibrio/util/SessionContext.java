package com.practice.aulaequilibrio.util;

import com.practice.aulaequilibrio.model.AccountData.Identity;

public final class SessionContext {
    private static volatile Identity current;
    private SessionContext() {}
    public static Identity current() { return current; }
    public static void install(Identity identity) { current = identity; }
    public static void clear() { current = null; }
}
