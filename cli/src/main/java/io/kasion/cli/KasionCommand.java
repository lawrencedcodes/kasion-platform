package io.kasion.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import java.util.concurrent.Callable;

@Command(name = "kasion", mixinStandardHelpOptions = true, version = "kasion 0.1",
        description = "The Native Edge for Modern Java.",
        subcommands = { PushCommand.class })
public class KasionCommand implements Callable<Integer> {

    public static void main(String... args) {
        int exitCode = new CommandLine(new KasionCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        System.out.println("⚡ Kasion CLI 0.1");
        System.out.println("Try 'kasion push' to deploy.");
        return 0;
    }
}
