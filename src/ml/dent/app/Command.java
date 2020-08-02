package ml.dent.app;

import java.util.function.Consumer;

public class Command {

    private String name;
    private String description;

    private Consumer<String[]> command;

    public Command(String name, Consumer<String[]> command, String description) {
        this.name = name;
        this.description = description;
        this.command = command;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void execute(String[] args) {
        command.accept(args);
    }

    public String toString() {
        return name + " -- " + description;
    }
}
