package dev.morling.disqustogiscus.command;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand()
@CommandLine.Command(
        name = "dtg",
        mixinStandardHelpOptions = true,
        subcommands = {
                GetCategoriesCommand.class, ImportCommand.class, DeleteAllDiscussionsCommand.class
        },
        description = "A command-line application for importing Disqus comments into Giscus"
)
public class DtgCommand {
}
