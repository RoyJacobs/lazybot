package org.royjacobs.lazybot.bot;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import lombok.extern.slf4j.Slf4j;
import org.royjacobs.lazybot.bot.plugins.*;
import org.royjacobs.lazybot.hipchat.client.RoomApi;
import org.royjacobs.lazybot.hipchat.client.RoomApiFactory;
import org.royjacobs.lazybot.hipchat.client.dto.RoomId;
import org.royjacobs.lazybot.hipchat.installations.Installation;
import org.royjacobs.lazybot.hipchat.installations.InstallationContext;
import org.royjacobs.lazybot.hipchat.installations.InstalledPlugin;
import org.royjacobs.lazybot.hipchat.server.webhooks.dto.RoomMessage;
import org.royjacobs.lazybot.store.Store;
import org.royjacobs.lazybot.store.StoreFactory;
import ratpack.service.Service;
import ratpack.service.StartEvent;
import ratpack.service.StopEvent;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;

@Slf4j
@Singleton
public class BotOrchestrationService implements Service {
    private final StoreFactory storeFactory;
    private final Provider<Set<Plugin>> pluginProvider;
    private final RoomApiFactory roomApiFactory;

    private final Map<Installation, InstallationContext> activeInstallations;
    private final Store<Installation> storedInstallations;

    @Inject
    public BotOrchestrationService(
            final StoreFactory storeFactory,
            final Provider<Set<Plugin>> pluginProvider,
            final RoomApiFactory roomApiFactory
    ) {
        this.storeFactory = storeFactory;
        this.pluginProvider = pluginProvider;
        this.roomApiFactory = roomApiFactory;
        this.activeInstallations = new HashMap<>();

        storedInstallations = storeFactory.get("installations", Installation.class);
    }

    public void onStart(final StartEvent event) throws IOException {
        for (Installation installation : storedInstallations.findAll()) {
            try {
                startInstallation(installation);
            } catch (Exception e) {
                log.error("Could not start installation for room: " + installation.getRoomId(), e);
            }
        }
    }

    public void startInstallation(final Installation installation) throws IOException {
        log.info("Starting installation: " + installation);

        final InstallationContext.InstallationContextBuilder builder = InstallationContext
                .builder()
                .installation(installation);

        log.info("Creating roomApi");
        final RoomApi roomApi = roomApiFactory.create(installation);

        log.info("Creating plugins");
        Set<PluginDescriptor> allDescriptors = new HashSet<>();

        pluginProvider.get().forEach(plugin -> {
            allDescriptors.add(plugin.getDescriptor());

            final Store<? extends PluginGlobalData> globalStore = storeFactory.get("plugindata-" + plugin.getDescriptor().getKey(), plugin.getDescriptor().getGlobalDataClass());

            final PluginContext.PluginContextBuilder pluginContextBuilder = PluginContext.builder()
                    .roomApi(roomApi)
                    .roomId(installation.getRoomId())
                    .allDescriptors(allDescriptors); // because this list is mutable, it will eventually contain descriptors for all the plugins

            if (plugin.getDescriptor().getRoomDataClass() != null) {
                pluginContextBuilder.roomStore(storeFactory.get("plugindata-" + plugin.getDescriptor().getKey() + "-room-" + installation.getRoomId(), plugin.getDescriptor().getRoomDataClass()));
            }

            if (plugin.getDescriptor().getGlobalDataClass() != null) {
                pluginContextBuilder.globalStore(storeFactory.get("plugindata-" + plugin.getDescriptor().getKey() + "-global", plugin.getDescriptor().getGlobalDataClass()));
            }

            final PluginContext pluginContext = pluginContextBuilder.build();
            final InstalledPlugin installedPlugin = InstalledPlugin.builder()
                    .plugin(plugin)
                    .context(pluginContext)
                    .build();
            builder.plugin(installedPlugin);

            plugin.onStart(pluginContext);
        });

        log.info("Installation complete");
        activeInstallations.put(installation, builder.build());
    }

    public void onStop(final StopEvent event) {
        for (InstallationContext context : activeInstallations.values()) {
            for (InstalledPlugin plugin: context.getPlugins()) {
                // Notify plugin
                plugin.getPlugin().onStop(false);
            }
        }
    }

    public void removeInstallation(final String oauthId) {
        getInstallationByOauthId(oauthId).ifPresent(context -> {
            for (InstalledPlugin plugin : context.getPlugins()) {
                // Remove context and clear any data
                plugin.getContext().getRoomStore().clearAll();

                // Notify plugin
                plugin.getPlugin().onStop(true);
            }

            removeInstallationByOauthId(oauthId);
        });
    }

    public void onRoomMessage(final RoomMessage message) {
        getInstallationByOauthId(message.getOauthId()).ifPresent(context -> {
            final List<String> cmdLine = Splitter.on(CharMatcher.WHITESPACE)
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToList(message.getItem().getMessage().getMessage());
            if (cmdLine.isEmpty()) return;
            if (!cmdLine.get(0).equalsIgnoreCase("/lazybot")) return;

            final Command command = Command.builder()
                    .originalMessage(message)
                    .command(cmdLine.size() > 1 ? cmdLine.get(1) : "help")
                    .args(cmdLine.size() > 2 ? cmdLine.subList(2, cmdLine.size()) : Collections.emptyList())
                    .build();

            for (InstalledPlugin plugin : context.getPlugins()) {
                final PluginMessageHandlingResult result = plugin.getPlugin().onCommand(command);
                switch (result) {
                    case NOT_INTENDED_FOR_THIS_PLUGIN:
                        continue;
                    case SUCCESS:
                    case BAD_REQUEST:
                        return;
                    case FAILURE:
                        return;
                }
            }

            // No plugin found that can handle this message, so let's give some help
            for (InstalledPlugin plugin : context.getPlugins()) {
                final PluginMessageHandlingResult result = plugin.getPlugin().onUnhandledCommand(command);
                if (result == PluginMessageHandlingResult.SUCCESS) return;
            }

            log.error("Message not handled", message);
        });
    }

    public Optional<InstallationContext> getInstallationByOauthId(String oauthId) {
        return activeInstallations.entrySet().stream().filter(kv -> Objects.equals(kv.getKey().getOauthId(), oauthId)).map(Map.Entry::getValue).findFirst();
    }

    private void removeInstallationByOauthId(String oauthId) {
        activeInstallations.entrySet().stream().filter(kv -> Objects.equals(kv.getKey().getOauthId(), oauthId)).map(Map.Entry::getKey).findFirst().ifPresent(activeInstallations::remove);
    }
}