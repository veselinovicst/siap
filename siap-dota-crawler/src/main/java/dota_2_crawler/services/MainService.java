package dota_2_crawler.services;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.util.JSON;

public class MainService {

	private RestTemplate restTemplate;
	private MongoClient mongoClient;

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

		long startingId = 3662760000L;
		String response = "";
		for (int i = 0; i < 100; i++, startingId++) {
			try {
				response = this.restTemplate.getForObject("https://api.opendota.com/api/matches/" + startingId,
						String.class);
				System.out.println(response);
				JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
				System.out.println(obj.get("match_id"));
				if (i % 3 == 0) {
					Thread.sleep(300);
				}
			} catch (Exception e) {
				i--;
				System.out.println("Not found for id = " + startingId);
			}

		}
	}

}
