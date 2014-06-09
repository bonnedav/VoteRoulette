package com.mythicacraft.voteroulette.stats;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;

import com.mythicacraft.voteroulette.VoteRoulette;
import com.mythicacraft.voteroulette.Voter;
import com.mythicacraft.voteroulette.VoterManager;
import com.mythicacraft.voteroulette.utils.ConfigAccessor;


class StatUpdater implements Runnable {

	VoterManager vm = VoteRoulette.getVoterManager();

	StatUpdater() {
	}

	@Override
	public void run() {
		ConfigAccessor statsData = new ConfigAccessor("data" + File.separator + "stats.yml");
		List<VoteStat> stats = new ArrayList<VoteStat>();
		if(VoteRoulette.USE_DATABASE)  {

		} else {
			File[] files = new File(Bukkit.getPluginManager().getPlugin("VoteRoulette").getDataFolder().getAbsolutePath() + File.separator + "data" + File.separator + "playerdata").listFiles();
			if(files != null && files.length != 0) {
				for (File file : files) {
					if (file.isFile()) {
						if(file.isHidden()) continue;
						if(file.getName().endsWith(".yml")) {
							String uuid = file.getName();
							ConfigAccessor playerCfg = new ConfigAccessor("data" + File.separator + "playerdata" + File.separator + uuid);
							Voter voter = vm.getVoter(playerCfg.getConfig().getString("name", ""));
							if(voter.isReal()) {
								stats.add(new VoteStat(voter));
							}
						}
					}
				}
			}
		}

		Collections.sort(stats, new Comparator<VoteStat>(){
			public int compare(VoteStat v1, VoteStat v2) {
				return v2.getLifetimeVotes() - v1.getLifetimeVotes();
			}
		});

		//clear stats
		statsData.getConfig().set("vote-totals.lifetime", null);
		statsData.saveConfig();

		//add stats for top timetime votes
		int count = 0;
		for(VoteStat stat : stats) {
			if(count != 10) {
				statsData.getConfig().set("vote-totals.lifetime." + stat.getPlayerName(), stat.getLifetimeVotes());
				count++;
			} else {
				break;
			}
		}

		//add stats for top longest streaks
		count = 0;
		statsData.saveConfig();
		Collections.sort(stats, new Comparator<VoteStat>(){
			public int compare(VoteStat v1, VoteStat v2) {
				return v2.getLongestVoteStreak() - v1.getLongestVoteStreak();
			}
		});

		statsData.getConfig().set("vote-streaks.longest", null);
		statsData.saveConfig();
		for(VoteStat stat : stats) {
			if(count != 10) {
				statsData.getConfig().set("vote-streaks.longest." + stat.getPlayerName(), stat.getLongestVoteStreak());
				count++;
			} else {
				break;
			}
		}
		statsData.saveConfig();
	}
}