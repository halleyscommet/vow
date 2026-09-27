package us.dingl.VowSMPPlugin.Ritual;

import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public class StormBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
                LifecycleEvents.DATAPACK_DISCOVERY, event -> {
                    DatapackRegistrar registrar = event.registrar();
                    try {
                        URI uri = Objects.requireNonNull(
                                StormBootstrap.class.getResource("/pack")
                        ).toURI();
                        registrar.discoverPack(uri, "storm_sky");
                    } catch (URISyntaxException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }
}
