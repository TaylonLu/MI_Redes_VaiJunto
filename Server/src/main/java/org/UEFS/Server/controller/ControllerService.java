package org.UEFS.Server.controller;

import java.util.HashMap;
import java.util.Map;

public final class ControllerService {
    private static final ControllerService instance = new ControllerService();
    private final Map<Class<?>, Object> controllers = new HashMap<>();

    private ControllerService() {}

    public static ControllerService getInstance() {
        return instance;
    }

    public <T> void register(Class<?> controllerClass, T controller) {
        controllers.put(controllerClass, controller);
    }

    public <T> T get(Class<T> controllerClass) {
        Object controller = controllers.get(controllerClass);
        if (controller == null) {
            throw new IllegalArgumentException("ERRO: Serviço \""+controllerClass.getSimpleName() + "\" não registrado.");
        }

        return controllerClass.cast(controller);
    }
}
