package dota_2_crawler.services;

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
		long startingId = 3662760227L;
		String response = "";
		for (int i = 0; i < 1; i++, startingId++) {
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
				obj.get("players").getAsJsonArray().forEach((item) -> {
					if(item instanceof JsonObject) {
						JsonObject itemCopy = (JsonObject)item;
						Player p = new Player();
						p.setAccountId(itemCopy.get("account_id").toString());
						if(itemCopy.get("account_id").toString().equals("null")) {
							throw new NullPointerException();
						}
						p.setHeroId(itemCopy.get("hero_id").toString());
						players.add(p);
					}
				});
				m.setPlayers(players);
				matches.add(m);
				if (i % 3 == 0) {
					Thread.sleep(300);
				}
			} catch (Exception e) {
				i--;
				System.out.println("Not found for id = " + startingId);
			}
		}
		
		List<Account> accounts = new ArrayList<>();
		for(Match m : matches) {
			for(Player p: m.getPlayers()) {
				response = this.restTemplate.getForObject("https://api.opendota.com/api/players/" + p.getAccountId()+"/wl",
						String.class);
				JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
				Account a = new Account();
				a.setAccountId(p.getAccountId());
				a.setLose(Integer.parseInt(obj.get("lose").toString()));
				a.setWin(Integer.parseInt(obj.get("win").toString()));
				response = this.restTemplate.getForObject("https://api.opendota.com/api/players/" + p.getAccountId()+"/heroes",
						String.class);
				JsonArray objs = new JsonParser().parse(response).getAsJsonArray();
				List<HeroData> heroes = new ArrayList<>();
				for(JsonElement o: objs) {
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

		MongoDatabase database = mongoClient.getDatabase("dota2-crawler");
		try {
			database.createCollection("matches");
		} catch (Exception e) {
			System.out.println(">>> collections already exists");
		}
		MongoCollection<Document> matchesCollection = database.getCollection("matches");
		Gson gson = new Gson();
		for(Match m: matches) {
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
		for(Account a: accounts) {
			String json = gson.toJson(a);
			Document doc = Document.parse(json);
			accountCollection.insertOne(doc);
		}
		
		System.out.println("MATCHES AND ACCOUNTS CREATED");
	}


}
