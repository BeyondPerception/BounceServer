package ml.dent.app;

import io.netty.channel.ChannelFuture;
import ml.dent.connect.Connection;
import ml.dent.connect.ConnectionManager;
import ml.dent.net.MainServer;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class Main {
    public static final String VERSION = "1.0";

    private static Logger logger;

    public static  int     PORT            = 1111;
    private static int     VERBOSITY       = 1;
    private static int     VERBOSE_CHANNEL = -1;
    private static boolean ECHO            = false;
    private static boolean DAEMON          = false;

    private static final String authString = "hi";

    private static MainServer server;

    static ArrayList<Command> commands = new ArrayList<>();

    static {
        commands.add(new Command("stop", (args) -> {
            exit();
        }, "Stops the server from listening and closes all current connections"));

        commands.add(new Command("exit", (args) -> {
            // TODO
        }, "Closes the debug session but allows the server to continue running"));

        commands.add(new Command("help", (args) -> {
            printHelp();
        }, "Prints the help menu"));

        commands.add(new Command("print", (args) -> {
            printCommand(args);
        }, "Prints information about the given argument"));

        commands.add(new Command("verbose", (args) -> {
            setVerbosity(args);
        }, "Sets the verbosity to the given argument.\n" +
                "\t1 - Print connections and disconnections (DEFAULT)\n" +
                "\t2 - Print state changes and the full stack trace of exceptions\n" +
                "\t3 - Print packet size of reads\n" +
                "\t4 - Print bytes received\n\n" +
                "\tchannel <CHANNEL_NUM> - be verbose on just this channel. -1 for all channels"));

        commands.add(new Command("echo", (args) -> {
            setEcho(args);
        }, "Sets whether this server should echo received packets back to the sending client as well\n" +
                "\tON or OFF\n"));
        commands.add(new Command("kill", (args) -> {
            kill(args);
        }, "Kills the specified connection"));
    }

    private static Scanner file;

    private static CountDownLatch controlConnected = new CountDownLatch(1);

    public static void setIn(InputStream in) {
        file = new Scanner(in);
        controlConnected.countDown();
    }

    private static void executeCommands() {
        while (true) {
            try {
                controlConnected.await();
            } catch (InterruptedException e) {
                continue;
            }
            logger.print("> ");
            String line;
            try {
                line = file.nextLine();
            } catch (Exception e) {
                controlConnected = new CountDownLatch(1);
                continue;
            }
            String[] input = line.trim().split(" ");

            if (input[0].isEmpty()) {
                continue;
            }

            ArrayList<Command> possibleCommands = parseCommand(input[0]);

            if (possibleCommands.size() > 1) {
                logger.println("Ambiguous command. Possible options: ");
                for (Command com : possibleCommands) {
                    logger.println(com.getName());
                }
                continue;
            } else if (possibleCommands.size() == 0) {
                logger.println("Unknown command. Possible options: ");
                printHelp();
                continue;
            }
            Command command = possibleCommands.get(0);
            String[] args = Arrays.copyOfRange(input, 1, input.length);
            command.execute(args);
        }
    }

    private static ArrayList<Command> parseCommand(String input) {
        ArrayList<Command> possibleCommands = new ArrayList<>();
        int maxCommon = -1;
        for (Command command : commands) {
            if (input.length() > command.getName().length()) {
                continue;
            }
            int curCommon = 0;
            for (int i = 0; i < input.length() && i < command.getName().length(); i++) {
                if (input.charAt(i) != command.getName().charAt(i)) {
                    curCommon = -2;
                    break;
                } else {
                    curCommon++;
                }
            }
            if (curCommon > maxCommon) {
                maxCommon = curCommon;
                possibleCommands.clear();
                possibleCommands.add(command);
            } else if (curCommon == maxCommon) {
                possibleCommands.add(command);
            }
        }

        return possibleCommands;
    }

    private static void printHelp() {
        for (Command com : commands) {
            logger.println(com);
        }
    }

    private static void printCommand(String[] args) {
        if (args.length <= 0) {
            logger.println("Too few arguments to print command");
            return;
        }
        String query = args[0];
        switch (query) {
            case "connections":
                printConnections();
                break;
            case "channels":
                printChannels();
                break;
        }
    }

    private static void printConnections() {
        Collection<Connection> connections = server.getConnections();

        for (Connection connection : connections) {
            logger.println(connection);
        }
    }

    private static void printChannels() {
        logger.println("Active channels:");
        TreeMap<Integer, List<Connection>> channels = new TreeMap<>();
        Collection<Connection> connections = server.getConnections();

        for (Connection connection : connections) {
            channels.putIfAbsent(connection.getChannelNumber(), new ArrayList<>());
            channels.get(connection.getChannelNumber()).add(connection);
        }

        for (Integer val : channels.keySet()) {
            logger.println(val + " - " + channels.get(val));
        }
    }

    private static void kill(String[] args) {
        if (args.length <= 0) {
            logger.println("Too few arguments to kill command");
            logger.println("Expecting connection id or fully qualified address");
            return;
        }
        String val = args[0];
        if (val.matches("[0-9]+")) {
            // kill by id
            int id = Integer.parseInt(val);
            if (!server.kill(id)) {
                logger.println("Unable to find connection with id: " + val);
            }
        } else {
            // kill by address
            Collection<Connection> connections = server.getConnections();
            for (Connection connection : connections) {
                if (connection.remoteAddress().toString().equals(val)) {
                    connection.close("Disconnected by server");
                    return;
                }
            }
            logger.println("Unable to find connection with address: " + val);
        }
    }

    private static void setVerbosity(String[] args) {
        if (args.length <= 0) {
            logger.println("Too few arguments to verbose command");
            logger.println(parseCommand("verbose").get(0));
            return;
        }
        String val = args[0];
        if (!val.matches("[0-9]+") && !val.equals("channel")) {
            logger.println("Argument must be a number or \"channel\"");
            logger.println(parseCommand("verbose").get(0));
            return;
        }
        if (val.equals("channel")) {
            if (args.length <= 1) {
                logger.println("Too few arguments to verbose channel command");
                logger.println("Requires channel number argument");
                return;
            }
            String channelNum = args[1];
            if (!channelNum.matches("[0-9]+")) {
                logger.println("Argument to verbose channel command must be a number");
                return;
            }
            VERBOSE_CHANNEL = Integer.parseInt(channelNum);
        } else {
            VERBOSITY = Integer.parseInt(val);
        }
    }

    private static void setEcho(String[] args) {
        if (args.length <= 0) {
            logger.println("Too few arguments to echo command");
            logger.println("Options - ON or OFF");
            return;
        }
        String val = args[0].toLowerCase();
        switch (val) {
            case "on":
                ECHO = true;
                break;
            case "off":
                ECHO = false;
                break;
        }
    }

    private static void exit() {
        server.close();
        System.exit(0);
    }

    public static int getVerbosity() {
        return VERBOSITY;
    }

    public static int getVerboseChannel() {
        return VERBOSE_CHANNEL;
    }

    public static boolean getEcho() {
        return ECHO;
    }

    public static String getAuthString() {
        return authString;
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                String val = args[i];
                switch (val) {
                    case "-v":
                        VERBOSITY = 2;
                        break;
                    case "-vv":
                        VERBOSITY = 3;
                        break;
                    case "-vvv":
                        VERBOSITY = 4;
                        break;
                    case "-e":
                        ECHO = true;
                        break;
                    case "-d":
                        DAEMON = true;
                        break;
                    case "-p":
                        if (i == args.length - 1) {
                            logger.logln("-p requires an integer argument between 1 and 65535");
                            System.exit(1);
                        }
                        String portString = args[i + 1];
                        if (!portString.matches("[0-9]+")) {
                            logger.logln("-p requires an integer argument between 1 and 65535");
                            System.exit(1);
                        }
                        int port = Integer.parseInt(portString);
                        if (port < 1 || port > 65535) {
                            logger.logln("-p requires an integer argument between 1 and 65535");
                            System.exit(1);
                        }
                        PORT = port;
                        i++;
                        break;
                    case "--max-connections":
                        if (i == args.length - 1) {
                            logger.logln("--max-connections requires an integer argument");
                            System.exit(1);
                        }
                        String maxConnectionsString = args[i + 1];
                        if (!maxConnectionsString.matches("[0-9]+")) {
                            logger.logln("--max-connections requires an integer argument");
                            System.exit(1);
                        }
                        int maxConnections = Integer.parseInt(maxConnectionsString);
                        ConnectionManager.setMaxConnections(maxConnections);
                        i++;
                        break;
                    case "--max-pairs":
                        if (i == args.length - 1) {
                            logger.logln("--max-pairs requires an integer argument");
                            System.exit(1);
                        }
                        String maxPairsString = args[i + 1];
                        if (!maxPairsString.matches("[0-9]+")) {
                            logger.logln("--max-pairs requires an integer argument");
                            System.exit(1);
                        }
                        int maxPairs = Integer.parseInt(maxPairsString);
                        ConnectionManager.setMaxPairs(maxPairs);
                        i++;
                        break;
                }
            }
        }
        logger = new Logger(DAEMON);
        if (DAEMON) {
            System.out.println("Control server started on port 32565");
        } else {
            setIn(System.in);
        }
        server = new MainServer(PORT);
        logger.logln("Starting bounce server on port [" + PORT + "]...");
        ChannelFuture cf = server.listen();
        cf.addListener(future -> {
            logger.logln("Shutting down server");
            System.exit(0);
        });
        logger.logln("Server started. Waiting for connections");

        executeCommands();
    }
}
