package dota_2_crawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import dota_2_crawler.services.MainService;

@SpringBootApplication
public class SiapDotaCrawlerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SiapDotaCrawlerApplication.class, args);
		MainService ms = new MainService();
		//ms.getMatch();
		ms.getHeroes();
	}
}