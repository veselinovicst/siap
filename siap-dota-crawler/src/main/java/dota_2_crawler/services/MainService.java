package dota_2_crawler.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

import dota_2_crawler.model.Account;
import dota_2_crawler.model.HeroData;
import dota_2_crawler.model.Match;
import dota_2_crawler.model.Player;
import dota_2_crawler.repository.MatchRepository;

@Service
public class MainService {

	private RestTemplate restTemplate;
	private MongoClient mongoClient;

	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	public MainService() {
		this.restTemplate = new RestTemplate();
		mongoClient = new MongoClient("localhost", 27017);
	}

	public void getHeroes() {
		System.out.println(">>> creating heroes collection");

		String response = this.restTemplate.getForObject("https://api.opendota.com/api/heroes", String.class);
		JsonArray array = new JsonParser().parse(response).getAsJsonArray();

		MongoDatabase database = mongoClient.getDatabase("dota2-crawler");
		try {
			database.createCollection("heroes");
		} catch (Exception e) {
			System.out.println(">>> collections already exists");
		}
		MongoCollection<Document> heroes = database.getCollection("heroes");

		for (JsonElement jElem : array) {
			Document doc = Document.parse(jElem.toString());
			heroes.insertOne(doc);
		}

		System.out.println(">>> create heroes collection");
	}

	public void getMatch() {
		System.out.println(">>> create matches collection");

		List<Match> matches = new ArrayList<>();
		long startingId = 3765946065L;
		String response = "";
		for (int i = 0; i < 5000; i++, startingId++) {
			try {
				response = this.restTemplate.getForObject("https://api.opendota.com/api/matches/" + startingId,
						String.class);
				System.out.println(response);
				JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
				System.out.println(obj.get("match_id"));
				Match m = new Match();
				m.setMatchId(obj.get("match_id").toString());
				m.setRadian_win(Boolean.parseBoolean(obj.get("radiant_win").toString()));
				List<Player> players = new ArrayList<>();
				int counterOfNullPlayers = 0;
				for (JsonElement item : obj.get("players").getAsJsonArray()) {
					if (item instanceof JsonObject) {
						JsonObject itemCopy = (JsonObject) item;
						Player p = new Player();
						p.setAccountId(itemCopy.get("account_id").toString());
						if (itemCopy.get("account_id").toString().equals("null")) {
							counterOfNullPlayers++;
						}
						p.setHeroId(itemCopy.get("hero_id").toString());
						players.add(p);
					}
				}
				/*
				 * obj.get("players").getAsJsonArray().forEach((item) -> { if (item instanceof
				 * JsonObject) { JsonObject itemCopy = (JsonObject) item; Player p = new
				 * Player(); p.setAccountId(itemCopy.get("account_id").toString()); if
				 * (itemCopy.get("account_id").toString().equals("null")) { throw new
				 * NullPointerException(); } p.setHeroId(itemCopy.get("hero_id").toString());
				 * players.add(p); } });
				 */
				if (counterOfNullPlayers > 3) {
					throw new NullPointerException();
				}
				m.setPlayers(players);
				matches.add(m);
				if (i % 3 == 0) {
					Thread.sleep(300);
				}
			} catch (Exception e) {
				i--;
				System.out.println("Not found for id = " + startingId);
			}
			System.out.println("MATCHES SIZE " + matches.size());
		}

		List<Account> accounts = new ArrayList<>();
		int matchNum = 1;
		for (Match m : matches) {
			for (Player p : m.getPlayers()) {
				System.out.println(
						"match_num" + matchNum + " match_id " + m.getMatchId() + " player " + p.getAccountId());
				if (p.getAccountId() == null || p.getAccountId().equals("null"))
					continue;
				Account a = new Account();
				try {
					response = this.restTemplate.getForObject(
							"https://api.opendota.com/api/players/" + p.getAccountId() + "/wl", String.class);
					JsonObject obj = new JsonParser().parse(response).getAsJsonObject();

					a.setAccountId(p.getAccountId());
					a.setLose(Integer.parseInt(obj.get("lose").toString()));
					a.setWin(Integer.parseInt(obj.get("win").toString()));
				} catch (Exception e) {
					System.out.println("Exception for account while getting account data: " + p.getAccountId());
				}

				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				try {
					response = this.restTemplate.getForObject(
							"https://api.opendota.com/api/players/" + p.getAccountId() + "/heroes", String.class);
					JsonArray objs = new JsonParser().parse(response).getAsJsonArray();
					List<HeroData> heroes = new ArrayList<>();
					for (JsonElement o : objs) {
						HeroData hd = new HeroData();
						hd.setHeroId(o.getAsJsonObject().get("hero_id").getAsString());
						hd.setWin(Integer.parseInt(o.getAsJsonObject().get("win").toString()));
						hd.setGames(o.getAsJsonObject().get("games").getAsInt());
						heroes.add(hd);
					}
					a.setHeroes(heroes);
					accounts.add(a);
				} catch (Exception e) {
					System.out.println("Exception for account while getting heroes: " + p.getAccountId());
				}
				matchNum++;
			}
		}

		System.out.println("WRITING TO DB");

		MongoDatabase database = mongoClient.getDatabase("dota2-crawler");
		try {
			database.createCollection("matches");
		} catch (Exception e) {
			System.out.println(">>> collections already exists");
		}
		MongoCollection<Document> matchesCollection = database.getCollection("matches");
		Gson gson = new Gson();
		for (Match m : matches) {
			String json = gson.toJson(m);
			Document doc = Document.parse(json);
			matchesCollection.insertOne(doc);
		}

		try {
			database.createCollection("accounts");
		} catch (Exception e) {
			System.out.println(">>> collections already exists");
		}
		MongoCollection<Document> accountCollection = database.getCollection("accounts");
		for (Account a : accounts) {
			String json = gson.toJson(a);
			Document doc = Document.parse(json);
			accountCollection.insertOne(doc);
		}

		System.out.println("MATCHES AND ACCOUNTS CREATED");
	}

	/***
	 * Unused method for parsing csv files.
	 */
	public void parseFiles() {
		String csvFile = "C:\\Users\\Stefan Veselinovic\\Documents\\Fax\\SIAP\\players.csv";
		BufferedReader br = null;
		String line = "";
		String cvsSplitBy = ",";

		List<Player> players = new ArrayList<Player>();
		List<Match> matches = new ArrayList<Match>();
		Match m = new Match();
		List<Player> matchPlayers = new ArrayList<Player>();
		try {
			int i = 0;
			br = new BufferedReader(new FileReader(csvFile));
			while ((line = br.readLine()) != null) {
				// use comma as separator
				String[] matchOutcomes = line.split(cvsSplitBy);

				/*
				 * System.out.println("Match [match_id= " + matchOutcomes[0] + " , account_id="
				 * + matchOutcomes[1] + ", hero_id" + "=" + matchOutcomes[2] + "]");
				 */
				if (i % 9 == 0) {
					m.setMatchId(matchOutcomes[0]);
					m.setPlayers(matchPlayers);
					matches.add(m);
					m = new Match();
					matchPlayers = new ArrayList<Player>();
				}
				Integer playerId = 0;
				try {
					playerId = Integer.parseInt(matchOutcomes[1]);
				} catch (Exception e) {
				}

				if (playerId.intValue() != 0) {
					Player p = new Player();
					p.setAccountId(matchOutcomes[1]);
					p.setHeroId(matchOutcomes[2]);
					players.add(p);
					matchPlayers.add(p);
				}
				i++;
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		System.out.println("Player size: " + players.size());
		System.out.println("Matches size: " + matches.size());

		List<Match> matchesWithAtleast6Players = new ArrayList<Match>();
		matches.forEach((Match match) -> {
			if (match.getPlayers().size() > 5) {
				matchesWithAtleast6Players.add(match);
			}
		});
		System.out.println("Matches with atleast 6 players, size: " + matchesWithAtleast6Players.size());
	}

	/***
	 * Unused method for parsing csv files.
	 */
	public void parsePlayerRaitings() {
		String csvFile = "C:\\Users\\Stefan Veselinovic\\Documents\\Fax\\SIAP\\player_ratings.csv";
		BufferedReader br = null;
		String line = "";
		String cvsSplitBy = ",";
		List<Integer> acc_ids = new ArrayList<>();
		try {
			br = new BufferedReader(new FileReader(csvFile));
			while ((line = br.readLine()) != null) {
				// use comma as separator
				String[] playersRaitings = line.split(cvsSplitBy);

				System.out.println("[acc_id= " + playersRaitings[0]);
				try {
					Integer rating = Integer.parseInt(playersRaitings[0]);
					if (rating.intValue() > 0)
						acc_ids.add(rating);
				} catch (Exception e) {
					System.out.println("Exception for " + playersRaitings[0]);
				}
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		System.out.println("ACCOUNTS SIZE : " + acc_ids.size());
	}

	public void updatePlayers() {
		MongoDatabase database = mongoClient.getDatabase("dota2-crawler");
		MongoCollection<Document> accountCollection = database.getCollection("accounts");

		accountCollection.find().forEach((Document d) -> {
			try {
				Gson gson = new Gson();
				Account a = new Account();

				a.setAccountId(d.getString("accountId"));
				a.setLose(d.getInteger("lose"));
				a.setWin(d.getInteger("win"));

				String response = this.restTemplate.getForObject(
						"https://api.opendota.com/api/players/" + d.getString("accountId") + "/heroes", String.class);
				JsonArray objs = new JsonParser().parse(response).getAsJsonArray();
				List<HeroData> heroes = new ArrayList<>();
				for (JsonElement o : objs) {
					HeroData hd = new HeroData();
					hd.setHeroId(o.getAsJsonObject().get("hero_id").getAsString());
					hd.setWin(Integer.parseInt(o.getAsJsonObject().get("win").toString()));
					hd.setGames(o.getAsJsonObject().get("games").getAsInt());
					heroes.add(hd);
				}
				a.setHeroes(heroes);

				accountCollection.deleteOne(new Document("accountId", d.getString("accountId")));

				String json = gson.toJson(a);
				Document doc = Document.parse(json);
				accountCollection.insertOne(doc);

				System.out.println("Player with ID: " + d.getString("accountId") + " updated.");
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				System.out.println("Exception occured while updating players");
			}
		});

		/*
		 * System.out.println(a.getAccountId() + " " + a.getWin() + " " + a.getLose() +
		 * " " + a.getHeroes().get(0).getHeroId() + " " +
		 * a.getHeroes().get(0).getGames());
		 */

		System.out.println("UPDATED PLAYERS !!!!!");
	}

	public void createCsvFile() throws FileNotFoundException {
		MongoDatabase database = mongoClient.getDatabase("dota2-crawler");
		MongoCollection<Document> accountCollection = database.getCollection("accounts");
		MongoCollection<Document> matchesCollection = database.getCollection("matches");
		MongoCollection<Document> heroesCollection = database.getCollection("heroes");

		PrintWriter pw = new PrintWriter(new File("csvFile3.csv"));
		StringBuilder sb = new StringBuilder();
		sb.append("playerSuccess");
		sb.append(',');
		sb.append("playerHeroSuccess");
		sb.append(',');
		sb.append("heroSuccess");
		sb.append(',');
		sb.append("heroRatio");
		sb.append(',');
		sb.append("playerWon");
		sb.append('\n');

		FindIterable<Document> matches = matchesCollection.find();
		MongoCursor<Document> iterator = matches.iterator();
		while (iterator.hasNext()) {

			Document d = iterator.next();

			boolean radiantWin = d.getBoolean("radian_win", true);

			List<Document> players = (List<Document>) d.get("players");
			sb = new StringBuilder();
			for (int i = 0; i < players.size(); i++) {

				Document p1 = players.get(i);
				String p1AccountId = p1.getString("accountId");
				// account information missing
				if (p1AccountId.equals("null")) {
					sb.append("50");
					sb.append(",");
					sb.append("50");
					sb.append(",");
					sb.append("50");
					sb.append(",");
					sb.append("2");
					sb.append(",");

					if (i < 5) {
						// radiant team
						if (radiantWin)
							sb.append("1");
						else
							sb.append("0");
					} else {
						// dire team
						if (radiantWin)
							sb.append("0");
						else
							sb.append("1");
					}

					sb.append("\n");
					continue;
				}
				String p1HeroId = p1.getString("heroId");
				Document p1Account = accountCollection.find(new Document("accountId", p1AccountId)).first();
				if (p1Account != null) {
					List<Document> p1AccountHeroes = (List<Document>) p1Account.get("heroes");
					Document p1AccHero = null;
					for (int j = 0; j < p1AccountHeroes.size(); j++) {
						p1AccHero = p1AccountHeroes.get(j);
						if (p1AccHero.getString("heroId").equals(p1HeroId))
							break;
					}
					int p1AccHeroWins = 0;
					int p1AccHeroGames = 0;
					if (p1AccHero != null) {
						p1AccHeroWins = p1AccHero.getInteger("win");
						p1AccHeroGames = p1AccHero.getInteger("games");
					}
					int p1AccWins = p1Account.getInteger("win");
					int p1AccLose = p1Account.getInteger("lose");

					float playerSuccess = 100 / (((float) (p1AccWins + p1AccLose)) / p1AccWins);

					float playerHeroSuccess = 100 / ((float) p1AccHeroGames / p1AccHeroWins);

					sb.append(playerSuccess);
					sb.append(",");
					sb.append(playerHeroSuccess);
					sb.append(",");
				}else {
					sb.append("50");
					sb.append(",");
					sb.append("50");
					sb.append(",");
				}
				Document p1Hero = heroesCollection.find(new Document("id", Integer.parseInt(p1HeroId))).first();
				if (p1Hero == null) {
					sb.append("50");
					sb.append(",");
					sb.append("2");
					sb.append(",");
				}else {
					String p1HeroStrength = p1Hero.get("win_pct").toString();
					double heroRatio = p1Hero.getDouble("kda_ratio");
					sb.append(p1HeroStrength);
					sb.append(",");
					sb.append(heroRatio);
					sb.append(",");
				}

				if (i < 5) {
					// radiant team
					if (radiantWin)
						sb.append("1");
					else
						sb.append("0");
				} else {
					// dire team
					if (radiantWin)
						sb.append("0");
					else
						sb.append("1");
				}

				sb.append("\n");
			}
			
			// ------------
			String[] sppliter = sb.toString().split("50,");
			if (sppliter.length > 12) {
				System.out.println("NOT ENOUGH DATA TO WRITE");
				continue;
			}
			// ------------
			
			pw.write(sb.toString());
			System.out.println("WROTE NEW 10 PLAYERS");
			System.out.println(sb.toString());
		}

		pw.close();
		System.out.println("done!");
	}
	
	public void createCsvFileTypeTwo() throws FileNotFoundException {
		MongoDatabase database = mongoClient.getDatabase("dota2-crawler");
		MongoCollection<Document> accountCollection = database.getCollection("accounts");
		MongoCollection<Document> matchesCollection = database.getCollection("matches");
		MongoCollection<Document> heroesCollection = database.getCollection("heroes");

		PrintWriter pw = new PrintWriter(new File("csvFileTypeTwo3.csv"));
		StringBuilder sb = new StringBuilder();
		sb.append("playerSuccess");
		sb.append(',');
		sb.append("playerHeroSuccess");
		sb.append(',');
		sb.append("heroSuccess");
		sb.append(',');
		sb.append("playerWon");
		sb.append('\n');
		
		

		FindIterable<Document> matches = matchesCollection.find();
		MongoCursor<Document> iterator = matches.iterator();
		while (iterator.hasNext()) {

			Document d = iterator.next();

			boolean radiantWin = d.getBoolean("radian_win", true);

			List<Document> players = (List<Document>) d.get("players");
			sb = new StringBuilder();
			float team1Success = 0;
			float team1HeroSuccess = 0;
			float team1HeroStrength = 0;
			float team2Success = 0;
			float team2HeroSuccess = 0;
			float team2HeroStrength = 0;
			float team1HeroRatio = 0;
			float team2HeroRatio = 0;
					
			int teamWon = radiantWin ? 1 : 2;
			for (int i = 0; i < players.size(); i++) {

				Document p1 = players.get(i);
				String p1AccountId = p1.getString("accountId");
				// account information missing
				if (p1AccountId.equals("null")) {
					sb.append("50");
					sb.append(",");
					sb.append("50");
					sb.append(",");
					sb.append("50");
					sb.append(",");
					
					if (i < 5) {
						// radiant team
						team1Success += 50;
						team1HeroStrength += 50;
						team1HeroSuccess += 50;
						team1HeroRatio += 2;
						if (radiantWin)
							sb.append("1");
						else
							sb.append("0");
					} else {
						// dire team
						team2Success += 50;
						team2HeroStrength += 50;
						team2HeroSuccess += 50;
						team2HeroRatio += 2;
						if (radiantWin)
							sb.append("0");
						else
							sb.append("1");
					}
					sb.append("\n");
					continue;
				}
				String p1HeroId = p1.getString("heroId");
				Document p1Account = accountCollection.find(new Document("accountId", p1AccountId)).first();
				if (p1Account != null) {
					List<Document> p1AccountHeroes = (List<Document>) p1Account.get("heroes");
					Document p1AccHero = null;
					for (int j = 0; j < p1AccountHeroes.size(); j++) {
						p1AccHero = p1AccountHeroes.get(j);
						if (p1AccHero.getString("heroId").equals(p1HeroId))
							break;
					}
					int p1AccHeroWins = 0;
					int p1AccHeroGames = 0;
					if (p1AccHero != null) {
						p1AccHeroWins = p1AccHero.getInteger("win");
						p1AccHeroGames = p1AccHero.getInteger("games");
					}
					int p1AccWins = p1Account.getInteger("win");
					int p1AccLose = p1Account.getInteger("lose");

					float playerSuccess = 100 / (((float) (p1AccWins + p1AccLose)) / p1AccWins);

					float playerHeroSuccess = 100 / ((float) p1AccHeroGames / p1AccHeroWins);

					if (i < 5) {
						team1Success += playerSuccess;
						team1HeroSuccess += playerHeroSuccess;
					}else {
						team2Success += playerSuccess;
						team2HeroSuccess += playerHeroSuccess;
					}
					sb.append(playerSuccess);
					sb.append(",");
					sb.append(playerHeroSuccess);
					sb.append(",");
				}else {
					if (i < 5) {
						team1Success += 50;
						team1HeroSuccess += 50;
					}else {
						team2Success += 50;
						team2HeroSuccess += 50;
					}
					sb.append("50");
					sb.append(",");
					sb.append("50");
					sb.append(",");
				}
				Document p1Hero = heroesCollection.find(new Document("id", Integer.parseInt(p1HeroId))).first();
				if (p1Hero == null) {
					if (i < 5) {
						team1HeroRatio += 2;
						team1HeroStrength += 50;
					}else {
						team2HeroRatio += 2;
						team2HeroStrength += 50;
					}
					sb.append("50");
					sb.append(",");
				}else {
					String p1HeroStrength = p1Hero.get("win_pct").toString();
					double p1HeroRatio = p1Hero.getDouble("kda_ratio");
					sb.append(p1HeroStrength);
					sb.append(",");
					if (i < 5) {
						team1HeroRatio += p1HeroRatio;
						team1HeroStrength += Float.parseFloat(p1HeroStrength);
					}else {
						team2HeroRatio += p1HeroRatio;
						team2HeroStrength += Float.parseFloat(p1HeroStrength);
					}
				}

				if (i < 5) {
					// radiant team
					if (radiantWin)
						sb.append("1");
					else
						sb.append("0");
				} else {
					// dire team
					if (radiantWin)
						sb.append("0");
					else
						sb.append("1");
				}

				sb.append("\n");
			}
			String[] sppliter = sb.toString().split("50,");
			if (sppliter.length > 12) {
				System.out.println("NOT ENOUGH DATA TO WRITE");
				continue;
			}
			
			StringBuilder sb2 = new StringBuilder();
			sb2.append(team1Success);
			sb2.append(",");
			sb2.append(team1HeroSuccess);
			sb2.append(",");
			sb2.append(team1HeroStrength);
			sb2.append(",");
			sb2.append(team1HeroRatio);
			sb2.append(",");
			sb2.append(team2Success);
			sb2.append(",");
			sb2.append(team2HeroSuccess);
			sb2.append(",");
			sb2.append(team2HeroStrength);
			sb2.append(",");
			sb2.append(team2HeroRatio);
			sb2.append(",");
			sb2.append(teamWon);
			sb2.append("\n");
			pw.write(sb2.toString());
			System.out.println("WROTE NEW MATCH");
			System.out.println(sb2.toString());
		}

		pw.close();
		System.out.println("done!");
	}

}
