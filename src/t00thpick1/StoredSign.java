package t00thpick1;

import java.util.List;

import org.bukkit.Location;

public class StoredSign {
    private String player;
    private List<Location> chestLocations;
 
    public StoredSign(String owner, List<Location> chests) {
        player = owner;
        chestLocations = chests;
    }
	/**
	 * @return the player
	 */
    public String getPlayer() {
        return player;
    }
	/**
	 * @return the chest location
	 */
    public List<Location> getChestLocations(){
    	return chestLocations;
    }
    public void addChest(Location loc){
    	chestLocations.add(loc);
    }
    public void removeChest(Location loc){
    	chestLocations.remove(loc);
    }
}
