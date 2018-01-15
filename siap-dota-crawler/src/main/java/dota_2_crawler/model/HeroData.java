package dota_2_crawler.model;

public class HeroData {

	private String heroId;
	private int games;
	private int win;
	
	
	public HeroData() {}


	public String getHeroId() {
		return heroId;
	}


	public void setHeroId(String heroId) {
		this.heroId = heroId;
	}


	public int getGames() {
		return games;
	}


	public void setGames(int games) {
		this.games = games;
	}


	public int getWin() {
		return win;
	}


	public void setWin(int win) {
		this.win = win;
	}
	
	
}
