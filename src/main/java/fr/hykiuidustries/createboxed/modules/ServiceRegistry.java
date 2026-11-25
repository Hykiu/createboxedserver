package fr.hykiuidustries.createboxed.modules;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ServiceRegistry {
    private final Map<Class<?>, Object> services = new HashMap<>();

    public <T> void register(Class<T> type, T impl) {
        if (services.putIfAbsent(type, impl) != null) {
            throw new IllegalStateException("Already register service : " + type.getName());
        }
    }

    public <T> T require(Class<T> type) {
        T s = type.cast(services.get(type));
        if (s == null) throw new IllegalStateException("Missing service : " + type.getName());
        return s;
    }

    public <T> Optional<T> find(Class<T> type) {
        return Optional.ofNullable(type.cast(services.get(type)));
    }

    public void unregister(Class<?> type) {
        services.remove(type);
    }

    public void clear() {
        services.clear();
    }
}
