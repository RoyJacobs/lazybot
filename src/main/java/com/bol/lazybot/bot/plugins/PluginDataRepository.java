package com.bol.lazybot.bot.plugins;

import java.util.Optional;

public interface PluginDataRepository {
    <T> Optional<T> get(final String key, final Class<T> clazz);
    <T> void save(final String key, final T data);

    void clearAll();
}