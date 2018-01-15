package dota_2_crawler.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


@Document
public class Match {

	@Id
	private String matchId;
	private boolean radian_win;
	private List<Player> players;
	
	public Match() {}


	public String getMatchId() {
		return matchId;
	}


	public void setMatchId(String matchId) {
		this.matchId = matchId;
	}


	public boolean isRadian_win() {
		return radian_win;
	}

	public void setRadian_win(boolean radian_win) {
		this.radian_win = radian_win;
	}

	public List<Player> getPlayers() {
		return players;
	}

	public void setPlayers(List<Player> players) {
		this.players = players;
	}
	
	
}
