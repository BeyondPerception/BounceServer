package ml.dent.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Logger {

    private static Logger instance;

    private InputStream  in;
    private OutputStream out;

    private PrintWriter pw;

    Logger(boolean isDaemon) {
        instance = this;
        if (isDaemon) {
            new Thread(() -> {
                ServerSocket controlSocket = null;
                try {
                    controlSocket = new ServerSocket(32565);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("Failed to start control server");
                    return;
                }
                while (true) {
                    try {
                        Socket socket = controlSocket.accept();

                        in = socket.getInputStream();
                        out = socket.getOutputStream();
                        pw = new PrintWriter(out, true);
                        Main.setIn(in);

                        socket.setKeepAlive(true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    public static Logger getInstance() {
        return instance;
    }

    public void print(Object msg) {
        if (pw != null && !pw.checkError()) {
            pw.print(msg);
            pw.flush();
        } else {
            System.out.print(msg);
        }
    }

    public void println(Object msg) {
        if (pw != null && !pw.checkError()) {
            pw.println(msg);
        } else {
            System.out.println(msg);
        }
    }

    public void log(Object msg) {
        System.out.print(msg);
        if (pw != null) {
            pw.print(msg);
            pw.flush();
        }
    }

    public void logln(Object msg) {
        System.out.println(msg);
        if (pw != null) {
            pw.println(msg);
        }
    }

    public void log(Throwable throwable) {
        throwable.printStackTrace();
        if (pw != null) {
            throwable.printStackTrace(pw);
        }
    }
}
