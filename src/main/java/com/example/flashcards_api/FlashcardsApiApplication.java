package com.example.flashcards_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Ponto de entrada da API de flashcards com repetição espaçada (estilo Anki). */
@SpringBootApplication
public class FlashcardsApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlashcardsApiApplication.class, args);
	}

}
