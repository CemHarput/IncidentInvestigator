package com.CemHarput.IncidentInvestigator;

import org.springframework.boot.SpringApplication;

public class TestIncidentInvestigatorApplication {

	public static void main(String[] args) {
		SpringApplication.from(IncidentInvestigatorApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
