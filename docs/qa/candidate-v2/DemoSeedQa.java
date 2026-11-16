import java.nio.file.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import javax.tools.ToolProvider;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.springframework.boot.SpringApplication;
import com.app.sme_health_backend.SmeHealthBackendApplication;
import tools.jackson.databind.json.JsonMapper;
public class DemoSeedQa {
 public static void main(String[] a)throws Exception{
  var json=JsonMapper.builder().build();var result=new LinkedHashMap<String,Object>();result.put("scriptHead",a[1]);
  var pg=DisposablePostgres.POSTGRES;var dir=Files.createTempDirectory("qa-seeder-classes-");
  int compiled=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,"-proc:none","-encoding","UTF-8","-classpath",System.getProperty("java.class.path"),"-d",dir.toString(),a[0]);
  if(compiled!=0)throw new AssertionError("Seeder compile failed");
  try(var loader=new URLClassLoader(new URL[]{dir.toUri().toURL()});var c=DriverManager.getConnection(DisposablePostgres.URL,"finsight_app",DisposablePostgres.PASSWORD)){
   var sentinel=UUID.randomUUID();try(var s=c.prepareStatement("INSERT INTO businesses(id,business_name,status,created_at,updated_at) VALUES (?, 'QA sentinel existing synthetic business','ACTIVE',now(),now())")){s.setObject(1,sentinel);s.executeUpdate();}
   try(var s=c.prepareStatement("INSERT INTO business_profiles(user_id,business_type,language_preference,whatsapp_opt_in,created_at) VALUES (?,'services','en',false,now())")){s.setObject(1,sentinel);s.executeUpdate();}
   c.setAutoCommit(false);var clazz=loader.loadClass("DemoScenarioSeeder");var seeder=clazz.getConstructor(Connection.class).newInstance(c);
   try {clazz.getMethod("seedAll").invoke(seeder);c.commit();result.put("seedSucceeded",true);}catch(java.lang.reflect.InvocationTargetException e){c.rollback();result.put("seedSucceeded",false);result.put("seedFailure",e.getCause().toString());}
   try(var s=c.prepareStatement("SELECT whatsapp_number,whatsapp_opt_in FROM business_profiles WHERE user_id=?")){s.setObject(1,sentinel);var rs=s.executeQuery();rs.next();result.put("existingSentinelSurvived",true);result.put("sentinelPhoneWasNull",true);result.put("sentinelPhoneStillNull",rs.getString(1)==null);result.put("sentinelOptIn",rs.getBoolean(2));}
   var businesses=new ArrayList<Map<String,Object>>();try(var s=c.createStatement();var rs=s.executeQuery("SELECT b.id,b.business_name,c.requested_modules::text,c.configured FROM businesses b JOIN business_configuration c ON c.business_id=b.id ORDER BY b.business_name")){while(rs.next()){var row=new LinkedHashMap<String,Object>();row.put("id",rs.getString(1));row.put("name",rs.getString(2));row.put("modules",json.readTree(rs.getString(3)));row.put("configured",rs.getBoolean(4));businesses.add(row);}}
   result.put("seededBusinesses",businesses);result.put("expectedSeededBusinesses",8);
   var roles=new ArrayList<Map<String,Object>>();try(var s=c.createStatement();var rs=s.executeQuery("SELECT m.role,count(*) FROM business_memberships m JOIN app_users u ON u.id=m.user_id WHERE u.email LIKE 'demo.%@finsight.local' GROUP BY m.role ORDER BY m.role")){while(rs.next())roles.add(Map.of("role",rs.getString(1),"memberships",rs.getLong(2)));}result.put("roles",roles);
   result.put("applicationSHA","71ac4e0e7e74cbfe2f08fe3a049d37cd26b0c7c3");
   if(Boolean.TRUE.equals(result.get("seedSucceeded"))){
    var keyField=clazz.getDeclaredField("CRYPTO_KEY_B64");keyField.setAccessible(true);String fixtureKey=(String)keyField.get(null);
    String fixturePassword=Files.readAllLines(Path.of(a[0])).stream().filter(l->l.contains("demo.owner@finsight.local")&&l.contains("OWNER")&&l.contains("FinSight@")).findFirst().orElseThrow().split("\"")[7];
    var app=SpringApplication.run(SmeHealthBackendApplication.class,"--server.port=0","--server.address=127.0.0.1","--spring.datasource.url="+DisposablePostgres.URL,"--spring.datasource.username=finsight_app","--spring.datasource.password="+DisposablePostgres.PASSWORD,"--spring.flyway.enabled=false","--app.documents.storage-dir="+DisposablePostgres.DOCUMENTS,"--app.whatsapp.scheduler.enabled=false","--app.whatsapp.provider=mock","--app.whatsapp.inbound.enabled=false","--finsight.crypto.keys.k1="+fixtureKey,"--spring.profiles.active=dev","--OCR_INTEGRATION_ENABLED=false","--app.ai.enabled=false","--logging.level.root=WARN");
    try {
     int port=((org.springframework.boot.web.server.context.WebServerApplicationContext)app).getWebServer().getPort();String base="http://127.0.0.1:"+port+"/api";
     var cookieManager=new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL);var client=java.net.http.HttpClient.newBuilder().cookieHandler(cookieManager).connectTimeout(java.time.Duration.ofSeconds(5)).build();
     java.util.function.BiFunction<String,Object,Map<String,Object>> post=(route,body)->{try{
      var cr=client.send(java.net.http.HttpRequest.newBuilder(URI.create(base+"/auth/csrf")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());String token=json.readTree(cr.body()).get("token").asText();
      var request=java.net.http.HttpRequest.newBuilder(URI.create(base+route)).header("Content-Type","application/json").header("X-XSRF-TOKEN",token).header("Idempotency-Key",UUID.randomUUID().toString()).POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
      var response=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());return Map.of("status",response.statusCode(),"body",response.body());
     }catch(Exception e){throw new RuntimeException(e);}};
     var login=post.apply("/auth/login",Map.of("email","demo.owner@finsight.local","password",fixturePassword));result.put("seedOwnerLoginStatus",login.get("status"));if(!login.get("status").equals(200))throw new AssertionError("Synthetic owner login failed");
     var runtime=new ArrayList<Map<String,Object>>();int index=0;
     for(var b:businesses){index++;String id=(String)b.get("id");var selected=post.apply("/businesses/active",Map.of("businessId",id));if(!selected.get("status").equals(200))throw new AssertionError("Business select failed");
      var config=client.send(java.net.http.HttpRequest.newBuilder(URI.create(base+"/business-configuration")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());var state=json.readTree(config.body());if(!state.get("configured").asBoolean()||state.get("requestedModules").size()!=15)throw new AssertionError("Seed config mismatch");
      for(var m:state.get("modules")){if(!m.get("enabled").asBoolean()||!m.get("available").asBoolean())throw new AssertionError("Seed module unavailable");}
      var statuses=new LinkedHashMap<String,Object>();statuses.put("businessId",id);statuses.put("configuration",config.statusCode());
      var counterpart=post.apply("/finance/counterparties",Map.of("displayName","Synthetic seed QA "+index,"type","CUSTOMER"));statuses.put("counterparty",counterpart.get("status"));
      var item=post.apply("/inventory/items",Map.of("sku","QA-SEED-"+index,"name","Synthetic seeded QA item","unit","unit","itemKind","RESALE_GOOD"));statuses.put("inventory",item.get("status"));
      var draft=Map.of("counterpartyName","Synthetic seed QA "+index,"documentDate","2026-11-16","lines",List.of(Map.of("description","Synthetic QA draft","quantity","1","unit","unit","unitPrice","1.00")));
      statuses.put("invoice",post.apply("/invoices",draft).get("status"));statuses.put("bill",post.apply("/bills",draft).get("status"));
      var account=post.apply("/finance/accounts",Map.of("accountName","Synthetic QA account "+index,"accountType","BANK","currency","PKR","openingBalance","0.00","openingDate","2026-01-01"));statuses.put("account",account.get("status"));
      if(account.get("status").equals(201)){String accountId=json.readTree((String)account.get("body")).get("id").asText();statuses.put("transaction",post.apply("/finance/transactions",Map.of("accountId",accountId,"eventType","DIRECT_SALE_RECEIPT","amount","1.00","effectiveDate","2026-11-16","description","Synthetic QA seeded-module verification")).get("status"));}
      runtime.add(statuses);
     }
     result.put("seededRuntimeWrites",runtime);
    } finally {app.close();}
   }
  }finally{pg.close();Files.writeString(Path.of(a[2]),json.writerWithDefaultPrettyPrinter().writeValueAsString(result));}
 }
}
