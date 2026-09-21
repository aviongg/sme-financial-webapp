package com.app.sme_health_backend;

import com.app.sme_health_backend.insight.repository.InsightRepository;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.score.repository.ScoreResultReader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class SmeHealthBackendApplicationTests {

	@MockitoBean
	private ScoreResultReader scoreResultReader;

	@MockitoBean
	private BusinessProfileRepository businessProfileRepository;

	@MockitoBean
	private InsightRepository insightRepository;

	@MockitoBean
	private MonthlyRecordRepository monthlyRecordRepository;

	@MockitoBean
	private RecommendationRepository recommendationRepository;

	@Test
	void contextLoads() {
	}

}
