package crafters.dk.plugins.EntityList;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class EntityListPlugin extends JavaPlugin {
	public static final Logger log = Logger.getLogger("Minecraft");

	@Override
	public void onDisable() {
		log.info(getDescription().getName()+" version "+getDescription().getVersion()+" is disabled!");
	}

	@Override
	public void onEnable() {
		log.info(getDescription().getName()+" version "+getDescription().getVersion()+" is enabled!");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		// Tjek efter om det rent faktisk er en spiller der bruger kommandoen
		Player player;
		if (sender instanceof Player){
			player = Player.class.cast(sender);
		}else{
			return false;
		}

		// Sørger for kun dem med "entitylist.allowcommand" kan bruge kommandoen
		if (!player.hasPermission("entitylist.allowcommand")) {
			sender.sendMessage("Du har ikke lov til at bruge denne kommando.");
			return true;
		}

		// Tjek om kommandoen der bliver kaldt er "le" som er den vi lytter efter
		if (command.getName().equals("le")) {
			String filename;
			int limit = 0;

			// Hvis der er angivet et tal som første argument skal vi bruge det til at vise kun chuncks med færre entities end dette nummer.
			if (args.length > 0) {
				try {
					limit = Integer.parseInt(args[0]);
					args[0] = "";
					if (limit < 0) {
						limit = 0;
					}
				} catch (NumberFormatException e) {}
			}

			// Vi tillader at brugeren kan smide et filnavn med som argument. Hvis ikke bruger vi Java timestamp som filnavn.
			if (args.length > 1 || (args.length > 0 && args[0].length() > 0)) {
				StringBuilder sb = new StringBuilder();
				for (String string : args) {
					sb.append(string + " ");
				}
				filename = sb.toString().trim() + ".log";
			} else {
				filename = new Date().getTime() + ".log";
			}

			// Tjek efter om mappen vi ønsker at gemme i allerede findes og hvis ikke så lav den.
			if (getDataFolder().exists() == false) {
				if (getDataFolder().mkdir() == false) {
					sender.sendMessage("Could not create dir: " + getDataFolder().getAbsolutePath());
					return true;
				}
			}

			// Tjek om der allerede findes en fil med det navn og hvis der gør så lad vær med at gemme noget i den.
			File entityfile = new File(getDataFolder(), filename);
			if (entityfile.exists()) {
				sender.sendMessage("Filen \"" + filename + "\" eksisterer allerede og kan derfor ikke oprettes.");
				return true;
			}

			// Her skriver vi indholdet til filen
			try {
				FileWriter fstream = new FileWriter(entityfile);
				BufferedWriter out = new BufferedWriter(fstream);

				// Sør for at sorterer worlds efter hvor mange entities der er (flest = først)
				List<World> worldList = Bukkit.getWorlds();
				Collections.sort(worldList, new WorldComparator());

				if (limit != 0) {
					if (limit == 1) {
						out.write("This file does not contain chunks with lesser than " + limit + " entity!\n");
					} else {
						out.write("This file does not contain chunks with lesser than " + limit + " entities!\n");						
					}
				}

				// Vi tjekker entities for hver world
				for (World world : worldList) {
					HashMap<Chunk, ArrayList<Entity>> chunkMap = new HashMap<Chunk, ArrayList<Entity>>();

					out.write("Entities for world: " + world.getName() + "\n");

					// Find alle entities i den valgte world og gem dem sammen med den chunk hver entity tilhører
					for (Entity entity : world.getEntities()) {
						Chunk currentChunk = entity.getLocation().getChunk();

						if (!chunkMap.containsKey(currentChunk)) {
							chunkMap.put(currentChunk, new ArrayList<Entity>());
						}

						chunkMap.get(currentChunk).add(entity);
					}

					// Sortering efter chunks størrelse således at de chunks med flest entities bliver listet først
					ChunkEntityArrayListComparable compare = new ChunkEntityArrayListComparable(chunkMap);
					TreeMap<Chunk, ArrayList<Entity>> sorted_map = new TreeMap<Chunk, ArrayList<Entity>>(compare);
					sorted_map.putAll(chunkMap);

					// Udskriv til fil hver chunk med tilhørende entities
					for (Chunk chunk : sorted_map.keySet()) {
						if (chunk.getEntities().length >= limit) {
							out.write("\n\tEntities for chunk: x = " + chunk.getX() + " z = " + chunk.getZ() + " (total: " + chunkMap.get(chunk).size() + ")\n");

							// Sorter listen med entities således den type der er flest af står først
							sortList(chunkMap.get(chunk));

							for (Entity entity : chunkMap.get(chunk)) {
								out.write("\t\t" + rightPad(entity.getClass().getCanonicalName(), 60) + "\t" + writeCoord(entity.getLocation()) + "\n");
							}
						}
					}

					out.write('\n');
				}

				out.close();
			} catch (Exception e) {
				log.severe("Error from EntityListPlugin:");
				log.severe("Error: " + e.getMessage());
			}

			sender.sendMessage("Alle entities er gemt i: plugins/EntityList/" + filename);

			return true;
		} else {
			return false;
		}
	}

	/**
	 * Gør det muligt at sammenligne worlds således at en world med flest entities kommer først.
	 * 
	 * @author Jacob
	 */
	private class WorldComparator implements Comparator<World> {
		@Override
		public int compare(World o1, World o2) {
			int compareValue = Integer.compare(o1.getEntities().size(), o2.getEntities().size()) * -1;

			if (compareValue == 0) {
				compareValue = 1;
			}

			return compareValue;
		}
	}

	/**
	 * Returner en string der inholder alle koordianterne omkring en parantes som fx (1 2 3) for x: 1, y:2, z:3
	 * Formatet er valgt for at gøre det nemt at bruge det sammen med fx /tppos kommandoen.
	 * 
	 * @param location Et location object der angiver lokationen som der skal henvises til.
	 * @return En string med alle koordinaterne som (x y z)
	 */
	private static String writeCoord(Location location) {
		return "(" + (int) location.getX() + " " + (int) location.getY() + " " + (int) location.getZ() + ")"; 
	}

	/**
	 * Sørger for at den valgte streng fylder bredden "width". Hvis den ikke gør vil der blive tilføjet " " indtil pladsen er brugt.
	 * 
	 * @param string Streng der skal returneres.
	 * @param width Bredde på strengen.
	 * @return Returnere strengen samt et n antal mellemrum såfrem strengen ikke er "width" bred.
	 */
	private static String rightPad(String string, int width) {
		return String.format("%-" + width + "s", string).replace('0', ' ');
	}

	/**
	 * Benyttes til at sammenligne chunks for at finde ud af hvilken chunks der skal vises "først.
	 * Rækkefølgende er angivet således at den chunk med flest entities vil have første priotet.
	 * 
	 * @author Jacob
	 */
	private class ChunkEntityArrayListComparable implements Comparator<Chunk> {
		Map<Chunk, ArrayList<Entity>> base;
		public ChunkEntityArrayListComparable(Map<Chunk, ArrayList<Entity>> base) {
			this.base = base;
		}

		@Override
		public int compare(Chunk c1, Chunk c2) {
			int compareValue = Integer.compare(base.get(c1).size(), base.get(c2).size()) * -1;

			if (compareValue == 0) {
				compareValue = 1;
			}

			return compareValue;
		}
	}

	/**
	 * Sorterer listen af Entities således at den type entities der er flest af kommer til at stå først.
	 * 
	 * @param list ArrayList med Entity objekter der skal sorteres.
	 */
	private void sortList(ArrayList<Entity> list) {
		HashMap<String, Integer> map = new HashMap<String, Integer>();
		ArrayList<Entity> newList = new ArrayList<Entity>();

		for (Entity entity : list) {
			String className = entity.getClass().getCanonicalName();
			int value = 0;

			if (map.containsKey(className)) {
				value = map.get(className);
			}
			map.put(className, value + 1);
		}

		EntityArrayListComparable compare = new EntityArrayListComparable(map);
		TreeMap<String, Integer> sorted_map = new TreeMap<String, Integer>(compare);
		sorted_map.putAll(map);

		for (String entityClassName : sorted_map.keySet()) {
			for (Entity entity : list) {
				if (entity.getClass().getCanonicalName().equals(entityClassName)) {
					newList.add(entity);
				}
			}
		}

		list.clear();
		list.addAll(newList);
	}

	/**
	 *	Bruges til at sammenligne entities og sørger for at de entities der er flest af
	 *	vil få største priotet.
	 * 
	 * @author Jacob
	 */
	private class EntityArrayListComparable implements Comparator<String> {
		Map<String, Integer> base;
		public EntityArrayListComparable(Map<String, Integer> base) {
			this.base = base;
		}

		@Override
		public int compare(String i1, String i2) {
			int compareValue = base.get(i1).compareTo(base.get(i2)) * -1;

			if (compareValue == 0) {
				compareValue = i1.compareTo(i2);
			}

			return compareValue;
		}
	}
}
