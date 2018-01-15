package dota_2_crawler.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class Account {

	@Id
	private String accountId;
	private int win;
	private int lose;
	private List<HeroData> heroes;
	
	public Account() {}

	public String getAccountId() {
		return accountId;
	}

	public void setAccountId(String accountId) {
		this.accountId = accountId;
	}

	public int getWin() {
		return win;
	}

	public void setWin(int win) {
		this.win = win;
	}

	public int getLose() {
		return lose;
	}

	public void setLose(int lose) {
		this.lose = lose;
	}

	public List<HeroData> getHeroes() {
		return heroes;
	}

	public void setHeroes(List<HeroData> heroes) {
		this.heroes = heroes;
	}
	
	
}
