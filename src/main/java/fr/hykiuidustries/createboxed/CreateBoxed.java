package fr.hykiuidustries.createboxed;

import com.mojang.logging.LogUtils;
import fr.hykiuidustries.createboxed.modules.ModuleLoader;
import fr.hykiuidustries.createboxed.modules.ServiceRegistry;
import fr.hykiuidustries.createboxed.plot.PlotModule;
import fr.hykiuidustries.createboxed.team.TeamsModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.nio.file.Path;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CreateBoxed.MOD_ID)
public class CreateBoxed {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "createboxed";
    // Create a Deferred Register to hold Blocks which will all be registered under the "createboxed" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    // Create a Deferred Register to hold Items which will all be registered under the "createboxed" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "createboxed" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(
        Registries.CREATIVE_MODE_TAB,
        MOD_ID
    );
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ModuleLoader loader;

    public CreateBoxed(IEventBus modEventBus, ModContainer container) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(this::onCommonSetup);

        Path baseFolder = FMLPaths.CONFIGDIR.get().resolve(MOD_ID);

        ServiceRegistry services = new ServiceRegistry();
        this.loader = new ModuleLoader(services, LOGGER, baseFolder);
        loader.register(new PlotModule());
        loader.register(new TeamsModule());

        container.registerConfig(ModConfig.Type.COMMON, loader.buildConfig());
    }

    /**
     * Configs are loaded before common setup, and Commands is built later during
     * WorldStem loading, so this is the earliest safe point to enable modules.
     */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        loader.enableAll();
    }
}
