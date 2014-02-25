package com.mythicacraft.voteroulette;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.mythicacraft.voteroulette.utils.ConfigAccessor;
import com.mythicacraft.voteroulette.utils.Utils;

public class RewardManager {

	private static final Logger log = Logger.getLogger("VoteRoulette");
	private ArrayList<Reward> rewards = new ArrayList<Reward>();
	private ArrayList<Milestone> milestones = new ArrayList<Milestone>();
	private Reward defaultReward = null;
	private PlayerManager pm;

	private static VoteRoulette plugin;

	RewardManager(VoteRoulette instance) {
		plugin = instance;
		pm = VoteRoulette.getPlayerManager();
	}

	//reward methods
	void addReward(Reward reward) {
		rewards.add(reward);
	}

	void clearRewards() {
		rewards.clear();
	}

	public boolean hasDefaultReward() {
		if(defaultReward == null) return false;
		return true;
	}

	public Reward getDefaultReward() {
		return defaultReward;
	}

	public void setDefaultReward(Reward defaultReward) {
		this.defaultReward = defaultReward;
	}

	public boolean playerHasRewards(String playerName) {
		Reward[] qualRewards = getQualifiedRewards(playerName);
		if(qualRewards.length == 0) {
			return false;
		}
		return true;
	}

	public Reward[] getQualifiedRewards(String playerName) {

		ArrayList<Reward> qualifiedRewards = new ArrayList<Reward>();

		//iterate through all rewards
		for(Reward reward: rewards) {

			//check if reward has specific players set
			if(reward.hasPlayers()) {
				if(reward.containsPlayer(playerName)) {
					qualifiedRewards.add(reward);
					continue;
				}
				if(VoteRoulette.hasPermPlugin()) {
					if(reward.hasPermissionGroups()) {
						if(plugin.ONLY_PRIMARY_GROUP) {
							String primaryGroup = VoteRoulette.permission.getPrimaryGroup("", playerName);
							if(primaryGroup != null) {
								if(reward.containsPermGroup(primaryGroup)) {
									qualifiedRewards.add(reward);
								}
							} else {
								System.out.println("[VoteRoulette] Warning! Could not get the primary group for player: " + playerName);
							}
						} else {
							String[] groups = reward.getPermGroups();
							for(String group: groups) {
								if(VoteRoulette.permission.playerInGroup("",playerName, group)) {
									qualifiedRewards.add(reward);
									break;
								}
							}
						}
					}
				}
			}

			//check if reward has specific perm groups set
			else if(VoteRoulette.hasPermPlugin()) {
				if(reward.hasPermissionGroups()) {
					if(plugin.ONLY_PRIMARY_GROUP) {
						String primaryGroup = VoteRoulette.permission.getPrimaryGroup("", playerName);
						if(primaryGroup != null) {
							if(reward.containsPermGroup(primaryGroup)) {
								qualifiedRewards.add(reward);
							}
						} else {
							System.out.println("[VoteRoulette] Warning! Could not get the primary group for player: " + playerName);
						}
					} else {
						String[] groups = reward.getPermGroups();
						for(String group: groups) {
							if(VoteRoulette.permission.playerInGroup("",playerName, group)) {
								qualifiedRewards.add(reward);
								break;
							}
						}
					}
				} else {
					qualifiedRewards.add(reward);
				}
			} else {
				qualifiedRewards.add(reward);
			}
		}

		Reward[] rewardsArray;
		rewardsArray = new Reward[qualifiedRewards.size()];
		qualifiedRewards.toArray(rewardsArray);

		//filter out rewards not for the world player is standing in
		if(plugin.CONSIDER_REWARDS_FOR_CURRENT_WORLD) {
			OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
			ArrayList<Reward> worldFilteredRewards = new ArrayList<Reward>();
			if(op.isOnline()) {
				String worldName = op.getPlayer().getWorld().getName();
				for(Reward reward : rewardsArray) {
					if(reward.hasWorlds()) {
						if(reward.getWorlds().contains(worldName)) {
							worldFilteredRewards.add(reward);
						}
					} else {
						worldFilteredRewards.add(reward);
					}
				}
				if(!worldFilteredRewards.isEmpty()) {
					rewardsArray = new Reward[worldFilteredRewards.size()];
					worldFilteredRewards.toArray(rewardsArray);
				}
			}
		}
		return rewardsArray;
	}

	public void giveDefaultReward(String playerName) {
		if(defaultReward == null) {
			log.warning("[VoteRoulette] Player earned the default reward but there's no default reward set to give!");
			return;
		}
		administerRewardContents(defaultReward, playerName);
	}

	public void administerRewardContents(Reward reward, String playerName) {

		Player player = Bukkit.getPlayerExact(playerName);

		if(player == null) {
			pm.saveUnclaimedReward(playerName, reward.getName());
			return;
		}

		String worldName = player.getWorld().getName();

		if(Utils.worldIsBlacklisted(worldName)) {
			pm.saveUnclaimedReward(player.getName(), reward.getName());
			player.sendMessage(ChatColor.RED + "You cannot claim rewards in this world!");
			return;
		}
		if(reward.hasWorlds()) {
			if(!reward.getWorlds().contains(worldName)) {
				pm.saveUnclaimedReward(player.getName(), reward.getName());
				player.sendMessage(ChatColor.RED + "You must claim the reward \"" + reward.getName()  + "\"  in the world(s): " + Utils.worldsString(reward.getWorlds()));
				return;
			}
		}
		if(reward.hasItems()) {
			if(reward.getRequiredSlots() <= Utils.getPlayerOpenInvSlots(player)) {
				Inventory inv = player.getInventory();
				reward.updateLoreAndCustomNames(player.getName());
				ItemStack[] items = reward.getItems();
				for(int i = 0; i < items.length; i++) {
					inv.addItem(items[i]);
				}
			} else {
				pm.saveUnclaimedReward(player.getName(), reward.getName());
				player.sendMessage(ChatColor.RED + "You don't have the required space in your inventory for this reward (" + reward.getRequiredSlots() + " slots). Please type \"/vr claim\" once you have cleared enough room in your inventory.");
				return;
			}
		}
		if(reward.hasCurrency()) {
			VoteRoulette.economy.depositPlayer(playerName, reward.getCurrency());
		}
		if(reward.hasXpLevels()) {
			player.giveExpLevels(reward.getXpLevels());
		}
		if(reward.hasCommands()) {
			for(String command : reward.getCommands()) {
				if(command.startsWith("/")) {
					command = command.replaceFirst("/", "");
				}
				if(command.startsWith("(")) {
					command = command.replaceFirst("\\(", "");
					String[] delayedCommandData = command.split("\\)");
					String delayedCommand = delayedCommandData[1].trim();
					if(delayedCommand.startsWith("/")) {
						delayedCommand = delayedCommand.replaceFirst("/", "");
					}
					int delay;
					boolean runOnLogOff = false;
					if(delayedCommandData[0].contains("/")) {
						String[] delayedCommandOptions = delayedCommandData[0].split("/");
						delay = Integer.parseInt(delayedCommandOptions[0].trim());
						if(delayedCommandOptions[1].trim().equalsIgnoreCase("logoff") || delayedCommandOptions[1].trim().equalsIgnoreCase("log off")) {
							runOnLogOff = true;
						}
					} else {
						delay = Integer.parseInt(delayedCommandData[0].trim());
					}
					DelayedCommand dc = new DelayedCommand(delayedCommand, playerName, runOnLogOff);
					dc.runTaskLater(plugin, delay*20);
					VoteRoulette.delayedCommands.add(dc);
				} else {
					Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), command.replace("%player%", player.getName()));
				}
			}
		}
		if(plugin.MESSAGE_PLAYER) {
			if(reward.hasMessage()) {
				player.sendMessage(Utils.transcribeColorCodes(reward.getMessage().replace("%player%", player.getName())));
			} else {
				player.sendMessage(getRewardMessage(player, reward));
			}
		}
		if(plugin.LOG_TO_CONSOLE) {
			System.out.println("[VoteRoulette] " + player.getName() + " just earned the reward: " + reward.getName());
		}
	}

	/*
	 * 
	 * milestone methods
	 * 
	 */

	void addMilestone(Milestone milestone) {
		milestones.add(milestone);
	}

	void clearMilestones() {
		milestones.clear();
	}

	public void administerMilestoneContents(Milestone milestone, String playerName) {

		Player player = Bukkit.getPlayerExact(playerName);
		if(player == null) {
			pm.saveUnclaimedMilestone(playerName, milestone.getName());
			return;
		}

		String worldName = player.getWorld().getName();
		if(Utils.worldIsBlacklisted(worldName)) {
			pm.saveUnclaimedMilestone(player.getName(), milestone.getName());
			player.sendMessage(ChatColor.RED + "You cannot claim milestones in this world!");
			return;
		}
		if(milestone.hasWorlds()) {
			if(!milestone.getWorlds().contains(worldName)) {
				pm.saveUnclaimedMilestone(player.getName(), milestone.getName());
				player.sendMessage(ChatColor.RED + "You must claim the milestone \"" + milestone.getName()  + "\" in the world(s): " + Utils.worldsString(milestone.getWorlds()));
				return;
			}
		}
		if(milestone.hasItems()) {
			if(milestone.getRequiredSlots() <= Utils.getPlayerOpenInvSlots(player)) {
				Inventory inv = player.getInventory();
				milestone.updateLoreAndCustomNames(player.getName());
				ItemStack[] items = milestone.getItems();
				for(int i = 0; i < items.length; i++) {
					inv.addItem(items[i]);
				}
			} else {
				pm.saveUnclaimedMilestone(player.getName(), milestone.getName());
				player.sendMessage(ChatColor.RED + "You don't have the required space in your inventory for this milestone (" + milestone.getRequiredSlots() + " slots). Please type \"/vr claim\" once you have cleared enough room in your inventory.");
				return;
			}
		}
		if(milestone.hasCurrency()) {
			VoteRoulette.economy.depositPlayer(playerName, milestone.getCurrency());
		}
		if(milestone.hasXpLevels()) {
			player.giveExpLevels(milestone.getXpLevels());
		}
		if(milestone.hasCommands()) {
			for(String command : milestone.getCommands()) {
				if(command.startsWith("/")) {
					command = command.replaceFirst("/", "");
				}
				if(command.startsWith("(")) {
					command = command.replaceFirst("\\(", "");
					String[] delayedCommandData = command.split("\\)");
					String delayedCommand = delayedCommandData[1].trim();
					if(delayedCommand.startsWith("/")) {
						delayedCommand = delayedCommand.replaceFirst("/", "");
					}
					int delay;
					boolean runOnLogOff = false;
					if(delayedCommandData[0].contains("/")) {
						String[] delayedCommandOptions = delayedCommandData[0].split("/");
						delay = Integer.parseInt(delayedCommandOptions[0].trim());
						if(delayedCommandOptions[1].trim().equalsIgnoreCase("logoff") || delayedCommandOptions[1].trim().equalsIgnoreCase("log off")) {
							runOnLogOff = true;
						}
					} else {
						delay = Integer.parseInt(delayedCommandData[0].trim());
					}
					DelayedCommand dc = new DelayedCommand(delayedCommand, playerName, runOnLogOff);
					dc.runTaskLater(plugin, delay*20);
					VoteRoulette.delayedCommands.add(dc);
				} else {
					Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), command.replace("%player%", player.getName()));
				}
			}
		}
		if(plugin.MESSAGE_PLAYER) {
			if(milestone.hasMessage()) {
				player.sendMessage(Utils.transcribeColorCodes(milestone.getMessage().replace("%player%", player.getName())));
			} else {
				player.sendMessage(getMilestoneMessage(player, milestone));
			}
		}
		if(plugin.LOG_TO_CONSOLE) {
			System.out.println("[VoteRoulette] " + player.getName() + " just earned the milestone: " + milestone.getName());
		}
	}


	public boolean playerHasMilestones(String playerName) {
		if(getQualifiedMilestones(playerName).length == 0) {
			return false;
		}
		return true;
	}

	public Milestone[] getQualifiedMilestones(String playerName) {

		ArrayList<Milestone> qualifiedMilestones = new ArrayList<Milestone>();

		//iterate through all rewards
		for(Milestone milestone: milestones) {

			//check if reward has specific players set
			if(milestone.hasPlayers()) {
				if(milestone.containsPlayer(playerName)) {
					qualifiedMilestones.add(milestone);
					continue;
				}
				if(VoteRoulette.hasPermPlugin()) {
					if(milestone.hasPermissionGroups()) {
						if(plugin.ONLY_PRIMARY_GROUP) {
							String primaryGroup = VoteRoulette.permission.getPrimaryGroup("", playerName);
							if(primaryGroup != null) {
								if(milestone.containsPermGroup(primaryGroup)) {
									qualifiedMilestones.add(milestone);
								}
							} else {
								System.out.println("[VoteRoulette] Warning! Could not get the primary group for player: " + playerName);
							}
						} else {
							String[] groups = milestone.getPermGroups();
							for(String group: groups) {
								if(VoteRoulette.permission.playerInGroup("",playerName, group)) {
									qualifiedMilestones.add(milestone);
									break;
								}
							}
						}
					}
				}
			}

			//check if reward has specific perm groups set
			else if(VoteRoulette.hasPermPlugin()) {
				if(milestone.hasPermissionGroups()) {
					if(plugin.ONLY_PRIMARY_GROUP) {
						String primaryGroup = VoteRoulette.permission.getPrimaryGroup("", playerName);
						if(primaryGroup != null) {
							if(milestone.containsPermGroup(primaryGroup)) {
								qualifiedMilestones.add(milestone);
							}
						} else {
							System.out.println("[VoteRoulette] Warning! Could not get the primary group for player: " + playerName);
						}
					} else {
						String[] groups = milestone.getPermGroups();
						for(String group: groups) {
							if(VoteRoulette.permission.playerInGroup("",playerName, group)) {
								qualifiedMilestones.add(milestone);
								break;
							}
						}
					}
				} else {
					qualifiedMilestones.add(milestone);
				}
			} else {
				qualifiedMilestones.add(milestone);
			}
		}

		Milestone[] milestonesArray;
		milestonesArray = new Milestone[qualifiedMilestones.size()];
		qualifiedMilestones.toArray(milestonesArray);

		//filter out rewards not for the world player is standing in
		if(plugin.CONSIDER_MILESTONES_FOR_CURRENT_WORLD) {
			OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
			ArrayList<Milestone> worldFilteredMilestones = new ArrayList<Milestone>();
			if(op.isOnline()) {
				String worldName = op.getPlayer().getWorld().getName();
				for(Milestone milestone : milestonesArray) {
					if(milestone.hasWorlds()) {
						if(milestone.getWorlds().contains(worldName)) {
							worldFilteredMilestones.add(milestone);
						}
					} else {
						worldFilteredMilestones.add(milestone);
					}
				}
				if(!worldFilteredMilestones.isEmpty()) {
					milestonesArray = new Milestone[worldFilteredMilestones.size()];
					worldFilteredMilestones.toArray(milestonesArray);
				}
			}
		}
		return milestonesArray;
	}

	public boolean playerReachedMilestone(String playerName) {

		ConfigAccessor playerCfg = new ConfigAccessor("data" + File.separator + "players.yml");

		if(!playerHasMilestones(playerName)) return false;
		if(!playerCfg.getConfig().contains(playerName)) return false;

		Milestone[] playerMS = getQualifiedMilestones(playerName);
		int playerVotes = playerCfg.getConfig().getInt(playerName + ".lifetimeVotes");

		for(int i = 0; i < playerMS.length; i++) {
			int milVotes = playerMS[i].getVotes();
			if(playerMS[i].isRecurring()) {
				if (playerVotes % milVotes == 0) {
					return true;
				}
			}
			if(milVotes == playerVotes) {
				return true;
			}
		}
		return false;
	}

	public List<Milestone> getReachedMilestones(String playerName) {

		ConfigAccessor playerCfg = new ConfigAccessor("data" + File.separator + "players.yml");

		List<Milestone> reachedMils = new ArrayList<Milestone>();
		Milestone[] playerMS = getQualifiedMilestones(playerName);
		int playerVotes = playerCfg.getConfig().getInt(playerName + ".lifetimeVotes");

		for(int i = 0; i < playerMS.length; i++) {
			int milVotes = playerMS[i].getVotes();
			if(playerMS[i].isRecurring()) {
				if (playerVotes % milVotes == 0) {
					reachedMils.add(playerMS[i]);
					continue;
				}
			}
			if(milVotes == playerVotes) {
				reachedMils.add(playerMS[i]);
			}
		}

		Collections.sort(reachedMils, new Comparator<Milestone>(){
			public int compare(Milestone m1, Milestone m2) {
				return m1.getPriority() - m2.getPriority();
			}
		});

		return reachedMils;
	}

	private String getRewardMessage(Player player, Reward reward) {
		String rewardMessage = plugin.PLAYER_VOTE_MESSAGE;
		rewardMessage = rewardMessage.replace("%name%", reward.getName());
		rewardMessage = rewardMessage.replace("%player%", player.getName());
		rewardMessage = rewardMessage.replace("%server%", Bukkit.getServerName());
		rewardMessage = rewardMessage.replace("%type%", "reward");
		rewardMessage = rewardMessage.replace("%prizes%", getRewardPrizes(reward));
		return rewardMessage;
	}

	private String getRewardPrizes(Reward reward) {
		StringBuilder sb = new StringBuilder();
		if(reward.hasCurrency()) {
			sb.append("$" + reward.getCurrency());
		}
		if(reward.hasXpLevels()) {
			if(reward.hasCurrency()) {
				sb.append(", ");
			}
			sb.append(reward.getXpLevels() + " xp levels");
		}
		if(reward.hasItems()) {
			if(reward.hasCurrency() || reward.hasXpLevels()) {
				sb.append(", ");
			}
			sb.append(Utils.getItemListSentance(reward.getItems()));
		}
		return sb.toString();
	}

	private String getMilestoneMessage(Player player, Milestone milestone) {
		String milestoneMessage = plugin.PLAYER_VOTE_MESSAGE;
		milestoneMessage = milestoneMessage.replace("%name%", milestone.getName());
		milestoneMessage = milestoneMessage.replace("%player%", player.getName());
		milestoneMessage = milestoneMessage.replace("%server%", Bukkit.getServerName());
		milestoneMessage = milestoneMessage.replace("%type%", "milestone");
		milestoneMessage = milestoneMessage.replace("%prizes%", getMilstonePrizes(milestone));
		return milestoneMessage;
	}

	private String getMilstonePrizes(Milestone milestone) {
		StringBuilder sb = new StringBuilder();
		if(milestone.hasCurrency()) {
			sb.append("$" + milestone.getCurrency());
		}
		if(milestone.hasXpLevels()) {
			if(milestone.hasCurrency()) {
				sb.append(", ");
			}
			sb.append(milestone.getXpLevels() + " xp levels");
		}
		if(milestone.hasItems()) {
			if(milestone.hasCurrency() || milestone.hasXpLevels()) {
				sb.append(", ");
			}
			sb.append(Utils.getItemListSentance(milestone.getItems()));
		}
		return sb.toString();
	}

	public boolean rewardsContainChance(Reward[] rewards) {
		for(Reward reward : rewards) {
			if(reward.hasChance()) return true;
		}
		return false;
	}

	public boolean milestonesContainChance(List<Milestone> reachedMils) {
		for(Milestone milestone : reachedMils) {
			if(milestone.hasChance()) return true;
		}
		return false;
	}
}
