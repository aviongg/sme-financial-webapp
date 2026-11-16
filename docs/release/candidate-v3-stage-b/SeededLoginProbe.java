// Disposable component acceptance, not the Docker init/start/stop rehearsal.
// Never invokes the fixed-port seeder main or connects to a developer database.
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import javax.tools.ToolProvider;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.SmeHealthBackendApplication;
import org.springframework.boot.SpringApplication;
import tools.jackson.databind.json.JsonMapper;

public class SeededLoginProbe {
  public static void main(String[] args) throws Exception {
    Path root=Path.of(args[0]).toAbsolutePath(), output=Path.of(args[2]);
    var json=JsonMapper.builder().build(); var result=new LinkedHashMap<String,Object>();
    result.put("testedSHA",args[1]); result.put("scope","Real seed preflight + fresh V52 embedded PostgreSQL + ephemeral Spring; not Docker launcher acceptance");
    var pg=DisposablePostgres.POSTGRES;
    try {
      var classes=Files.createTempDirectory("stage-b-seeder-");
      int status=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"-proc:none","-encoding","UTF-8",
        "-classpath",System.getProperty("java.class.path"),"-d",classes.toString(),root.resolve("scripts/DemoScenarioSeeder.java").toString());
      if(status!=0)throw new AssertionError("Actual seeder compilation failed");
      try(var loader=new URLClassLoader(new URL[]{classes.toUri().toURL()})) {
        var type=loader.loadClass("DemoScenarioSeeder");
        try(var admin=pg.getPostgresDatabase().getConnection();var sql=admin.createStatement()) {
          sql.execute("COMMENT ON DATABASE postgres IS 'finsight-disposable-synthetic-demo:stage-b-component-probe'");
        }
        try(var connection=DriverManager.getConnection(DisposablePostgres.URL,"finsight_app",DisposablePostgres.PASSWORD)) {
          connection.setAutoCommit(false);var seeder=type.getConstructor(Connection.class).newInstance(connection);
          var guard=type.getDeclaredMethod("requireFreshDisposableDatabase");guard.setAccessible(true);guard.invoke(seeder);
          type.getMethod("seedAll").invoke(seeder);connection.commit();
        }
        var key=type.getDeclaredField("CRYPTO_KEY_B64");key.setAccessible(true);
        var accounts=type.getDeclaredField("DEMO_ACCOUNTS");accounts.setAccessible(true);
        var app=SpringApplication.run(SmeHealthBackendApplication.class,
          "--server.port=0","--server.address=127.0.0.1","--spring.config.import=",
          "--spring.datasource.url="+DisposablePostgres.URL,"--spring.datasource.username=finsight_app",
          "--spring.datasource.password="+DisposablePostgres.PASSWORD,"--spring.flyway.enabled=false",
          "--app.documents.storage-dir="+DisposablePostgres.DOCUMENTS,"--finsight.crypto.keys.k1="+key.get(null),
          "--spring.profiles.active=dev","--OCR_INTEGRATION_ENABLED=false","--app.ai.enabled=false","--app.ai.provider=mock",
          "--app.whatsapp.provider=mock","--app.whatsapp.inbound.enabled=false","--app.whatsapp.scheduler.enabled=false",
          "--app.recurring.scheduler.enabled=false","--app.monthly-projection.sweeper.enabled=false","--logging.level.root=WARN");
        try {
          int port=((org.springframework.boot.web.server.context.WebServerApplicationContext)app).getWebServer().getPort();
          String base="http://127.0.0.1:"+port+"/api";
          var checks=new ArrayList<Map<String,Object>>();
          result.put("logins",checks);
          for(String[] account:(String[][])accounts.get(null)) {
            var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
            var csrf=client.send(HttpRequest.newBuilder(URI.create(base+"/auth/csrf")).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(csrf.statusCode()!=200)throw new AssertionError("CSRF failed");
            String token=json.readTree(csrf.body()).get("token").asText();
            var login=client.send(HttpRequest.newBuilder(URI.create(base+"/auth/login")).timeout(Duration.ofSeconds(30))
              .header("Content-Type","application/json").header("X-XSRF-TOKEN",token)
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("email",account[0],"password",account[3])))).build(),HttpResponse.BodyHandlers.ofString());
            if(login.statusCode()!=200)throw new AssertionError("Seeded login failed for "+account[2]+": "+login.statusCode());
            var businesses=client.send(HttpRequest.newBuilder(URI.create(base+"/businesses")).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString());
            var rows=json.readTree(businesses.body());
            if(businesses.statusCode()!=200||!rows.isArray()||rows.size()!=8)throw new AssertionError("Eight businesses required for "+account[2]);
            for(var row:rows)if(!account[2].equals(row.get("role").asText()))throw new AssertionError("Membership role mismatch");
            // A user with eight memberships must explicitly choose an active
            // business, just as the current browser's business chooser does.
            var chooseCsrf=client.send(HttpRequest.newBuilder(URI.create(base+"/auth/csrf")).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(chooseCsrf.statusCode()!=200)throw new AssertionError("Selection CSRF failed");
            var selected=client.send(HttpRequest.newBuilder(URI.create(base+"/businesses/active")).timeout(Duration.ofSeconds(15))
              .header("Content-Type","application/json").header("X-XSRF-TOKEN",json.readTree(chooseCsrf.body()).get("token").asText())
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("businessId",rows.get(0).get("businessId").asText())))).build(),HttpResponse.BodyHandlers.ofString());
            if(selected.statusCode()!=200)throw new AssertionError("Explicit business selection failed for "+account[2]);
            var config=client.send(HttpRequest.newBuilder(URI.create(base+"/business-configuration")).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString());
            var state=json.readTree(config.body());
            // BusinessAuthorizationService deliberately denies general financial
            // data (including configuration GET) to SALESPERSON. Preserve that 403.
            boolean salesperson="SALESPERSON".equals(account[2]);
            if(salesperson) {
              if(config.statusCode()!=403)throw new AssertionError("Salesperson must not gain general financial read access");
            } else if(config.statusCode()!=200||!state.get("configured").asBoolean()||state.get("requestedModules").size()!=15) {
              result.put("failedConfiguration",Map.of("role",account[2],"status",config.statusCode(),"body",config.body()));
              throw new AssertionError("Canonical seed configuration missing for "+account[2]);
            }
            checks.add(Map.of("role",account[2],"email",account[0],"login",login.statusCode(),"businesses",rows.size(),"matchingRoleMemberships",8,"configuration",config.statusCode(),"requestedModules",salesperson?"denied by role":15));
          }
          if(checks.size()!=5)throw new AssertionError("All five roles required");
          result.put("logins",checks);result.put("status","PASS");
          System.out.println("PASS actual seeded credentials: all 5 logins, 40 matching-role memberships, canonical configuration");
        } finally { app.close(); }
      }
    } catch(Throwable error) {
      result.put("status","FAIL");result.put("failure",error.toString());throw error;
    } finally { pg.close();Files.writeString(output,json.writerWithDefaultPrettyPrinter().writeValueAsString(result)); }
  }
}
