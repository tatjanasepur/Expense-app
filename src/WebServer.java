import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class WebServer {
    static final int PORT = 8080;
    static final String DB = "expenses.db";
    static final Gson gson = new GsonBuilder().create();

    record Expense(long id, String name, String category, double amount, String date) {}
    static Connection conn;

    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite:" + DB);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("""
                CREATE TABLE IF NOT EXISTS expenses(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  category TEXT NOT NULL,
                  amount REAL NOT NULL,
                  date TEXT NOT NULL
                )
            """);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Static
        server.createContext("/", ex -> {
            try {
                if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { cors(ex); send(ex,200,new byte[0],"text/plain"); return; }
                String path = ex.getRequestURI().getPath();
                if (path.equals("/")) path = "/index.html";
                Path file = Paths.get("web").resolve(path.substring(1)).normalize();
                if (!file.startsWith(Paths.get("web")) || !Files.exists(file) || Files.isDirectory(file)) { sendText(ex,404,"Not found"); return; }
                cors(ex);
                send(ex,200,Files.readAllBytes(file), mime(file.getFileName().toString()));
            } catch (Exception e){ e.printStackTrace(); sendText(ex,500,"Server error"); }
        });

        // Health
        server.createContext("/api/health", ex -> { cors(ex); if ("GET".equalsIgnoreCase(ex.getRequestMethod())) sendText(ex,200,"OK"); else sendText(ex,405,""); });

        // GET/POST /api/expenses
        server.createContext("/api/expenses", ex -> {
            try { cors(ex);
                if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                    var list = new ArrayList<Expense>();
                    try (Statement st = conn.createStatement();
                         ResultSet rs = st.executeQuery("SELECT id,name,category,amount,date FROM expenses ORDER BY datetime(date) DESC, id DESC")) {
                        while (rs.next()) list.add(new Expense(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getString(5)));
                    }
                    sendJson(ex,200,list); return;
                }
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject j = JsonParser.parseString(body).getAsJsonObject();
                    String name = j.get("name").getAsString().trim();
                    String category = j.get("category").getAsString().trim();
                    String date = j.has("date") && !j.get("date").isJsonNull() ? j.get("date").getAsString() : Instant.now().toString();

                    double amount;
                    try { amount = Double.parseDouble(j.get("amount").getAsString().replace(',','.')); }
                    catch (Exception ee){ sendJson(ex,400, Map.of("error","Neispravan iznos")); return; }

                    if (name.isEmpty() || category.isEmpty()){ sendJson(ex,400, Map.of("error","Prazna polja")); return; }

                    Expense saved;
                    try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO expenses(name,category,amount,date) VALUES(?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1,name); ps.setString(2,category); ps.setDouble(3,amount); ps.setString(4,date);
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()){ long id = rs.next()? rs.getLong(1):0; saved = new Expense(id,name,category,amount,date); }
                    }
                    sendJson(ex,201,saved); return;
                }
                if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex,200,new byte[0],"text/plain"); return; }
                sendText(ex,405,"");
            } catch (Exception e){ e.printStackTrace(); sendText(ex,500,"Server error"); }
        });

        // DELETE /api/expenses/{id}
        server.createContext("/api/expenses/", ex -> {
            try { cors(ex);
                String p = ex.getRequestURI().getPath();
                if (p.matches("/api/expenses/\\d+") && "DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
                    long id = Long.parseLong(p.substring(p.lastIndexOf('/')+1));
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM expenses WHERE id=?")) { ps.setLong(1,id); ps.executeUpdate(); }
                    ex.getResponseHeaders().add("Access-Control-Allow-Origin","*");
                    ex.sendResponseHeaders(204,-1); ex.close(); return;
                }
                if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex,200,new byte[0],"text/plain"); return; }
                sendText(ex,404,"Not found");
            } catch (Exception e){ e.printStackTrace(); sendText(ex,500,"Server error"); }
        });

        // /api/stats
        server.createContext("/api/stats", ex -> {
            try { cors(ex);
                if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { sendText(ex,405,""); return; }
                Map<String,Double> map = new LinkedHashMap<>();
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT category, SUM(amount) FROM expenses GROUP BY category ORDER BY 2 DESC")){
                    while (rs.next()) map.put(rs.getString(1), rs.getDouble(2));
                }
                sendJson(ex,200,map);
            } catch (Exception e){ e.printStackTrace(); sendText(ex,500,"Server error"); }
        });

        server.start();
        System.out.println("Server running on http://localhost:" + PORT);
    }

    // ---------- utils ----------
    static void cors(HttpExchange ex){ ex.getResponseHeaders().add("Access-Control-Allow-Origin","*");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers","Content-Type");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods","GET,POST,DELETE,OPTIONS"); }
    static void send(HttpExchange ex, int code, byte[] bytes, String ct) throws IOException {
        if (ct!=null) ex.getResponseHeaders().add("Content-Type", ct);
        ex.sendResponseHeaders(code, bytes.length);
        try(OutputStream os = ex.getResponseBody()){ os.write(bytes); }
    }
    static void sendText(HttpExchange ex, int code, String text) throws IOException { send(ex, code, text.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8"); }
    static void sendJson(HttpExchange ex, int code, Object obj) throws IOException { send(ex, code, gson.toJson(obj).getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8"); }
    static String mime(String name){
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".css"))  return "text/css; charset=utf-8";
        if (n.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (n.endsWith(".png"))  return "image/png";
        if (n.endsWith(".jpg")||n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".svg"))  return "image/svg+xml";
        return "application/octet-stream";
    }
}
