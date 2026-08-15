package com.example.game3d.terrain.io.publish;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JavaProviderRegistry {
    private final Map<String, JavaLevelProvider> providers;

    public JavaProviderRegistry(Iterable<JavaLevelProvider> values) {
        LinkedHashMap<String, JavaLevelProvider> byId = new LinkedHashMap<>();
        for (JavaLevelProvider value : values) {
            if (value == null || byId.put(value.id(), value) != null)
                throw new IllegalArgumentException("Duplicate or null Java provider");
        }
        providers = Collections.unmodifiableMap(byId);
    }

    public JavaLevelProvider require(String id) {
        JavaLevelProvider provider = providers.get(id);
        if (provider == null) throw new IllegalArgumentException("Unknown Java terrain provider " + id);
        return provider;
    }
}
