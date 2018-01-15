package dota_2_crawler.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import dota_2_crawler.model.Match;

@Repository
public interface MatchRepository extends MongoRepository<Match, String> {

}
