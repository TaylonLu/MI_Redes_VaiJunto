package org.UEFS.vaijunto.Server.Interfaces;

import org.UEFS.vaijunto.Server.Request;
import org.UEFS.vaijunto.Server.Response;

@FunctionalInterface
public interface RequestHandler {
    public Response handle(Request R);
}
