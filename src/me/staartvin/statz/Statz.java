package me.staartvin.statz;

import me.staartvin.statz.api.API;
import me.staartvin.statz.cache.CachingManager;
import me.staartvin.statz.commands.manager.CommandsManager;
import me.staartvin.statz.config.ConfigHandler;
import me.staartvin.statz.database.DatabaseConnector;
import me.staartvin.statz.database.datatype.RowRequirement;
import me.staartvin.statz.datamanager.DataManager;
import me.staartvin.statz.datamanager.player.PlayerInfo;
import me.staartvin.statz.datamanager.player.PlayerStat;
import me.staartvin.statz.gui.GUIManager;
import me.staartvin.statz.hooks.DependencyManager;
import me.staartvin.statz.importer.ImportManager;
import me.staartvin.statz.language.LanguageHandler;
import me.staartvin.statz.language.StatisticDescriptionConfig;
import me.staartvin.statz.listeners.*;
import me.staartvin.statz.logger.LogManager;
import me.staartvin.statz.patches.PatchManager;
import me.staartvin.statz.placeholders.StatzPlaceholders;
import me.staartvin.statz.statsdisabler.DisableManager;
import me.staartvin.statz.tasks.TaskManager;
import me.staartvin.statz.tasks.UpdateDatabaseTask;
import me.staartvin.statz.update.UpdatePoolManager;
import me.staartvin.statz.util.StatzUtil;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Main class of Statz Spigot/Bukkit plugin.
 *
 * @author Staartvin
 */
public class Statz extends JavaPlugin {

    private DatabaseConnector connector;
    private DataManager dataManager;
    private API statzAPI;
    private DependencyManager depManager;
    private ConfigHandler configHandler;
    private CommandsManager commandsManager;
    private LogManager logsManager;
    private LanguageHandler langHandler;
    private ImportManager importManager;
    private DisableManager disableManager;
    private PatchManager patchManager;
    private GUIManager guiManager;

    private CachingManager cachingManager;
    private TaskManager taskManager;
    private UpdatePoolManager updatePoolManager;

    private StatisticDescriptionConfig statisticDescriptionConfig;

    @Override
    public void onEnable() {
        // Load confighandler
        this.setConfigHandler(new ConfigHandler(this));

        // Load config with default values
        this.getConfigHandler().loadConfig();

        // Load hooks
        this.setDependencyManager(new DependencyManager(this));

        // Create patch manager and send patches
        this.setPatchManager(new PatchManager(this));

        // Load data manager as database is loaded!
        this.setDataManager(new DataManager(this));

        // Load API
        this.setStatzAPI(new API(this));

        // Do performance test
        //this.doPerformanceTest();

        // Do a check on all present hooks
        this.getDependencyManager().loadDependencies();

        this.setCommandsManager(new CommandsManager(this));

        // Register command
        getCommand("statz").setExecutor(getCommandsManager());

        // Load GUI manager
        this.setGUIManager(new GUIManager(this));

        this.setLogsManager(new LogManager(this));

        // Register listeners
        this.registerListeners();

        // Create log file
        this.getLogsManager().createLogFile();

        // Show admin what the status of the logging system is.
        if (!this.getConfigHandler().isLoggingEnabled()) {
            this.getServer().getConsoleSender().sendMessage("[Statz] " + ChatColor.RED + "Logging is disabled!");
        } else {
            this.getServer().getConsoleSender().sendMessage("[Statz] " + ChatColor.GREEN + "Logging is enabled!");
        }

        this.setLangHandler(new LanguageHandler(this));

        // Load lang.yml
        this.getLangHandler().createNewFile();

        this.setDisableManager(new DisableManager(this));

        // Load disabled-stats.yml
        this.getDisableManager().createNewFile();

        this.setImportManager(new ImportManager(this));

        this.getLogger().info(this.getDescription().getFullName() + " has been enabled!");

        this.getLogsManager().writeToLogFile("Enabled Statz!");

        // Check whether you are running at high enough version.
        String minimumVersion = "1.10";

        if (!StatzUtil.isHigherVersion(minimumVersion)) {
            this.getServer().getConsoleSender().sendMessage("[Statz] " + ChatColor.RED + "You are running a version " +
                    "of Minecraft below " + minimumVersion + "! This version of Statz needs at least " +
                    minimumVersion);
            this.getServer().getConsoleSender().sendMessage("[Statz] " + ChatColor.RED + "Statz will now disable " +
                    "itself.");

            // Disable Statz.
            this.getServer().getPluginManager().disablePlugin(this);
        }

        // Set up caching manager
        this.setCachingManager(new CachingManager());

        // Start update pool manager.
        this.setUpdatePoolManager(new UpdatePoolManager(this));

        // Create task manager for starting and stopping tasks.
        this.setTaskManager(new TaskManager(this));

        // Run task to sync database with update list.
        this.getTaskManager().startUpdateDatabaseTask();

        // Create Statistic Description handler
        this.setStatisticDescriptionConfig(new StatisticDescriptionConfig(this));

        // Load statistics.yml
        this.getStatisticDescriptionConfig().createNewFile();

        // Only register placeholders if we have placeholder API available!
        if (this.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            // Register Statz Placeholder
            new StatzPlaceholders(this).register();
        }
    }

    @Override
    public void onDisable() {
        debugMessage(ChatColor.RED + "Saving updates to database!");

        // Schedule task to update database for the last time.
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.execute(new UpdateDatabaseTask(this));
        scheduler.shutdown();

        this.getLogger().info(this.getDescription().getFullName() + " has been disabled!");

        this.getLogsManager().writeToLogFile("Disabled Statz!");
    }

    private void registerListeners() {
        if (!this.getConfigHandler().getStatsTracking()) {
            this.debugMessage(ChatColor.GOLD + "Statz won't track stats of any player!");
            return; // We don't track stats, so we don't register listeners
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.DEATHS)) {
            this.getServer().getPluginManager().registerEvents(new DeathsListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.JOINS)) {
            this.getServer().getPluginManager().registerEvents(new JoinsListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.ITEMS_CAUGHT)) {
            this.getServer().getPluginManager().registerEvents(new ItemsCaughtListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.BLOCKS_PLACED)) {
            this.getServer().getPluginManager().registerEvents(new BlocksPlacedListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.BLOCKS_BROKEN)) {
            this.getServer().getPluginManager().registerEvents(new BlocksBrokenListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.KILLS_MOBS)) {
            this.getServer().getPluginManager().registerEvents(new KillsMobsListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.KILLS_PLAYERS)) {
            this.getServer().getPluginManager().registerEvents(new KillsPlayersListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.FOOD_EATEN)) {
            this.getServer().getPluginManager().registerEvents(new FoodEatenListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.DAMAGE_TAKEN)) {
            this.getServer().getPluginManager().registerEvents(new DamageTakenListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.TIMES_SHORN)) {
            this.getServer().getPluginManager().registerEvents(new TimesShornListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.DISTANCE_TRAVELLED)) {
            this.getServer().getPluginManager().registerEvents(new DistanceTravelledListener(this), this);
            this.getServer().getPluginManager().registerEvents(new DistanceTravelledVehicleListener(this), this);
            this.getServer().getPluginManager().registerEvents(new DistanceTravelledToggleGlideListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.ITEMS_CRAFTED)) {
            this.getServer().getPluginManager().registerEvents(new ItemsCraftedListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.XP_GAINED)) {
            this.getServer().getPluginManager().registerEvents(new XPGainedListener(this), this);
        }

        // Important listeners that should always be on.
        this.getServer().getPluginManager().registerEvents(new JoinPlayerListener(this), this);
        this.getServer().getPluginManager().registerEvents(new QuitListener(this), this);

        // Check for votes if the stat is not disabled and PluginLibrary is active.
        if (!this.getConfigHandler().isStatDisabled(PlayerStat.VOTES) && this.getDependencyManager().isPluginLibraryLoaded()) {
            this.getServer().getPluginManager().registerEvents(new VotesListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.ARROWS_SHOT)) {
            this.getServer().getPluginManager().registerEvents(new ArrowsShotListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.ENTERED_BEDS)) {
            this.getServer().getPluginManager().registerEvents(new EnteredBedsListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.COMMANDS_PERFORMED)) {
            this.getServer().getPluginManager().registerEvents(new CommandsPerformedListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.TIMES_KICKED)) {
            this.getServer().getPluginManager().registerEvents(new TimesKickedListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.TOOLS_BROKEN)) {
            this.getServer().getPluginManager().registerEvents(new ToolsBrokenListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.EGGS_THROWN)) {
            this.getServer().getPluginManager().registerEvents(new EggsThrownListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.WORLDS_CHANGED)) {
            this.getServer().getPluginManager().registerEvents(new WorldsChangedListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.BUCKETS_FILLED)) {
            this.getServer().getPluginManager().registerEvents(new BucketsFilledListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.BUCKETS_EMPTIED)) {
            this.getServer().getPluginManager().registerEvents(new BucketsEmptiedListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.ITEMS_PICKED_UP)) {
            this.getServer().getPluginManager().registerEvents(new ItemsPickedUpListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.ITEMS_DROPPED)) {
            this.getServer().getPluginManager().registerEvents(new ItemsDroppedListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.TELEPORTS)) {
            this.getServer().getPluginManager().registerEvents(new TeleportsListener(this), this);
        }

        if (!this.getConfigHandler().isStatDisabled(PlayerStat.VILLAGER_TRADES)) {
            this.getServer().getPluginManager().registerEvents(new VillagerTradesListener(this), this);
        }

        for (PlayerStat stat : this.getConfigHandler().getDisabledStats()) {
            this.debugMessage(ChatColor.DARK_AQUA + "Statz won't track " + stat.toString() + "!");
        }

        // Register confirm command
        this.getServer().getPluginManager().registerEvents(new ConfirmTransferCommandListener(this), this);
    }

    public void debugMessage(String message) {
        // Check if debug is enabled
        if (!this.getConfigHandler().isDebugEnabled())
            return;

        this.getServer().getConsoleSender()
                .sendMessage(ChatColor.translateAlternateColorCodes('&', "[Statz debug] " + message));
    }

    /**
     * This method does a general check for all events.
     * <br>
     * Currently, it checks if a player is in creative mode and if we should
     * ignore creative mode
     *
     * @param player Player to check
     * @param stat   Stat to check
     * @return true if we should track the stat, false otherwise.
     */
    public boolean doGeneralCheck(Player player, PlayerStat stat) {
        // Check if player is NPC.
        if (player.hasMetadata("NPC")) {
            return false;
        }

        // Check if we should register players in CREATIVE
        if (this.getConfigHandler().shouldIgnoreCreative() && player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }

        // Check if we should track in the player's current position.
        return !this.getDisableManager().isStatDisabledLocation(player.getLocation(), stat);
    }

    public void doPerformanceTest() {
        this.getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            public void run() {

                debugMessage("Start stresstest");

                // 38 random players
                ArrayList<UUID> uuids = new ArrayList<UUID>();
                uuids.add(UUID.fromString("cb58fd93-567f-45f0-8a60-171f15b66e5f"));
                uuids.add(UUID.fromString("d44fe94f-6c26-408c-b05e-6746b3b79b7e"));
                uuids.add(UUID.fromString("48e11f72-2310-406f-ae47-1117e8d08547"));
                uuids.add(UUID.fromString("9f6ddfae-7398-4973-82a0-d1fb67635956"));
                uuids.add(UUID.fromString("0bec5711-4316-4a8f-b61b-2a119e652082"));
                uuids.add(UUID.fromString("5169e667-c43c-41cb-80b3-9b127ad1d64a"));
                uuids.add(UUID.fromString("54db3bd8-b206-411d-900f-ba56698a049e"));
                uuids.add(UUID.fromString("6b831421-b03b-45d6-bbee-c660c1ef3abf"));
                uuids.add(UUID.fromString("dcdaa593-6b01-43ea-9792-372f6ca4a646"));
                uuids.add(UUID.fromString("7747121e-1d51-44e3-8c7c-98deaf2d0f41"));
                uuids.add(UUID.fromString("311959d1-038f-4e82-9f28-d7fdc06b917e"));
                uuids.add(UUID.fromString("dee81da3-5d11-4630-964b-66cde2c3a50c"));
                uuids.add(UUID.fromString("ec9e8bd0-cdad-46b1-9da7-0d187a187d4a"));
                uuids.add(UUID.fromString("cc41ffa3-2f70-4b18-9ca8-c1e9af9f5041"));
                uuids.add(UUID.fromString("0f42c47b-5c9e-4b8c-a21d-9fa83239dad0"));
                uuids.add(UUID.fromString("d0853e6e-30d8-421b-b86b-bd5e635f01e1"));
                uuids.add(UUID.fromString("3c7db14d-ac4b-4e35-b2c6-3b2237f382be"));
                uuids.add(UUID.fromString("86464dd9-9a02-4cd0-895e-4c60b5766108"));
                uuids.add(UUID.fromString("2361ca08-44dc-4541-8fb1-4f02f3f9f222"));
                uuids.add(UUID.fromString("e8441bb2-2fa8-498c-919d-8aaba24bc414"));
                uuids.add(UUID.fromString("3c166720-24ea-4f0a-8630-04c7ee222c86"));
                uuids.add(UUID.fromString("6128dc3e-e826-4fe7-94df-6d6a2b3b4d38"));
                uuids.add(UUID.fromString("b1adf2ec-eed6-46d6-a770-40f409651913"));
                uuids.add(UUID.fromString("4de3afa2-b921-472a-93d5-a077ffdc40a9"));
                uuids.add(UUID.fromString("4c607d4b-3816-40de-b384-389f3b6ea8c4"));
                uuids.add(UUID.fromString("8112554c-ef4a-46bd-bb21-61ac3a7b30a2"));
                uuids.add(UUID.fromString("2c757641-1213-4a23-8285-5d3e578a525c"));
                uuids.add(UUID.fromString("d2feb792-e293-456e-8f65-d2c6557a3475"));
                uuids.add(UUID.fromString("67ac9634-44f4-481e-b6c9-7a6a9d03d041"));
                uuids.add(UUID.fromString("3e14a919-2e96-447e-a0c1-095123073c3e"));
                uuids.add(UUID.fromString("66869715-7435-4e87-b8a2-3bfafb166428"));
                uuids.add(UUID.fromString("9c68ddca-a37b-4873-936d-dc4160d4a155"));
                uuids.add(UUID.fromString("9935c756-b677-4769-a54c-78406c9954f1"));
                uuids.add(UUID.fromString("44359973-2977-4a96-bdd3-3ceaea6eb301"));
                uuids.add(UUID.fromString("eb37de95-404b-43ef-81cc-84f22b9d5d7f"));
                uuids.add(UUID.fromString("e373fd60-a1fb-41a1-95d0-df9c0ffb77c9"));
                uuids.add(UUID.fromString("8e1edca1-cc02-4d19-8226-366530ac649b"));
                uuids.add(UUID.fromString("0d0523ca-89c7-4ac8-94c5-da279091a6a2"));

                ArrayList<String> move = new ArrayList<>();
                move.add("WALK");
                move.add("FLY");
                move.add("MINECART");
                move.add("PIG IN MINECART");
                move.add("HORSE IN MINECART");
                move.add("PIG");
                move.add("BOAT");

                long startTime = System.currentTimeMillis();

                List<Long> times = new ArrayList<>();

                // 100 million interations
                for (int i = 0; i < 10000; i++) {

                    long start = System.currentTimeMillis();
                    // Send 100 million updates

                    //debugMessage("--------------------------");
                    // Get a random UUID
                    Random randomizer = new Random();
                    UUID random = uuids.get(randomizer.nextInt(uuids.size()));

                    //debugMessage("i: " + i + ", UUID: " + random);

                    final PlayerStat stat = PlayerStat.DISTANCE_TRAVELLED;

                    String movementType = move.get(new Random().nextInt(move.size()));

                    int distTravelled = new Random().nextInt(5);

                    //debugMessage("Dist value: " + distTravelled);

                    if (distTravelled == 0) {
                        continue;
                    }

                    // Get player info.
                    final PlayerInfo info = getDataManager().getPlayerInfo(random, stat, new RowRequirement("world",
                            "world"), new RowRequirement("moveType", movementType));

                    // Get current value of stat.
                    int currentValue = 0;

                    // Add to total value
                    currentValue += info.getTotalValue(stat);

                    // Update value to new stat.
                    getDataManager().setPlayerInfo(random, stat, StatzUtil.makeQuery("uuid", random, "value",
                            (currentValue + distTravelled), "moveType", movementType, "world", "world"));

                    long total = System.currentTimeMillis() - start;

                    times.add(total);
                }

                long totalTime = System.currentTimeMillis() - startTime;

                debugMessage("End of stresstest");
                debugMessage("Took " + totalTime + " ms");

                long totalSum = 0;

                for (Long time : times) {
                    totalSum += time;
                }

                double per = (totalSum * 1.0 / times.size() * 1.0);
                double perSec = 1000.0 / per;

                debugMessage("Average input took " + per + " ms");
                debugMessage("Hence, I can perform (on average) " + perSec + " operations per second");
                //getDataPoolManager().printPool();

            }
        }, 20 * 10, 20 * 30);
    }

    public DatabaseConnector getDatabaseConnector() {
        return connector;
    }

    public void setDatabaseConnector(final DatabaseConnector sqlConnector) {
        this.connector = sqlConnector;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public void setDataManager(final DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public API getStatzAPI() {
        return statzAPI;
    }

    public void setStatzAPI(API statzAPI) {
        this.statzAPI = statzAPI;
    }

    public DependencyManager getDependencyManager() {
        return depManager;
    }

    public void setDependencyManager(DependencyManager depManager) {
        this.depManager = depManager;
    }

    public ConfigHandler getConfigHandler() {
        return configHandler;
    }

    public void setConfigHandler(ConfigHandler configHandler) {
        this.configHandler = configHandler;
    }

    public CommandsManager getCommandsManager() {
        return commandsManager;
    }

    public void setCommandsManager(CommandsManager commandsManager) {
        this.commandsManager = commandsManager;
    }

    public LogManager getLogsManager() {
        return logsManager;
    }

    public void setLogsManager(LogManager logsManager) {
        this.logsManager = logsManager;
    }

    public LanguageHandler getLangHandler() {
        return langHandler;
    }

    public void setLangHandler(LanguageHandler langHandler) {
        this.langHandler = langHandler;
    }

    public ImportManager getImportManager() {
        return importManager;
    }

    public void setImportManager(ImportManager importManager) {
        this.importManager = importManager;
    }

    public DisableManager getDisableManager() {
        return disableManager;
    }

    public void setDisableManager(DisableManager disableManager) {
        this.disableManager = disableManager;
    }

    public PatchManager getPatchManager() {
        return patchManager;
    }

    public void setPatchManager(PatchManager patchManager) {
        this.patchManager = patchManager;
    }

    public GUIManager getGUIManager() {
        return guiManager;
    }

    public void setGUIManager(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    public CachingManager getCachingManager() {
        return cachingManager;
    }

    public void setCachingManager(CachingManager cachingManager) {
        this.cachingManager = cachingManager;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public void setTaskManager(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public UpdatePoolManager getUpdatePoolManager() {
        return updatePoolManager;
    }

    public void setUpdatePoolManager(UpdatePoolManager updatePoolManager) {
        this.updatePoolManager = updatePoolManager;
    }

    public StatisticDescriptionConfig getStatisticDescriptionConfig() {
        return statisticDescriptionConfig;
    }

    public void setStatisticDescriptionConfig(StatisticDescriptionConfig statisticDescriptionConfig) {
        this.statisticDescriptionConfig = statisticDescriptionConfig;
    }
}
