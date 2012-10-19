package t00thpick1;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
 
public class TransactionEvent extends Event{
    private static final HandlerList handlers = new HandlerList();
    private TSign sign;
    private String player;
	private double cashFlow;
 
    public TransactionEvent(TSign sig, String player) {
        sign = sig;
        cashFlow = sign.getCashFlow();
    }
    public String getOwner() {
        return sign.getPlayer();
    }
	/**
	 * @return the player
	 */
    public String getPlayer() {
        return player;
    }
	/**
	 * @return the price
	 */
    public double getCashFlow() {
    	return cashFlow;
    }
    public void setCashFlow(double cashflow) {
    	cashFlow = cashflow;
    }
	/**
	 * @return the item being given
	 */
    public ItemStack getItemStackOne() {
    	return sign.getItemStackOne();
    }
	/**
	 * @return the item being taken
	 */
    public ItemStack getItemStackTwo() {
    	return sign.getItemStackTwo();
    }
	/**
	 * @return the sign location
	 */
    public Location getLocation() {
    	return sign.getLocation();
    }
	/**
	 * @return the chest location
	 */
    public List<Location> getChestLocations(){
    	return sign.getChestLocations();
    }
    public List<Chest> getLinkedChests(){
    	List<Chest> chests = new ArrayList<Chest>();
    	for(Location loc: getChestLocations()){
    		chests.add((Chest)loc.getBlock().getState());
    	}
    	return chests;
    }
	/**
	 * @return the type of sign
	 */
    public String getType() {
        return sign.getType();
    }
 
    public HandlerList getHandlers() {
        return handlers;
    }
 
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
