package t00thpick1;

import info.somethingodd.OddItem.OddItem;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.bekvon.bukkit.residence.Residence;
import com.griefcraft.lwc.LWC;

public class TShops extends JavaPlugin implements Listener {
    //TODO Implement limit permissions
    //TODO Implement Give/Take money signs
    //TODO Split protection checks into own classes
    private String user;
    private String pass;
    private String url;
    private Connection conn;
    private Logger log;
    private Configuration config;
    private static Economy econ = null;
    private static Permission perms = null;
    private Map<Location, StoredSign> Signs = new HashMap<Location, StoredSign>();
    private Map<String, Sign> tLink = new HashMap<String,Sign>();
    private Set<String> tUnlink = new HashSet<String>();
    private boolean pricecheck;
    @Override
    public void onEnable() {
        log = getLogger();
        File file = this.getDataFolder();
        if (!file.isDirectory()) {
            if (!file.mkdirs()) {
                this.log.severe("Failed to create TShop's directory folder!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
        if(loadSettings()) {
            getServer().getPluginManager().registerEvents(this, this);
            try {
                createTables();
            } catch (SQLException e) {
                System.out.println("Error with TShop's SQL Connection");
                e.printStackTrace();
            }
            Signs = loadSigns();
        } else {
            getServer().getPluginManager().disablePlugin(this);
        }
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this,
                new Runnable() {
            @Override
            public void run() {
                checkSigns();
            }
        }, 60, 6000);
    }
    public boolean loadSettings() {
        File configFile = new File(this.getDataFolder()+"/config.yml");
        config = YamlConfiguration.loadConfiguration(configFile);
        if(!config.contains("Config.MySQL.URL.IP")) {
            getConfig().addDefault("Config.MySQL.URL.IP", "LOCALHOST");
        }
        if(!config.contains("Config.MySQL.URL.PORT")) {
            getConfig().addDefault("Config.MySQL.URL.PORT", "3306");
        }
        if(!config.contains("Config.MySQL.URL.DATABASE")) {
            getConfig().addDefault("Config.MySQL.URL.DATABASE", "DATABASE");
        }
        if(!config.contains("Config.MySQL.Password")) {
            getConfig().addDefault("Config.MySQL.Password", "Password");
        }
        if(!config.contains("Config.MySQL.Username")) {
            getConfig().addDefault("Config.MySQL.Username", "username");
        }
        if(!config.contains("Config.PriceInSearch")) {
            getConfig().addDefault("Config.PriceInSearch", false);
        }
        config.options().copyDefaults(true);
        this.saveConfig();
        url = "jdbc:mysql://"+config.getString("Config.MySQL.URL.IP")+":"+config.getString("Config.MySQL.URL.PORT")+"/"+config.getString("Config.MySQL.URL.DATABASE");
        pass = config.getString("Config.MySQL.Password");
        user = config.getString("Config.MySQL.Username");
        pricecheck = config.getBoolean("Config.PriceInSearch");
        setupPermissions();
        setupEconomy();
        try {
            connect();
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
            return false;
        }
        return true;
    }
    public Map<Location, StoredSign> loadSigns() {
        Map<Integer, Location> loading = new HashMap<Integer, Location>();
        Map<Location, String> owners = new HashMap<Location, String>();
        Map<Location, StoredSign> result = new HashMap<Location, StoredSign>();
        List<Location> remove = new ArrayList<Location>();
        try {
            String query = "SELECT * FROM tshopsigns";
            ResultSet rs = resultSet(query);
            while(rs.next()) {
                if(rs.getObject("id") != null) {
                    Location loc = new Location(getServer().getWorld(rs.getString("world")), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    if(loc.getBlock().getState() instanceof Sign) {
                        loading.put(rs.getInt("id"), loc);
                        owners.put(loc, rs.getString("owner"));
                    } else {
                        remove.add(loc);
                    }
                }
            }
            for(Location loc: remove) {
                removeSign(loc);
            }
            remove.clear();
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
        try {
            for(int id: loading.keySet()) {
                String query = "SELECT * FROM tshopchests WHERE sign = '"+id+"'";
                ResultSet rs = resultSet(query);
                List<Location> list = new ArrayList<Location>();
                while(rs.next()) {
                    if(rs.getObject("id") != null) {
                        Location loc = new Location(getServer().getWorld(rs.getString("world")), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                        list.add(loc);
                    }
                }
                StoredSign sign = new StoredSign(owners.get(loading.get(id)), list);
                result.put(loading.get(id), sign);
            }
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
        return result;
    }
    public void checkSigns() {
        Set<Location> locs = Signs.keySet();
        for(Location loc: locs) {
            if(!(loc.getBlock().getState() instanceof Sign)) {
                removeSign(loc);
            }
        }
    }
    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = rsp.getProvider();
        return perms != null;
    }
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }
    public void createTables() throws SQLException {
        if(!conn.isValid(1)) {
            connect();
        }
        DatabaseMetaData dbm = conn.getMetaData();
        ResultSet tables = dbm.getTables(null, null, "tshopsigns", null);
        this.log.info("Checking for tshopsigns database table....");
        if (!tables.next()) {
            this.log.info("Table not found, creating table");
            Statement stmt = conn.createStatement();
            String sql = "CREATE TABLE tshopsigns(id INT AUTO_INCREMENT KEY, x INT, y INT, z INT, world VARCHAR(50), firstItem VARCHAR(50), firstQuantity INT, cashFlow DOUBLE, type VARCHAR(20), secondItem VARCHAR(50), secondQuantity INT, owner VARCHAR(50))";
            stmt.executeUpdate(sql);
        }
        tables = dbm.getTables(null, null, "tshopchests", null);
        this.log.info("Checking for tshopchests database table....");
        if (!tables.next()) {
            this.log.info("Table not found, creating table");
            Statement stmt = conn.createStatement();
            String sql = "CREATE TABLE tshopchests(id INT AUTO_INCREMENT KEY, sign INT, owner VARCHAR(50), x INT, y INT, z INT, world VARCHAR(50))";
            stmt.executeUpdate(sql);
        }
        tables = dbm.getTables(null, null, "tshophistory", null);
        this.log.info("Checking for tshophistory database table....");
        if (!tables.next()) {
            this.log.info("Table not found, creating table");
            Statement stmt = conn.createStatement();
            String sql = "CREATE TABLE tshophistory(id INT AUTO_INCREMENT KEY, sign INT, customer VARCHAR(50),  firstItem VARCHAR(50), firstQuantity INT, cashFlow DOUBLE, type VARCHAR(20), secondItem VARCHAR(50), secondQuantity INT, time TIMESTAMP)";
            stmt.executeUpdate(sql);
        }
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        Sign sign = (Sign)event.getBlock().getState();
        int i = 0;
        for(String line: event.getLines()) {
            sign.setLine(i, line);
            i++;
        }
        if(!isGlobalSign(sign)) {
            TSign sig = new TSign(sign, player.getName());
            if(sig.isValid()) {
                if(player.hasPermission("tshop.create")) {
                    createShopSign(sign, player.getName());
                    player.sendMessage(ChatColor.GOLD + "You have succesfully created a TShop Sign Store");
                    player.sendMessage(ChatColor.GOLD + "Now you need to link it to your chest(s)");
                    sign.setLine(3, "<unlinked>");
                } else {
                    player.sendMessage(ChatColor.GOLD + "Insufficient permissions");
                    for(int c = 0; c < 4; c++) {
                        sign.setLine(c, "");
                    }
                }
            }
        }
        if(isGlobalSign(sign)) {
            TSign sig = new TSign(sign, (String)null);
            if(sig.isValid()) {
                if(player.hasPermission("tshop.admin")){
                    createShopSign(sign, "Server Sign");
                    player.sendMessage(ChatColor.GOLD + "You have succesfully created a TShop Global Sign Store");
                } else {
                    player.sendMessage(ChatColor.GOLD + "Insufficient permissions");
                    for(int c = 0; c < 4; c++) {
                        sign.setLine(c, "");
                    }
                }
            }
        }
    }
    public void createShopSign(Sign sign, String owner) {
        Location loc = sign.getLocation();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        String world = loc.getWorld().getName();
        TSign sig = new TSign(sign, owner);
        String item1 = getItemStorageName(sig.getItemStackOne());
        int quantity1 = sig.getItemStackOne().getAmount();
        String item2 = getItemStorageName(sig.getItemStackTwo());
        int quantity2 = sig.getItemStackTwo().getAmount();
        double cashflow = sig.getCashFlow();
        String type = sig.getType();
        String query = null;
        if(type.equals("buy")) {
            query = "INSERT INTO tshopsigns (x ,y ,z , world, cashflow, firstItem, type, firstQuantity, owner) VALUES ('" + x + "', '" + y + "', '" + z + "', '" + world + "', '" + cashflow + "', '" + item1 + "', " + type + ", '" + quantity1 + "', '" + owner + "')";
        } else if (type.equals("sell")) {
            query = "INSERT INTO tshopsigns (x ,y ,z , world, cashflow, secondItem, type, secondQuantity, owner) VALUES ('" + x + "', '" + y + "', '" + z + "', '" + world + "', '" + cashflow + "', '" + item2 + "', " + type + ", '" + quantity2 + "', '" + owner + "')";
        } else if (type.equals("trade")) {
            query = "INSERT INTO tshopsigns (x ,y ,z , world, firstItem, firstQuantity, cashflow, secondItem, type, secondQuantity, owner) VALUES ('" + x + "', '" + y + "', '" + z + "', '" + world + "', '" + item1 + "', '" + quantity1 + "', '" + cashflow + "', '" + item2 + "', " + type + ", '" + quantity2 + "', '" + owner + "')";
        }
        List<Location> list = new ArrayList<Location>();
        Signs.put(loc, new StoredSign(owner, list));
        try {
            SQLupdate(query);
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
    }
    public void SQLupdate(String query) throws SQLException {
        if(!conn.isValid(1)) {
            connect();
        }
        Statement stmt = conn.createStatement();
        stmt.executeUpdate(query);
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSignOrChestClick(PlayerInteractEvent event) {
        if(event.isCancelled() || ((event.getAction() != Action.RIGHT_CLICK_BLOCK) && (event.getAction() != Action.LEFT_CLICK_BLOCK))) {
            return;
        }
        Block block = event.getClickedBlock();
        if(block == null) {
            return;
        }
        Player player = event.getPlayer();
        if(event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if(player.getItemInHand().getType() == Material.BOOK) {
                if((block.getTypeId() == 68) || (block.getTypeId() == 63)) {
                    Sign sign = (Sign)block.getState();
                    if(getShopSign(sign) != null) {
                        if(getShopSign(sign).getPlayer().equalsIgnoreCase(player.getName()) || player.hasPermission("tshop.admin")) {
                            pullHistory(sign, player);
                        } else {
                            player.sendMessage(ChatColor.RED + "You do not have access to that sign's history!");
                        }
                    }
                    return;
                }
            }
            if(tLink.containsKey(player.getName())) {
                if((block.getTypeId() == 68) || (block.getTypeId() == 63)) {
                    if(tLink.get(player.getName()) == null) {
                        Sign sign = (Sign)block.getState();
                        if(getShopSign(sign).getPlayer().equalsIgnoreCase(player.getName()) && !isGlobalSign(sign)) {
                            tLink.put(player.getName(), sign);
                            player.sendMessage(ChatColor.GOLD + "Now punch a chest to link it to that sign!");
                            return;
                        } else {
                            player.sendMessage(ChatColor.RED + "That is not your sign!");
                            return;
                        }
                    }
                    if(tUnlink.contains(player.getName())) {
                        Sign sign = (Sign)block.getState();
                        if(getShopSign(sign).getPlayer().equalsIgnoreCase(player.getName())) {
                            tUnlink.remove(player.getName());
                            unlinkSign(sign);
                            player.sendMessage(ChatColor.GOLD + "Unlinking sign from all chests!");
                            return;
                        } else {
                            player.sendMessage(ChatColor.RED + "That is not your sign!");
                            return;
                        }
                    }
                }
            }
            if((block.getTypeId() == 54) && (tLink.get(player.getName()) != null)) {
                Sign sign = tLink.get(player.getName());
                Chest chest = (Chest)block.getState();
                if(!hasResidencePerms(player, chest) || !hasLWCPerms(player, chest)) {
                    player.sendMessage(ChatColor.RED + "That chest is protected!");
                    return;
                }
                String linker = player.getName();
                if(checkTotal(getShopSign(sign).getChestLocations()) < 10) {
                    if(chest.getBlock().getRelative(BlockFace.EAST).getTypeId() == 54) {
                        linkChest(linker, null, sign, (Chest)chest.getBlock().getRelative(BlockFace.EAST).getState());
                    }
                    if(chest.getBlock().getRelative(BlockFace.NORTH).getTypeId() == 54) {
                        linkChest(linker, null, sign, (Chest)chest.getBlock().getRelative(BlockFace.NORTH).getState());
                    }
                    if(chest.getBlock().getRelative(BlockFace.WEST).getTypeId() == 54) {
                        linkChest(linker, null, sign, (Chest)chest.getBlock().getRelative(BlockFace.WEST).getState());
                    }
                    if(chest.getBlock().getRelative(BlockFace.SOUTH).getTypeId() == 54) {
                        linkChest(linker, null, sign, (Chest)chest.getBlock().getRelative(BlockFace.SOUTH).getState());
                    }
                    linkChest(linker, player, sign, chest);
                } else {
                    player.sendMessage(ChatColor.RED + "That sign has reached its maximum amount of linked chests");
                }
                return;
            }
            if((block.getTypeId() == 54) && tUnlink.contains(player.getName())) {
                Chest chest = (Chest)block.getState();
                String unlinker = player.getName();
                if(chest.getBlock().getRelative(BlockFace.EAST).getTypeId() == 54) {
                    unlinkChest(unlinker, null, (Chest)chest.getBlock().getRelative(BlockFace.EAST).getState());
                }
                if(chest.getBlock().getRelative(BlockFace.NORTH).getTypeId() == 54) {
                    unlinkChest(unlinker, null, (Chest)chest.getBlock().getRelative(BlockFace.NORTH).getState());
                }
                if(chest.getBlock().getRelative(BlockFace.WEST).getTypeId() == 54) {
                    unlinkChest(unlinker, null, (Chest)chest.getBlock().getRelative(BlockFace.WEST).getState());
                }
                if(chest.getBlock().getRelative(BlockFace.SOUTH).getTypeId() == 54) {
                    unlinkChest(unlinker, null, (Chest)chest.getBlock().getRelative(BlockFace.SOUTH).getState());
                }
                unlinkChest(unlinker, player, chest);
                return;
            }
            return;
        }
        if((block.getTypeId() != 68) && (block.getTypeId() != 63)) {
            return;
        }
        Sign sign = (Sign)block.getState();
        processTransaction(player,sign);
    }
    private int checkTotal(List<Location> chestLocations) {
        int doub = 0;
        int sing = 0;
        for(Location loc: chestLocations) {
            boolean doubl = false;
            for(Location lo: chestLocations) {
                if ((loc.getWorld() == lo.getWorld()) && (loc.distance(lo) == 1)) {
                    doub++;
                    doubl = true;
                }
            }
            if(!doubl){
                sing++;
            }
        }
        return (sing + (doub / 2));
    }
    public boolean hasResidencePerms(Player player, Chest chest) {
        return Residence.getPermsByLoc(chest.getLocation()).playerHas(player.getName(), player.getWorld().getName(), "container", true);
    }
    public boolean hasLWCPerms(Player player, Chest chest) {
        if(LWC.getInstance().findProtection(chest.getBlock()) == null) {
            return true;
        }
        return LWC.getInstance().canAccessProtection(player, chest.getBlock());
    }
    @SuppressWarnings("deprecation")
    private void processTransaction(Player player, Sign sign) {
        TSign sig = getShopSign(sign);
        if((sig == null) || !sig.isValid()) {
            return;
        }
        TransactionEvent tSign = new TransactionEvent(sig, player.getName());
        Bukkit.getPluginManager().callEvent(tSign);
        if((player.getInventory().firstEmpty( ) == -1) && (tSign.getItemStackOne() != null)) {
            player.sendMessage(ChatColor.RED + "No room in your inventory!");
            return;
        }
        if((tSign.getItemStackTwo() != null) && !(getStock(player, tSign.getItemStackTwo()) > 0)) {
            player.sendMessage(ChatColor.RED + "You don't have that item!");
            return;
        }
        if(!(getBalance(player) >= tSign.getCashFlow()) && (tSign.getCashFlow() > 0)) {
            player.sendMessage(ChatColor.RED + "Not enough money!");
            return;
        }
        Chest usechest = null;
        boolean notfull = false;
        boolean stocked = false;
        if(!isGlobalSign(sign)) {
            if(!(getBalance(tSign.getPlayer()) >= tSign.getCashFlow()) && (tSign.getCashFlow() < 0)) {
                player.sendMessage(ChatColor.RED + "Owner does not have enough money!");
                return;
            }
            if(tSign.getChestLocations().isEmpty()) {
                player.sendMessage(ChatColor.RED + "Sign is not linked to any chests!");
                return;
            }
            for(Chest chest: tSign.getLinkedChests()) {
                if((tSign.getItemStackOne() != null) && !stocked){
                    if(getStock(chest, tSign.getItemStackOne()) > 0) {
                        usechest = chest;
                        stocked = true;
                    }
                } else {
                    stocked = true;
                }
                if((tSign.getItemStackTwo() != null) && !notfull) {
                    if(chest.getBlockInventory().firstEmpty() != -1){
                        usechest = chest;
                        notfull = true;
                    }
                } else {
                    notfull = true;
                }
            }
            if(!stocked) {
                player.sendMessage(ChatColor.RED + "Sign is not stocked!");
                return;
            }
            if(!notfull) {
                player.sendMessage(ChatColor.RED + "No room in the linked chests!");
                return;
            }
        }
        Inventory inv = player.getInventory();
        Inventory inv2 = null;
        if(!isGlobalSign(sign)) {
            inv2 = usechest.getBlockInventory();
        }
        if(tSign.getItemStackOne() != null) {
            inv.addItem(tSign.getItemStackOne());
            if(!isGlobalSign(sign)) {
                takeItem(inv2, tSign.getItemStackOne());
                updateSigns(usechest);
                if(tSign.getCashFlow() > 0) {
                    creditAccount(tSign.getPlayer(), tSign.getCashFlow());
                }
            }
            if(tSign.getCashFlow() > 0) {
                chargeAccount(player.getName(), tSign.getCashFlow());
            }
        }
        if(tSign.getItemStackTwo() != null) {
            if(!isGlobalSign(sign)) {
                inv2.addItem(tSign.getItemStackTwo());
                if(tSign.getCashFlow() < 0) {
                    chargeAccount(tSign.getPlayer(), Math.abs(tSign.getCashFlow()));
                }
            }
            takeItem(inv, tSign.getItemStackTwo());
            if(tSign.getCashFlow() < 0) {
                creditAccount(player.getName(), Math.abs(tSign.getCashFlow()));
            }
        }
        player.updateInventory();
        updateSign(sign);
        addTransactionHistory(player, tSign, sign);
        if(tSign.getType().equals("buy")) {
            player.sendMessage(ChatColor.GOLD + "You have spent $" + ChatColor.DARK_GREEN + tSign.getCashFlow() + ChatColor.GOLD + " to purchase:");
        }
        if(tSign.getType().equals("sell")) {
            player.sendMessage(ChatColor.GOLD + "You have gained $" + ChatColor.DARK_GREEN + (-tSign.getCashFlow()) + ChatColor.GOLD + " by selling:");
        }
        if(tSign.getType().equals("take")) {
            player.sendMessage(ChatColor.GOLD + "You have obtained for free:");
        }
        if(tSign.getType().equals("give")) {
            player.sendMessage(ChatColor.GOLD + "You have given:");
        }
        if(tSign.getType().equals("trade")) {
            player.sendMessage(ChatColor.GOLD + "You have traded:");
        }
        if(tSign.getType().equals("give") || tSign.getType().equals("sell")) {
            player.sendMessage(ChatColor.GOLD + "" + tSign.getItemStackOne().getAmount() + " " + getItemName(tSign.getItemStackOne()));
        }
        if(tSign.getType().equals("trade")) {
            player.sendMessage(ChatColor.GOLD + "in exchange for:");
        }
        if(tSign.getType().equals("buy") || tSign.getType().equals("take")) {
            player.sendMessage(ChatColor.GOLD + "" + tSign.getItemStackTwo().getAmount() + " " + getItemName(tSign.getItemStackTwo()));
        }
        if(tSign.getType().equals("sell") || tSign.getType().equals("buy")) {
            player.sendMessage(ChatColor.GOLD + "You now have $" + ChatColor.DARK_GREEN + getBalance(player) + ChatColor.GOLD + "!");
        }
    }
    public void takeItem(Inventory inv, ItemStack itemstack) {
        for(ItemStack item: inv.all(itemstack.getType()).values()) {
            if(item.getDurability() == itemstack.getDurability()) {
                if(item.getAmount() > itemstack.getAmount()) {
                    item.setAmount(item.getAmount() - itemstack.getAmount());
                    return;
                } else if(item.getAmount() == itemstack.getAmount()) {
                    inv.setItem(inv.first(itemstack), null);
                    return;
                }
            }
        }
        int needed = itemstack.getAmount();
        for(ItemStack item: inv.all(itemstack.getType()).values()) {
            if(item.getDurability() == itemstack.getDurability()) {
                if(item.getAmount() > needed) {
                    inv.getItem(inv.first(item)).setAmount(item.getAmount() - needed);
                    needed = 0;
                } else if(item.getAmount() == needed){
                    inv.setItem(inv.first(item), null);
                    needed = 0;
                } else if (item.getAmount() < needed){
                    needed = needed - item.getAmount();
                    inv.setItem(inv.first(item), null);
                }
                if(needed == 0){
                    return;
                }
            }
        }
    }
    public boolean isGlobalSign(Sign sign) {
        if(sign.getLine(0).equalsIgnoreCase("(selling)") || sign.getLine(0).equalsIgnoreCase("(buying)") || sign.getLine(0).equalsIgnoreCase("trading")) {
            return true;
        }
        return false;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public void onChestUpdate(InventoryCloseEvent event) {
        if(event.getInventory().getType() == InventoryType.CHEST) {
            if(event.getInventory() instanceof DoubleChestInventory) {
                DoubleChestInventory dchest = (DoubleChestInventory) event.getInventory();
                Chest chest1 = (Chest)dchest.getLeftSide().getHolder();
                Chest chest2 = (Chest)dchest.getRightSide().getHolder();
                if(getLinks(chest1) > 0) {
                    updateSigns(chest1);
                }
                if(getLinks(chest2) > 0) {
                    updateSigns(chest2);
                }
            } else {
                Chest chest = (Chest)event.getInventory().getHolder();
                if(getLinks(chest) > 0) {
                    updateSigns(chest);
                }
            }
        }
    }
    public void addTransactionHistory(Player player, TransactionEvent ev, Sign sign) {
        int id = getId(sign);
        String customer = player.getName();
        String query = "";
        String type = ev.getType();
        if(type.equals("buy")) {
            query = "INSERT INTO tshophistory (sign, customer, cashflow, firstItem, type, firstQuantity) VALUES " +
                    "('" + id + "', '" + customer + "', '" + (-ev.getCashFlow()) + "', '" + getItemStorageName(ev.getItemStackOne()) + "', '" + type + "', '" + getItemStorageName(ev.getItemStackOne()) + "')";
        } else if (type.equals("sell")) {
            query = "INSERT INTO tshophistory (sign, customer, cashflow, secondItem, type, secondQuantity) VALUES " +
                    "('" + id + "', '" + customer + "', '" + (ev.getCashFlow()) + "', '" + getItemStorageName(ev.getItemStackTwo()) + "', '" + type + "', '" + ev.getItemStackTwo().getAmount() + "')";
        } else if (type.equals("trade")) {
            query = "INSERT INTO tshophistory (sign, customer, firstItem, firstQuantity, cashflow, secondItem, type, secondQuantity) VALUES " +
                    "('" + id + "', '" + customer + "', '" + getItemStorageName(ev.getItemStackOne()) + "', '" + getItemStorageName(ev.getItemStackOne()) + "', '0', '" + getItemStorageName(ev.getItemStackTwo()) + "', '"+type+"', '" + ev.getItemStackTwo().getAmount() + "')";
        } else if (type.equals("give")) {
            query = "INSERT INTO tshophistory (sign, customer, cashflow, secondItem, type, secondQuantity) VALUES " +
                    "('" + id + "', '" + customer + "', '0', '" + getItemStorageName(ev.getItemStackTwo()) + "', '" + type + "', '" + ev.getItemStackTwo().getAmount() + "')";
        } else if (type.equals("take")) {
            query = "INSERT INTO tshophistory (sign, customer, cashflow, firstItem, type, firstQuantity) VALUES " +
                    "('" + id + "', '" + customer + "', '0', '" + getItemStorageName(ev.getItemStackOne()) + "', '" + type + "', '" + getItemStorageName(ev.getItemStackOne()) + "')";
        }
        try {
            SQLupdate(query);
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
    }
    public void pullHistory(Sign sign, Player player) {
        int id = getId(sign);
        TSign sig = getShopSign(sign);
        String owner = sig.getPlayer();
        try {
            double sum = 0;
            if(sig.getType().equals("sell") || sig.getType().equals("buy")) {
                String query = "SELECT SUM(cashflow) as cashflow FROM tshophistory WHERE sign = '" + id + "'";
                ResultSet rs = resultSet(query);
                while (rs.next()) {
                    if(rs.getObject("cashflow") != null) {
                        sum = rs.getDouble("cashflow");
                    }
                }
            }
            String query = "SELECT * FROM tshophistory WHERE sign = '" + id + "'";
            ResultSet rs = resultSet(query);
            rs.afterLast();
            int i = 0;
            player.sendMessage(ChatColor.GOLD + "Sign Owner: " + owner);
            String type = sig.getType();
            while(rs.previous() && (i < 7)) {
                if(i == 0) {
                    int total = rs.getRow();
                    player.sendMessage(ChatColor.GOLD + "" + total + " transactions so far!");
                    if(type.equals("sell")) {
                        player.sendMessage(ChatColor.GOLD + "" + (total * rs.getInt("firstQuantity")) + " " + getItemName(rs.getString("firstItem")) + "'s sold!");
                    } else if (type.equals("buy")) {
                        player.sendMessage(ChatColor.GOLD + "" + (total * rs.getInt("secondQuantity")) + " " + getItemName(rs.getString("secondItem")) + "'s bought!");
                    } else if (type.equals("trade")) {
                        player.sendMessage(ChatColor.GOLD + "" + (total*rs.getInt("firstQuantity")) + " "+ rs.getString("firstItem")+"'s traded for:");
                        player.sendMessage(ChatColor.GOLD+""+(total*rs.getInt("secondQuantity")) + " " + rs.getString("secondItem") + "'s!");
                    } else if (type.equals("give")) {
                        player.sendMessage(ChatColor.GOLD + "" + (total * rs.getInt("firstQuantity")) + " " + rs.getString("firstItem") + "'s given away!");
                    } else if (type.equals("take")) {
                        player.sendMessage(ChatColor.GOLD + "" + (total * rs.getInt("secondQuantity")) + " " + rs.getString("secondItem") + "'s obtained!");
                    }
                    if(type.equals("sell") || type.equals("buy")) {
                        player.sendMessage(ChatColor.GOLD+"$"+sum+" total cash handled!");
                    }
                }
                i++;
                String date = (new SimpleDateFormat("MM-dd-yyyy HH:mm:ss")).format(new Date(rs.getTimestamp("time").getTime()));
                String customer = rs.getString("customer");
                if(type.equals("sell")) {
                    player.sendMessage(ChatColor.GOLD + date + "|| " + customer + " bought " + rs.getInt("firstQuantity") + " " + getItemName(rs.getString("firstItem")) + " for " + Math.abs(rs.getDouble("cashflow")));
                } else if (type.equals("buy")) {
                    player.sendMessage(ChatColor.GOLD + date + "|| " + customer + " sold " + rs.getInt("secondQuantity") + " " + getItemName(rs.getString("secondItem")) + " for " + Math.abs(rs.getDouble("cashflow")));
                } else if (type.equals("trade")) {
                    player.sendMessage(ChatColor.GOLD + date + "|| " + customer + " traded " + rs.getInt("firstQuantity") + " " + rs.getString("firstItem") + "'s for: " + rs.getInt("secondQuantity") + " " + rs.getString("secondItem") + "'s");
                } else if (type.equals("give")) {
                    player.sendMessage(ChatColor.GOLD + date + "|| " + customer + " took " + rs.getInt("firstQuantity") + " " + rs.getString("firstItem") + "'s");
                } else if (type.equals("take")) {
                    player.sendMessage(ChatColor.GOLD + date + "|| " + customer + " gave " + rs.getInt("secondQuantity") + " " + rs.getString("secondItem") + "'s");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
    }
    public int getLinks(Chest chest) {
        int result = 0;
        for(StoredSign sign: Signs.values()) {
            if(sign.getChestLocations().contains(chest.getLocation())) {
                result++;
            }
        }
        return result;
    }
    public List<StoredSign> getLinkedSigns(Chest chest) {
        List<StoredSign> signs = new ArrayList<StoredSign>();
        for(StoredSign sign: Signs.values()) {
            if(sign.getChestLocations().contains(chest.getLocation())) {
                signs.add(sign);
            }
        }
        return signs;
    }
    public void updateSigns(Chest chest) {
        List<StoredSign> signs = getLinkedSigns(chest);
        Set<Location> locs = Signs.keySet();
        for(Location loc: locs) {
            if(signs.contains(Signs.get(loc))) {
                updateSign(loc);
            }
        }
    }
    public void updateSign(Sign sign) {
        TSign sig = getShopSign(sign);
        int Stock = getStock(sig);
        int Room = getRoom(sig);
        if(sig.getType().equals("sell") || sig.getType().equals("take")) {
            sign.setLine(3, "Stock: " + Stock);
        } else if (sig.getType().equals("buy") || sig.getType().equals("give")) {
            sign.setLine(3, "Room:" + Room);
        } else {
            sign.setLine(3, "Stock:" + Stock + "|Room:" + Room);
        }
        sign.update();
    }
    public void updateSign(Location loc) {
        Sign sign = (Sign)loc.getBlock().getState();
        updateSign(sign);
    }
    public int getStock(TSign sign) {
        int total = 0;
        for(Chest chest: sign.getLinkedChests()) {
            total += getStock(chest, sign.getItemStackOne());
        }
        return total;
    }
    public int getStock(Chest chest, ItemStack item) {
        int total = 0;
        for(ItemStack items: chest.getBlockInventory().getContents()) {
            if(items != null) {
                if((items.getType() == item.getType()) && (items.getDurability() == item.getDurability())) {
                    total += items.getAmount();
                }
            }
        }
        return (total/item.getAmount());
    }
    public int getRoom(TSign sign) {
        int total = 0;
        for(Chest chest: sign.getLinkedChests()) {
            total += getRoom(chest, sign.getItemStackTwo());
        }
        return total;
    }
    public int getRoom(Chest chest, ItemStack item) {
        int total = 0;
        for(ItemStack items: chest.getBlockInventory().getContents()) {
            if(items != null) {
                if((items.getType() == item.getType()) && (items.getDurability() == item.getDurability())) {
                    total += items.getMaxStackSize() - items.getAmount();
                }
            } else {
                total += item.getMaxStackSize();
            }
        }
        return (total/item.getAmount());
    }
    public int getStock(Player player, ItemStack item) {
        int total = 0;
        for(ItemStack items: player.getInventory().getContents()) {
            if(items != null) {
                if((items.getType() == item.getType())&&(items.getDurability() == item.getDurability())) {
                    total += items.getAmount();
                }
            }
        }
        return (total/item.getAmount());
    }
    public void linkChest(String linker, Player player, Sign sign, Chest chest) {
        try {
            Location loc = chest.getLocation();
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            String world = loc.getWorld().getName();
            StoredSign sig = Signs.get(sign.getLocation());
            if(!sig.getChestLocations().contains(loc)) {
                int id = getId(sign);
                String query = "INSERT INTO tshopchests (sign, owner, x ,y ,z , world) VALUES ('" + id + "', '" + linker + "', '" + x + "', '" + y + "', '" + z + "', '" + world + "')";
                SQLupdate(query);
                Signs.get(sign.getLocation()).addChest(loc);
                updateSign(sign);
                if(player != null) {
                    tLink.remove(player.getName());
                    player.sendMessage(ChatColor.GOLD + "You have successfully linked that sign to that chest!");
                }
            } else if (player != null) {
                player.sendMessage(ChatColor.RED + "That sign and chest are already linked!");
            }
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
    }
    public void unlinkChest(String unlinker, Player player, Chest chest) {
        try {
            Location loc = chest.getLocation();
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            String world = loc.getWorld().getName();
            String query = "DELETE FROM tshopchests WHERE owner = '" + unlinker + "' AND x = '" + x + "' AND y = '" + y + "' AND z = '" + z + "' AND world = '" + world + "'";
            SQLupdate(query);
            for(StoredSign sign: getLinkedSigns(chest)) {
                if(sign.getPlayer().equalsIgnoreCase(unlinker)) {
                    sign.removeChest(loc);
                }
            }
            updateSigns(chest);
            if(player != null) {
                tUnlink.remove(player.getName());
                player.sendMessage(ChatColor.GOLD + "You have successfully unlinked that chest from your signs!");
            }
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
    }
    public void unlinkChestForce(Player player, Chest chest) {
        try {
            Location loc = chest.getLocation();
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            String world = loc.getWorld().getName();
            String query = "DELETE FROM tshopchests WHERE x = '" + x + "' AND y = '" + y + "' AND z = '" + z + "' AND world = '" + world + "'";
            SQLupdate(query);
            for(StoredSign sign: getLinkedSigns(chest)) {
                sign.removeChest(loc);
            }
            updateSigns(chest);
            if(player != null) {
                tUnlink.remove(player.getName());
                player.sendMessage(ChatColor.GOLD + "You have successfully unlinked that chest from all signs!");
            }
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
    }
    public void unlinkSign(Sign sign) {
        try {
            int id = getId(sign);
            String query = "DELETE FROM tshopchests WHERE id = '" + id + "'";
            SQLupdate(query);
            for(Location loc: Signs.get(sign.getLocation()).getChestLocations()) {
                Signs.get(sign.getLocation()).removeChest(loc);
            }
            updateSign(sign);
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
    }
    public double getBalance(Player player) {
        return econ.getBalance(player.getName());
    }
    public double getBalance(String player) {
        return econ.getBalance(player);
    }
    public String getItemStorageName(String line) {
        try{
            ItemStack item = OddItem.getItemStack(line);
            return item.getTypeId()+":"+item.getDurability();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    public String getItemStorageName(ItemStack item) {
        return item.getTypeId()+":"+item.getDurability();
    }
    public String getItemName(String line) {
        String[] name = line.split(":");
        ItemStack item = new ItemStack(Material.getMaterial(Integer.valueOf(name[0])));
        item.setDurability(Short.valueOf(name[1]));
        return getItemName(item);
    }
    public String getItemName(ItemStack item) {
        return (String) OddItem.getAliases(item).toArray()[0];
    }
    public void chargeAccount(String player, double price) {
        updateBalance(player, price, false);
    }
    public void creditAccount(String player, double price) {
        updateBalance(player, price, true);
    }
    public void updateBalance(String player, double amount, boolean type) {
        if(type) {
            econ.depositPlayer(player, amount);
        } else {
            econ.withdrawPlayer(player, amount);
        }
    }
    public TSign getShopSign(Sign sign) {
        if(!Signs.containsKey(sign.getLocation())) {
            return null;
        }
        try{
            TSign tsign = new TSign(sign, Signs.get(sign.getLocation()));
            return tsign;
        } catch (Exception e) {
            return null;
        }
    }
    public void getSignSelling(Player player, String item) {
        if(getItemName(getItemStorageName(item)) == null) {
            player.sendMessage(ChatColor.RED + "Invalid item!");
            return;
        }
        String itemsearch = getItemStorageName(item);
        String query = "SELECT * FROM tshopsigns WHERE firstItem = '" + itemsearch + "' AND world = '" + player.getWorld().getName() + "' AND type = 'sell'";
        try {
            ResultSet rs = resultSet(query);
            while(rs.next()){
                if(rs.getObject("id") != null) {
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");
                    int quantity = rs.getInt("firstQuantity");
                    double price = Math.abs(rs.getDouble("cashflow"));
                    player.sendMessage(ChatColor.GOLD + "Coords: " + ChatColor.AQUA + x + " " + y + " " + z + " " + ChatColor.BLUE + quantity + " " + item + " is being sold" + (isPriceCheckEnabled() ? (" for " + ChatColor.DARK_GREEN + "$" + price) : ""));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
    }
    public void getSignTrading(Player player, String item) {
        if(getItemName(getItemStorageName(item)) == null) {
            player.sendMessage(ChatColor.RED + "Invalid item!");
            return;
        }
        String itemsearch = getItemStorageName(item);
        String query = "SELECT * FROM tshopsigns WHERE firstItem = '" + itemsearch + "' AND world = '" + player.getWorld().getName() + "' AND type = 'trade'";
        try {
            ResultSet rs = resultSet(query);
            while(rs.next()) {
                if(rs.getObject("id") != null) {
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");
                    int quantity = rs.getInt("firstQuantity");
                    String item2 = getItemName(rs.getString("secondItem"));
                    int quantity2 = rs.getInt("secondQuantity");
                    player.sendMessage(ChatColor.GOLD + "Coords: " + ChatColor.AQUA + x + " " + y + " " + z + " " + ChatColor.BLUE + quantity + " " + item + " is being traded for " + quantity2 + " " + item2);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
    }
    public void getSignBuying(Player player, String item) {
        if(getItemName(getItemStorageName(item)) == null) {
            player.sendMessage(ChatColor.RED + "Invalid item!");
            return;
        }
        String itemsearch = getItemStorageName(item);
        String query = "SELECT * FROM tshopsigns WHERE secondItem = '" + itemsearch + "' AND world = '" + player.getWorld().getName() + "' AND type = 'buy'";
        try {
            ResultSet rs = resultSet(query);
            while(rs.next()) {
                if(rs.getObject("id") != null) {
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");
                    int quantity = rs.getInt("secondQuantity");
                    double price = Math.abs(rs.getDouble("cashflow"));
                    player.sendMessage(ChatColor.GOLD + "Coords: " + ChatColor.AQUA + x + " " + y + " " + z + " " + ChatColor.BLUE + quantity + " " + item + " is being bought" + (isPriceCheckEnabled() ? (" for " + ChatColor.DARK_GREEN + "$" + price) : ""));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if(block == null) {
            return;
        }
        Player player = event.getPlayer();
        if(block.getTypeId() == 54) {
            Chest chest = (Chest)block.getState();
            Sign sign = null;
            if(chest.getBlock().getRelative(BlockFace.EAST).getTypeId() == 54) {
                for(StoredSign sig: getLinkedSigns((Chest)chest.getBlock().getRelative(BlockFace.EAST).getState())) {
                    for(Location loc: Signs.keySet()) {
                        if(Signs.get(loc) == sig) {
                            sign = (Sign)loc.getBlock().getState();
                            String linker = getShopSign(sign).getPlayer();
                            linkChest(linker, null, sign, chest);
                            player.sendMessage(ChatColor.GOLD + "The adjacent linked chest has now been converted to a linked double chest");
                        }
                    }
                }
            }
            if(chest.getBlock().getRelative(BlockFace.NORTH).getTypeId() == 54) {
                for(StoredSign sig: getLinkedSigns((Chest)chest.getBlock().getRelative(BlockFace.NORTH).getState())) {
                    for(Location loc: Signs.keySet()) {
                        if(Signs.get(loc) == sig) {
                            sign = (Sign)loc.getBlock().getState();
                            String linker = getShopSign(sign).getPlayer();
                            linkChest(linker, null, sign, chest);
                            player.sendMessage(ChatColor.GOLD + "The adjacent linked chest has now been converted to a linked double chest");
                        }
                    }
                }
            }
            if(chest.getBlock().getRelative(BlockFace.WEST).getTypeId() == 54) {
                for(StoredSign sig: getLinkedSigns((Chest)chest.getBlock().getRelative(BlockFace.WEST).getState())) {
                    for(Location loc: Signs.keySet()) {
                        if(Signs.get(loc) == sig) {
                            sign = (Sign)loc.getBlock().getState();
                            String linker = getShopSign(sign).getPlayer();
                            linkChest(linker, null, sign, chest);
                            player.sendMessage(ChatColor.GOLD + "The adjacent linked chest has now been converted to a linked double chest");
                        }
                    }
                }
            }
            if(chest.getBlock().getRelative(BlockFace.SOUTH).getTypeId() == 54) {
                for(StoredSign sig: getLinkedSigns((Chest)chest.getBlock().getRelative(BlockFace.SOUTH).getState())) {
                    for(Location loc: Signs.keySet()) {
                        if(Signs.get(loc) == sig) {
                            sign = (Sign)loc.getBlock().getState();
                            String linker = getShopSign(sign).getPlayer();
                            linkChest(linker, null, sign, chest);
                            player.sendMessage(ChatColor.GOLD + "The adjacent linked chest has now been converted to a linked double chest");
                        }
                    }
                }
            }
            return;
        }
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        BlockState state = (block == null ? null : block.getState());
        if(state instanceof Chest) {
            Chest chest = (Chest)state;
            if(getLinks(chest) > 0) {
                unlinkChestForce(player, chest);
            }
        }
        if(state instanceof Sign) {
            Sign sign = (Sign)state;
            TSign tsign = getShopSign(sign);
            if(tsign == null) {
                return;
            }
            if(!tsign.getPlayer().equalsIgnoreCase(player.getName()) && !player.hasPermission("tshops.admin")) {
                event.setCancelled(true);
                player.sendMessage("You can't break this!");
                state.update();
                return;
            } else {
                removeSign(sign);
                player.sendMessage("Your Sign Shop was removed");
            }
        }
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public void onLogout(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if(tLink.containsKey(player.getName())) {
            tLink.remove(player.getName());
        }
        if(tUnlink.contains(player.getName())) {
            tUnlink.remove(player.getName());
        }
    }
    public void removeSign(Location loc) {
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        String world = loc.getWorld().getName();
        String query = "DELETE FROM tshopsigns WHERE x = '" + x + "' AND y = '" + y + "' AND z = '" + z + "' AND world = '" + world + "'";
        int id = getId(loc);
        String query2 = "DELETE FROM tshopchests WHERE sign = '" + id + "'";
        String query3 = "DELETE FROM tshophistory WHERE sign = '" + id + "'";
        try {
            SQLupdate(query);
            SQLupdate(query2);
            SQLupdate(query3);
            Signs.remove(loc);
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
    }
    public int getId(Location loc) {
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        String world = loc.getWorld().getName();
        try {
            String query = "SELECT id FROM tshopsigns WHERE x = '" + x + "' AND y = '" + y + "' AND z = '" + z + "' AND world = '" + world + "'";
            ResultSet rs = resultSet(query);
            while(rs.next()) {
                if(rs.getObject("id") != null) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error with TShop's SQL Connection");
            e.printStackTrace();
        }
        return (Integer) null;
    }
    public void removeSign(Sign sign) {
        removeSign(sign.getLocation());
    }
    public int getId(Sign sign) {
        return getId(sign.getLocation());
    }
    public ResultSet resultSet(String query) throws SQLException {
        if(!conn.isValid(1)) {
            connect();
        }
        Statement stmt = conn.createStatement();
        return stmt.executeQuery(query);
    }
    public void connect() throws SQLException {
        if(conn != null) {
            conn.close();
        }
        conn = DriverManager.getConnection(url, user, pass);
    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if(!(sender instanceof Player)) {
            return false;
        }
        Player player = (Player)sender;
        if(cmd.getName().equalsIgnoreCase("tshop")) {
            if(args.length == 0) {
                player.sendMessage(ChatColor.GOLD + "/tshop link");
                player.sendMessage(ChatColor.GOLD + "/tshop unlink");
                player.sendMessage(ChatColor.GOLD + "/tshop search [ITEM]");
                return true;
            }
            if(args[0].equalsIgnoreCase("link") && (player.hasPermission("tshop.create") || player.hasPermission("tshop.admin"))) {
                tUnlink.remove(player.getName());
                if(!tLink.containsKey(player.getName())) {
                    tLink.put(player.getName(), null);
                    player.sendMessage(ChatColor.GOLD + "Punch a sign to begin to link it!");
                    player.sendMessage(ChatColor.GOLD + "Type /tshop link again to cancel");
                } else {
                    tLink.remove(player.getName());
                }
                return true;
            }
            if(args[0].equalsIgnoreCase("unlink") && (player.hasPermission("tshop.create") || player.hasPermission("tshop.admin"))) {
                tLink.remove(player.getName());
                if(!tUnlink.contains(player.getName())) {
                    tUnlink.add(player.getName());
                    player.sendMessage(ChatColor.GOLD + "Punch a chest or sign to unlink it!");
                    player.sendMessage(ChatColor.GOLD + "Type /tshop unlink again to cancel");
                } else {
                    tUnlink.remove(player.getName());
                }
                return true;
            }
            if(args[0].equalsIgnoreCase("search") && (args.length == 2)) {
                player.sendMessage(ChatColor.GOLD + "Buying:");
                getSignBuying(player, args[1]);
                player.sendMessage(ChatColor.GOLD + "Selling:");
                getSignSelling(player, args[1]);
                player.sendMessage(ChatColor.GOLD + "Trading:");
                getSignTrading(player, args[1]);
                return true;
            }
        }
        return false;
    }
    private boolean isPriceCheckEnabled() {
        return pricecheck;
    }

}
