package com.service.market_lens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MarketLensApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarketLensApplication.class, args);
	}

}
