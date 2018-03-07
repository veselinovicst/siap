package dota_2_crawler.services;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.mongodb.client.MongoCollection;
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
		for (int i = 0; i < 100; i++, startingId++) {
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
				for(JsonElement item: obj.get("players").getAsJsonArray()) {
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
				/*obj.get("players").getAsJsonArray().forEach((item) -> {
					if (item instanceof JsonObject) {
						JsonObject itemCopy = (JsonObject) item;
						Player p = new Player();
						p.setAccountId(itemCopy.get("account_id").toString());
						if (itemCopy.get("account_id").toString().equals("null")) {
							throw new NullPointerException();
						}
						p.setHeroId(itemCopy.get("hero_id").toString());
						players.add(p);
					}
				});*/
				if(counterOfNullPlayers > 3) {
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
				System.out.println("match_num" + matchNum + " match_id " + m.getMatchId() + " player " +  p.getAccountId());
				if(p.getAccountId() == null || p.getAccountId().equals("null")) continue;
				response = this.restTemplate
						.getForObject("https://api.opendota.com/api/players/" + p.getAccountId() + "/wl", String.class);
				JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
				Account a = new Account();
				a.setAccountId(p.getAccountId());
				a.setLose(Integer.parseInt(obj.get("lose").toString()));
				a.setWin(Integer.parseInt(obj.get("win").toString()));
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				try {
				response = this.restTemplate.getForObject(
						"https://api.opendota.com/api/players/" + p.getAccountId() + "/heroes", String.class);
				}catch(Exception e) {
					System.out.println("Exception for account while getting heroes: " + p.getAccountId());
				}
				JsonArray objs = new JsonParser().parse(response).getAsJsonArray();
				List<HeroData> heroes = new ArrayList<>();
				for (JsonElement o : objs) {
					HeroData hd = new HeroData();
					hd.setHeroId(o.getAsJsonObject().get("hero_id").getAsString());
					hd.setWin(Integer.parseInt(o.getAsJsonObject().get("win").toString()));
					hd.setGames(o.getAsJsonObject().get("hero_id").getAsInt());
					heroes.add(hd);
				}
				a.setHeroes(heroes);
				accounts.add(a);
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
				}catch(Exception e) {}
				
				if(playerId.intValue() != 0) {
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
			if(match.getPlayers().size() > 5) {
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

}
