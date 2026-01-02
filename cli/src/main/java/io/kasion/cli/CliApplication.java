package io.kasion.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;

@SpringBootApplication
public class CliApplication implements CommandLineRunner {

	public static void main(String[] args) {
		// This boots Spring, which then calls run() below
		SpringApplication.run(CliApplication.class, args);
	}

	@Override
	public void run(String... args) {
		// Pass the command line arguments to our KasionCommand
		int exitCode = new CommandLine(new KasionCommand()).execute(args);
		System.exit(exitCode);
	}
}
