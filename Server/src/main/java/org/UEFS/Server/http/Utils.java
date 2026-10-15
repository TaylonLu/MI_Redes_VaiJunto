package org.UEFS.Server.http;

import io.javalin.http.Context;

public class Utils {
    public static String tokenDe(Context ctx) {
        String auth = ctx.header("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }
}
