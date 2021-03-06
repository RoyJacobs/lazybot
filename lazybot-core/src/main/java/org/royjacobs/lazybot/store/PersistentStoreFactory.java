package org.royjacobs.lazybot.store;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import org.royjacobs.lazybot.api.store.Store;
import org.royjacobs.lazybot.api.store.StoreFactory;
import org.royjacobs.lazybot.config.DatabaseConfig;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
public class PersistentStoreFactory implements StoreFactory {
    private final DatabaseConfig databaseConfig;
    private final Cache<String, Object> storeCache;

    @Inject
    public PersistentStoreFactory(final DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
        storeCache = CacheBuilder.newBuilder().maximumSize(10000).build();
    }

    public <T> Store<T> get(final String key, final Class<T> clazz) {
        final Object existingStore = storeCache.getIfPresent(key);
        if (existingStore != null) return (JacksonStore<T>)existingStore;

        try {
            Files.createDirectory(Paths.get(databaseConfig.getFolder()));
        } catch (FileAlreadyExistsException e){
            // fine!
        } catch (IOException e) {
            log.error("Could not create storage folder for database");
            throw Throwables.propagate(e);
        }

        final File file = new File(databaseConfig.getFolder(), key + ".db");
        final ChronicleMap<String, String> map;
        try {
            map = ChronicleMap.of(String.class, String.class).averageKeySize(32).averageValueSize(100).entries(100).createOrRecoverPersistedTo(file);
        } catch (IOException e) {
            log.error("Could not create or open persistent map: " + file.toString(), e);
            throw Throwables.propagate(e);
        }

        final Store<T> store = new JacksonStore<>(map, clazz);
        storeCache.put(key, store);
        return store;
    }
}
