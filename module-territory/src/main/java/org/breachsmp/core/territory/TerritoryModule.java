package org.breachsmp.core.territory;

import org.breachsmp.core.api.audit.AuditService;
import org.breachsmp.core.api.command.CommandRegistry;
import org.breachsmp.core.api.concurrent.CoreExecutors;
import org.breachsmp.core.api.module.ARKModule;
import org.breachsmp.core.api.module.HealthStatus;
import org.breachsmp.core.api.module.ModuleContext;
import org.breachsmp.core.api.season.SeasonService;
import org.breachsmp.core.api.service.ServiceRegistry;
import org.breachsmp.core.api.territory.TerritoryService;
import org.breachsmp.core.api.tunable.TunableService;
import org.breachsmp.core.domain.territory.RecipeSpec;
import org.breachsmp.core.domain.territory.TerritoryConfig;
import org.breachsmp.core.paper.PaperBridge;
import org.breachsmp.core.territory.block.PlacedBlockTracker;
import org.breachsmp.core.territory.claim.ClaimService;
import org.breachsmp.core.territory.command.ClaimSubcommand;
import org.breachsmp.core.territory.cupboard.ClaimVisualizer;
import org.breachsmp.core.territory.cupboard.CupboardListener;
import org.breachsmp.core.territory.cupboard.CupboardMenu;
import org.breachsmp.core.territory.cupboard.PinDialogs;
import org.breachsmp.core.territory.cupboard.TerritoryItems;
import org.breachsmp.core.territory.hammer.HammerListener;
import org.breachsmp.core.territory.protect.ProtectionListener;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Modulo territorio (M2a): claim dei Tool Cupboard con PIN, protezione della struttura, tier dei blocchi e martello. In
 * {@code onLoad} config, servizio e comando; in {@code onEnable} caricamento dei claim, listener e ricette.
 */
public final class TerritoryModule implements ARKModule {

    public static final String ID = "territory";
    private static final String CONFIG_FILE = "territory.yml";

    private volatile PaperBridge bridge;
    private volatile CoreExecutors executors;
    private volatile Logger logger;
    private volatile TerritoryConfig config;
    private volatile TerritoryTunables tunables;
    private volatile ClaimService claims;
    private volatile TerritoryItems items;
    private volatile PlacedBlockTracker placed;
    private volatile List<NamespacedKey> recipes = List.of();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onLoad(ModuleContext context) {
        ServiceRegistry services = context.services();
        bridge = services.require(PaperBridge.class);
        SeasonService seasons = services.require(SeasonService.class);
        AuditService audit = services.require(AuditService.class);
        CommandRegistry commands = services.require(CommandRegistry.class);
        tunables = TerritoryTunables.register(services.require(TunableService.class));
        executors = context.executors();
        logger = context.logger();
        config = TerritoryConfigLoader.load(defaultConfigIfMissing(bridge.dataFolder().resolve(CONFIG_FILE)));
        claims = new ClaimService(context.dataSource(), executors, seasons, audit, config.tiers(),
                tunables.pinAttemptsPer10Min()::get, Clock.systemDefaultZone(), logger);
        services.register(TerritoryService.class, claims);
        commands.registerAdmin(new ClaimSubcommand(claims, () -> items, () -> placed, executors.main()));
    }

    @Override
    public void onEnable() {
        TerritoryConfigLoader.checkBlocks(config);
        claims.loadNow();
        items = new TerritoryItems(bridge, config);
        placed = new PlacedBlockTracker(bridge);
        PinDialogs pins = new PinDialogs(claims, executors, logger);
        CupboardMenu menu = new CupboardMenu(claims, pins, new ClaimVisualizer(bridge), executors, logger);
        bridge.registerListener(placed);
        bridge.registerListener(new ProtectionListener(claims, placed));
        bridge.registerListener(new CupboardListener(claims, items, placed, pins, menu, tunables, config, executors, logger));
        bridge.registerListener(menu);
        bridge.registerListener(new HammerListener(claims, placed, items, config.tiers()));
        Server server = bridge.plugin().getServer();
        NamespacedKey cupboardKey = new NamespacedKey(bridge.plugin(), "tool_cupboard");
        NamespacedKey hammerKey = new NamespacedKey(bridge.plugin(), "hammer");
        server.addRecipe(recipe(cupboardKey, items.cupboard(1), config.cupboardRecipe()));
        server.addRecipe(recipe(hammerKey, items.hammer(), config.hammerRecipe()));
        recipes = List.of(cupboardKey, hammerKey);
    }

    @Override
    public void onDisable() {
        PaperBridge current = bridge;
        if (current != null) {
            recipes.forEach(key -> current.plugin().getServer().removeRecipe(key));
        }
        recipes = List.of();
        // Claim e autorizzati sono già nel DB (I1): niente da svuotare
    }

    @Override
    public HealthStatus health() {
        return HealthStatus.HEALTHY;
    }

    @Override
    public List<String> healthDetails() {
        ClaimService current = claims;
        return List.of("Claim attivi: " + (current == null ? 0 : current.activeCount()));
    }

    /** Copia il {@code territory.yml} di default dal jar (funziona anche nei test, niente {@code saveResource}). */
    private static Path defaultConfigIfMissing(Path file) {
        if (Files.exists(file)) {
            return file;
        }
        try (InputStream in = Objects.requireNonNull(TerritoryModule.class.getResourceAsStream("/" + CONFIG_FILE),
                "territory.yml di default mancante nel jar")) {
            Files.createDirectories(file.getParent());
            Files.copy(in, file);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossibile creare " + file + ": " + e.getMessage(), e);
        }
        return file;
    }

    private static ShapedRecipe recipe(NamespacedKey key, ItemStack result, RecipeSpec spec) {
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(spec.pattern().toArray(String[]::new));
        spec.ingredients().forEach((slot, material) ->
                recipe.setIngredient(slot, Objects.requireNonNull(Material.matchMaterial(material), material)));
        return recipe;
    }
}
