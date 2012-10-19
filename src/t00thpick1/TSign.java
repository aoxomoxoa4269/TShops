package t00thpick1;

import info.somethingodd.OddItem.OddItem;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.inventory.ItemStack;

public class TSign {
    private String player;
    private double cashFlow;
    private ItemStack itemOne;
    private ItemStack itemTwo;
    private Location location;
    private List<Location> chestLocations;
    private String type;
    private boolean valid = true;
 
    public TSign(Sign sign, StoredSign sig) {
        type = getSignType(sign.getLine(0));
        player = sig.getPlayer();
        chestLocations = sig.getChestLocations();
        if(type.equals("sell")){
        	itemOne = getItem(sign.getLine(1));
        	cashFlow = getCashFlow(sign.getLine(2));
        } else if (type.equals("buy")){
        	itemTwo = getItem(sign.getLine(1));
        	cashFlow = getCashFlow(sign.getLine(2));
        } else if (type.equals("trade")){
        	itemOne = getItem(sign.getLine(1));
        	itemTwo = getItem(sign.getLine(2));
        	cashFlow = 0;
        } else if (type.equals("take")){
        	itemOne = getItem(sign.getLine(1));
        	cashFlow = 0;
        } else if (type.equals("give")){
        	itemTwo = getItem(sign.getLine(1));
        	cashFlow = 0;
        }
        location = sign.getLocation();
    }
    public TSign(Sign sign, String owner) {
    	cashFlow = getCashFlow(sign.getLine(2));
        type = getSignType(sign.getLine(0));
		if(owner != null){
			player = owner;
		} else {
			player = "Server Sign";
		}
        chestLocations = new ArrayList<Location>();
        if(type.equals("sell")){
        	itemOne = getItem(sign.getLine(1));
        	cashFlow = getCashFlow(sign.getLine(2));
        } else if (type.equals("buy")){
        	itemTwo = getItem(sign.getLine(1));
        	cashFlow = getCashFlow(sign.getLine(2));
        } else if (type.equals("trade")){
        	itemOne = getItem(sign.getLine(1));
        	itemTwo = getItem(sign.getLine(2));
        	cashFlow = 0;
        } else if (type.equals("take")){
        	itemOne = getItem(sign.getLine(1));
        	cashFlow = 0;
        } else if (type.equals("give")){
        	itemTwo = getItem(sign.getLine(1));
        	cashFlow = 0;
        }
        location = sign.getLocation();
    }
    public boolean isValid(){
    	return valid;
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
	/**
	 * @return the item being given
	 */
    public ItemStack getItemStackOne() {
    	return itemOne;
    }
	/**
	 * @return the item being taken
	 */
    public ItemStack getItemStackTwo() {
    	return itemTwo;
    }
	/**
	 * @return the sign location
	 */
    public Location getLocation() {
    	return location;
    }
	/**
	 * @return the chest location
	 */
    public List<Location> getChestLocations(){
    	return chestLocations;
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
        return type;
    }
    private double getCashFlow(String line){
    	try{
    		return Double.valueOf(line.replace("$", ""));
    	} catch (NumberFormatException e){
    		valid = false;
    		return (Double) null;
    	}
    }
	private String getSignType(String line) {
		if(line.equalsIgnoreCase("[buying]")||line.equalsIgnoreCase("(buying)")){
			cashFlow = -cashFlow;
			return "sell";
		}
		if(line.equalsIgnoreCase("[selling]")||line.equalsIgnoreCase("(selling)")){
			return "buy";
		}
		if(line.equalsIgnoreCase("[trading]")||line.equalsIgnoreCase("(trading)")){
			return "trade";
		}
		if(line.equalsIgnoreCase("[give]")||line.equalsIgnoreCase("(give)")){
			return "give";
		}
		if(line.equalsIgnoreCase("[take]")||line.equalsIgnoreCase("(take)")){
			return "take";
		}
		valid = false;
		return null;
	}
	private int getItemQuantity(String line) {
		try{
			return Integer.valueOf(line.split(" ")[0]);
		} catch (NumberFormatException e){
			valid = false;
			return 0;
		}
	}
	private ItemStack getItem(String line){
		try{
			ItemStack ret = OddItem.getItemStack(line.split(" ")[1]);
			ret.setAmount(getItemQuantity(line));
			if(ret.getAmount()==0){
				return null;
			}
			return ret;
		} catch (Exception e){
			valid = false;
			return null;
		}
	}
}
