package com.app.sme_health_backend.samples;

import com.app.sme_health_backend.identity.dto.CsrfResponse;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real sessions/CSRF, application role, PostgreSQL locks and all F09 planning mutations. */
@SpringBootTest(properties = {"app.ai.enabled=false"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class SamplePlanningPostgreSqlIT extends DisposablePostgres {
    @Autowired AppUserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired BusinessMembershipRepository memberships;
    @Autowired BusinessProfileRepository profiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwordEncoder;
    static final String PASSWORD = "F09-synthetic-password-2026!";
    record Fixture(AppUser user, UUID businessId) { }

    Fixture fixture() {
        UUID business = UUID.randomUUID();
        businesses.saveAndFlush(new Business(business));
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(business); profile.setBusinessType("services"); profile.setLanguagePreference("en");
        profiles.saveAndFlush(profile);
        return member(business, MembershipRole.OWNER);
    }
    Fixture member(UUID business, MembershipRole role) {
        AppUser user = new AppUser();
        user.setEmail("f09." + UUID.randomUUID() + "@example.com"); user.setFullName("Synthetic F09 " + role);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD)); user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false); user = users.saveAndFlush(user);
        memberships.saveAndFlush(new BusinessMembership(user.getId(), business, role, MembershipStatus.ACTIVE));
        return new Fixture(user, business);
    }
    Cookie login(Fixture f) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        Cookie token = response.getCookie("XSRF-TOKEN"), initial = response.getCookie("FINSIGHT_SESSION");
        assertNotNull(token);
        var request = post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                .contentType("application/json").content(json.writeValueAsString(Map.of("email", f.user.getEmail(), "password", PASSWORD)));
        if (initial != null) request.cookie(initial);
        Cookie session = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(session);
        command(session, "POST", "/api/businesses/active", Map.of("businessId", f.businessId), 200);
        return session;
    }
    RequestPostProcessor csrf(Cookie session) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").cookie(session)).andExpect(status().isOk()).andReturn().getResponse();
        var cookie = response.getCookie("XSRF-TOKEN"); assertNotNull(cookie);
        var token = json.readValue(response.getContentAsString(), CsrfResponse.class);
        return request -> {
            var cookies = new ArrayList<>(Arrays.asList(request.getCookies() == null ? new Cookie[0] : request.getCookies()));
            cookies.removeIf(c -> c.getName().equals(cookie.getName())); cookies.add(cookie);
            request.setCookies(cookies.toArray(Cookie[]::new)); request.addHeader(token.headerName(), token.token()); return request;
        };
    }
    JsonNode command(Cookie session, String method, String path, Object body, int expected) throws Exception {
        var request = switch (method) { case "POST" -> post(path); case "PUT" -> put(path); case "DELETE" -> delete(path); default -> get(path); };
        request.cookie(session);
        if (!method.equals("GET")) request.with(csrf(session));
        if (body != null) request.contentType("application/json").content(body instanceof String s ? s : json.writeValueAsString(body));
        var response = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse();
        return response.getContentAsString().isBlank() ? null : json.readTree(response.getContentAsString());
    }
    Map<String,Object> activity(String intent) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("code", "S-" + UUID.randomUUID().toString().substring(0, 8)); result.put("name", "Synthetic planning activity");
        result.put("kind", "SAMPLE"); result.put("recoveryIntent", intent); return result;
    }
    Map<String,Object> plan(long expected, String recovery, boolean update) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put(update ? "expectedPlanVersion" : "expectedActivityVersion", expected); result.put("plannedRecovery", recovery);
        result.put("lines", List.of(
                Map.of("category", "MATERIAL", "description", "Material plan", "quantity", "2", "unit", "batch", "unitCost", "100.125"),
                Map.of("category", "LABOUR", "description", "Labour plan", "quantity", "3", "unit", "hour", "unitCost", "20.25"),
                Map.of("category", "OVERHEAD", "description", "Overhead plan", "quantity", "1", "unit", "batch", "unitCost", "19"),
                Map.of("category", "FREIGHT", "description", "Freight plan", "quantity", "1", "unit", "trip", "unitCost", "10"),
                Map.of("category", "OTHER", "description", "Other plan", "quantity", "1", "unit", "batch", "unitCost", "10")));
        return result; // exact cost 300.00
    }
    String path(JsonNode activity) { return "/api/samples/" + activity.get("id").asText(); }
    JsonNode create(Cookie session, String intent) throws Exception { return command(session,"POST","/api/samples",activity(intent),201); }
    JsonNode current(Cookie session, String path) throws Exception { return command(session,"GET",path,null,200); }
    JsonNode approve(Cookie session, String path, JsonNode plan) throws Exception {
        return command(session,"POST",path + "/plans/" + plan.get("id").asText() + "/approve",
                Map.of("expectedActivityVersion",current(session,path).get("version").asLong(),"expectedPlanVersion",plan.get("version").asLong()),200);
    }
    JsonNode transition(Cookie session,String path,String status,int expected) throws Exception {
        return command(session,"POST",path + "/transition",Map.of("targetStatus",status,"expectedVersion",current(session,path).get("version").asLong()),expected);
    }
    Map<String,String> financialSnapshot() {
        Map<String,String> result = new LinkedHashMap<>();
        for (String table : List.of("monthly_records","score_results","document_financial_contributions","projects",
                "project_budget_versions","project_budget_lines","insights","recommendations","uploaded_documents",
                "whatsapp_deliveries","business_configuration","dashboard_preferences","commercial_documents","commercial_document_lines","commercial_number_sequences"))
            result.put(table,jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM " + table + " t",String.class));
        return result;
    }

    @Test
    void freePlanRevisionsPreserveHistoryAndNeverMutateFinancialOrProjectRows() throws Exception {
        var owner = fixture(); var session = login(owner);
        // Nonempty legacy actual data and F05 approved budgets precede the isolation baseline.
        jdbc.update("INSERT INTO monthly_records(user_id,month,revenue,cogs,cash_balance_eom) VALUES (?,'2026-10',1000,500,200)",owner.businessId);
        jdbc.update("INSERT INTO score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness,methodology_version) VALUES (?,'2026-10',70,'Healthy','{}','cashflow',1,'health-score-v1')",owner.businessId);
        var project = command(session,"POST","/api/projects",Map.of("code","CTX-"+UUID.randomUUID().toString().substring(0,8),"name","Context only","kind","JOB"),201);
        var budget = plan(0,"500",false); budget.remove("expectedActivityVersion"); budget.remove("plannedRecovery"); budget.put("expectedProjectVersion",0); budget.put("plannedRevenue","500");
        var savedBudget = command(session,"POST","/api/projects/"+project.get("id").asText()+"/budgets",budget,201);
        command(session,"POST","/api/projects/"+project.get("id").asText()+"/budgets/"+savedBudget.get("id").asText()+"/approve",Map.of("expectedProjectVersion",1,"expectedBudgetVersion",0),200);
        UUID invoice=UUID.randomUUID();
        jdbc.update("INSERT INTO commercial_documents(id,business_id,kind,counterparty_name_ciphertext,document_date,subtotal,total,created_by_user_id,updated_by_user_id) VALUES (?,?,'INVOICE','enc:v1:fixture:QUJDREVGRw==','2026-10-28',100,100,?,?)",invoice,owner.businessId,owner.user.getId(),owner.user.getId());
        jdbc.update("INSERT INTO commercial_document_lines(business_id,document_id,line_number,description,quantity,unit,unit_price,line_amount) VALUES (?,?,1,'Preexisting invoice',2,'batch',50,100)",owner.businessId,invoice);
        jdbc.update("UPDATE commercial_documents SET status='FINALIZED',document_number='INV-000001',finalized_by_user_id=?,finalized_at=now(),version=1 WHERE id=?",owner.user.getId(),invoice);
        jdbc.update("INSERT INTO commercial_number_sequences(business_id,kind,next_value) VALUES (?,'INVOICE',2)",owner.businessId);
        Map<String,String> before = financialSnapshot();
        var payload=activity("FREE"); payload.put("projectId",project.get("id").asText());
        var sample=command(session,"POST","/api/samples",payload,201); String p=path(sample);
        assertEquals("PLANNED_ONLY",sample.get("financialBasis").asText()); assertEquals(owner.businessId.toString(),sample.get("businessId").asText());
        assertEquals(owner.user.getId().toString(),sample.get("createdBy").asText());
        var first=command(session,"POST",p+"/plans",plan(0,null,false),201);
        assertEquals("0.00",first.get("plannedRecovery").asText()); assertEquals("300.00",first.get("plannedCost").asText());
        assertEquals("-300.00",first.get("plannedContribution").asText());
        var approved=approve(session,p,first);
        assertEquals("APPROVED",approved.get("status").asText()); assertEquals(owner.user.getId().toString(),approved.get("approvedBy").asText());
        var historical=json.writeValueAsString(approved.get("lines"));
        var next=command(session,"POST",p+"/plans",plan(current(session,p).get("version").asLong(),"0",false),201);
        approve(session,p,next);
        var history=command(session,"GET",p+"/plans",null,200);
        assertEquals(2,history.size()); assertEquals("APPROVED",history.get(0).get("status").asText());
        assertEquals("SUPERSEDED",history.get(1).get("status").asText());
        assertEquals(next.get("id").asText(),history.get(1).get("supersededByPlanId").asText());
        assertEquals(historical,json.writeValueAsString(history.get(1).get("lines")));
        var single=command(session,"GET",p+"/plans/"+first.get("id").asText(),null,200);
        assertEquals("SUPERSEDED",single.get("status").asText());
        command(session,"PUT",p+"/plans/"+first.get("id").asText(),plan(single.get("version").asLong(),"0",true),409);
        command(session,"DELETE",p+"?expectedVersion="+current(session,p).get("version").asLong(),null,409);
        transition(session,p,"ACTIVE",200); transition(session,p,"COMPLETED",200); transition(session,p,"ARCHIVED",200);
        command(session,"POST",p+"/plans",plan(current(session,p).get("version").asLong(),null,false),409);
        var summary=command(session,"GET",p+"/planning-summary",null,200);
        assertEquals("PLANNED_ONLY",summary.get("financialBasis").asText());
        assertEquals(before,financialSnapshot(),"Every financial and F05 budget row must remain byte-equivalent");
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM security_audit_events WHERE business_id=? AND actor_user_id=? AND event_type LIKE 'SAMPLE_%'",Integer.class,owner.businessId,owner.user.getId())>=6);
    }

    @Test
    void paidAndPartialRecoveryKeepUnknownDistinctFromRecordedZeroAndFreeRejectsNonzero() throws Exception {
        var session=login(fixture());
        for(String intent:List.of("PAID","PARTIAL")) {
            var sample=create(session,intent); String p=path(sample);
            var draft=command(session,"POST",p+"/plans",plan(0,null,false),201);
            assertTrue(draft.get("plannedRecovery").isNull()); assertTrue(draft.get("plannedContribution").isNull());
            assertEquals("PLANNED_RECOVERY_UNKNOWN",draft.get("unavailableReasons").get("plannedContribution").asText());
            draft=command(session,"PUT",p+"/plans/"+draft.get("id").asText(),plan(0,"0",true),200);
            assertEquals("0.00",draft.get("plannedRecovery").asText()); assertEquals("-300.00",draft.get("plannedContribution").asText());
            draft=command(session,"PUT",p+"/plans/"+draft.get("id").asText(),plan(1,"450.125",true),200);
            assertEquals("150.125",draft.get("plannedContribution").asText());
            approve(session,p,draft);
        }
        var free=create(session,"FREE"); command(session,"POST",path(free)+"/plans",plan(0,"1",false),400);
    }

    @Test
    void safeDraftDeletionLifecycleAndRecoveryIntentLockAreExplicit() throws Exception {
        var session=login(fixture()); var sample=create(session,"PARTIAL"); String p=path(sample);
        var draft=command(session,"POST",p+"/plans",plan(0,null,false),201);
        var edit=activity("PAID"); edit.put("expectedVersion",1);
        command(session,"PUT",p,edit,409);
        transition(session,p,"ACTIVE",200);
        transition(session,p,"COMPLETED",409);
        command(session,"DELETE",p+"/plans/"+draft.get("id").asText()+"?expectedActivityVersion="+current(session,p).get("version").asLong()+"&expectedPlanVersion=0",null,204);
        transition(session,p,"CANCELLED",200); transition(session,p,"ARCHIVED",200);
        edit.put("expectedVersion",current(session,p).get("version").asLong()); command(session,"PUT",p,edit,409);
        transition(session,p,"ACTIVE",409);
        var deletable=create(session,"FREE"); String d=path(deletable);
        command(session,"POST",d+"/plans",plan(0,"0",false),201);
        command(session,"DELETE",d+"?expectedVersion=1",null,204);
        command(session,"GET",d,null,404);
    }

    @Test
    void ownerAndAccountantWriteManagerAndViewerReadOnlyAndSessionTenantControlsEveryResource() throws Exception {
        var a=fixture(); var b=fixture(); var owner=login(a); var foreign=login(b);
        var accountant=login(member(a.businessId,MembershipRole.ACCOUNTANT));
        var sample=create(accountant,"PAID"); String p=path(sample);
        assertEquals(a.businessId.toString(),sample.get("businessId").asText());
        var plan=command(accountant,"POST",p+"/plans",plan(0,"450",false),201);
        var approved=approve(accountant,p,plan);
        mvc.perform(get("/api/samples")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/samples").cookie(owner).contentType("application/json").content(json.writeValueAsString(activity("FREE")))).andExpect(status().isForbidden());
        for(MembershipRole role:List.of(MembershipRole.MANAGER,MembershipRole.VIEWER)) {
            var read=login(member(a.businessId,role));
            for(String suffix:List.of("","/plans","/planning-summary","/plans/"+plan.get("id").asText())) command(read,"GET",p+suffix,null,200);
            command(read,"GET","/api/samples",null,200);
            command(read,"POST","/api/samples",activity("FREE"),403);
            var edit=activity("PAID");edit.put("expectedVersion",current(owner,p).get("version").asLong());command(read,"PUT",p,edit,403);
            command(read,"POST",p+"/transition",Map.of("targetStatus","ACTIVE","expectedVersion",2),403);
            command(read,"POST",p+"/plans",plan(2,"500",false),403);
            command(read,"PUT",p+"/plans/"+plan.get("id").asText(),plan(approved.get("version").asLong(),"500",true),403);
            command(read,"POST",p+"/plans/"+plan.get("id").asText()+"/approve",Map.of("expectedActivityVersion",2,"expectedPlanVersion",1),403);
            command(read,"DELETE",p+"/plans/"+plan.get("id").asText()+"?expectedActivityVersion=2&expectedPlanVersion=1",null,403);
            command(read,"DELETE",p+"?expectedVersion=2",null,403);
        }
        for(String suffix:List.of("","/plans","/planning-summary","/plans/"+plan.get("id").asText()))command(foreign,"GET",p+suffix,null,404);
        var f=activity("FREE");f.put("expectedVersion",2);command(foreign,"PUT",p,f,404);
        command(foreign,"POST",p+"/plans",plan(2,null,false),404);
        command(foreign,"DELETE",p+"?expectedVersion=2",null,404);
        assertEquals(0,command(foreign,"GET","/api/samples",null,200).get("items").size());
    }

    @Test
    void sameBusinessProjectLinkIsContextOnlyAndArchivedOrForeignNewLinksAreRejected() throws Exception {
        var a=fixture();var b=fixture();var owner=login(a);var foreign=login(b);
        var project=command(owner,"POST","/api/projects",Map.of("code","CTX","kind","PROJECT","name","Context project"),201);
        var payload=activity("FREE");payload.put("projectId",project.get("id").asText());
        command(foreign,"POST","/api/samples",payload,404);
        var sample=command(owner,"POST","/api/samples",payload,201);String p=path(sample);
        command(owner,"POST","/api/projects/"+project.get("id").asText()+"/transition",Map.of("targetStatus","CANCELLED","expectedVersion",0),200);
        command(owner,"POST","/api/projects/"+project.get("id").asText()+"/transition",Map.of("targetStatus","ARCHIVED","expectedVersion",1),200);
        payload=activity("FREE");payload.put("projectId",project.get("id").asText());command(owner,"POST","/api/samples",payload,409);
        payload.put("expectedVersion",0);command(owner,"PUT",p,payload,200); // Existing archived context may be retained.
        assertEquals(project.get("id").asText(),current(owner,p).get("projectId").asText());
    }

    @Test
    void linkedDraftProjectDeleteConflictsUntilOpenSampleIsExplicitlyDetached() throws Exception {
        var owner=fixture(); var session=login(owner);
        var project=command(session,"POST","/api/projects",Map.of("code","LINK-DELETE","kind","PROJECT","name","Draft project context"),201);
        String projectPath="/api/projects/"+project.get("id").asText();
        var payload=activity("FREE");payload.put("projectId",project.get("id").asText());
        var sample=command(session,"POST","/api/samples",payload,201);String p=path(sample);
        var before=financialSnapshot();
        var rejected=command(session,"DELETE",projectPath+"?expectedVersion=0",null,409);
        assertEquals("project_delete_forbidden",rejected.get("error").asText());
        assertEquals(before,financialSnapshot(),"Rejected deletion must roll back every project and financial change");
        assertEquals(project.get("id").asText(),current(session,p).get("projectId").asText());
        assertEquals(0,current(session,projectPath).get("version").asLong());
        payload.remove("projectId");payload.put("expectedVersion",0);
        command(session,"PUT",p,payload,200);
        assertTrue(current(session,p).get("projectId").isNull());
        assertEquals(before,financialSnapshot(),"Detaching sample context never mutates project values");
        command(session,"DELETE",projectPath+"?expectedVersion=0",null,204);
        command(session,"GET",projectPath,null,404);
        assertEquals("DRAFT",current(session,p).get("status").asText());
    }

    @Test
    void closedSchemasRejectClientAuthorityOnActivityAndPlanRequests() throws Exception {
        var session=login(fixture());
        for(String field:List.of("businessId","actorUserId","createdBy","updatedBy","financialBasis","status","plannedCost","approvedBy","supersededBy")) {
            var payload=activity("FREE");payload.put(field,"forged");command(session,"POST","/api/samples",payload,400);
        }
        var sample=create(session,"PAID");String p=path(sample);
        for(String field:List.of("businessId","actorUserId","createdBy","updatedBy","financialBasis","status","plannedCost","plannedContribution","approvedBy","supersededBy")) {
            var payload=plan(0,null,false);payload.put(field,"forged");command(session,"POST",p+"/plans",payload,400);
        }
        command(session,"POST",p+"/plans","{\"expectedActivityVersion\":0,\"plannedRecovery\":1,\"lines\":[]}",400);
        command(session,"POST","/api/samples","{\"code\":\"A\",\"code\":\"B\",\"kind\":\"SAMPLE\",\"name\":\"Bad\",\"recoveryIntent\":\"FREE\"}",400);
    }

    /** Two real concurrent HTTP service transactions, each authenticated with its own session/CSRF token. */
    List<Integer> race(Cookie first,Cookie second,String method,String path,Object payload) throws Exception {
        var token1=csrf(first);var token2=csrf(second);String body=json.writeValueAsString(payload);
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Callable<Integer>> tasks=new ArrayList<>();
            for(int i=0;i<2;i++) {var cookie=i==0?first:second;var token=i==0?token1:token2;
                tasks.add(()->{ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));
                    var request=method.equals("POST")?post(path):put(path);
                    return mvc.perform(request.cookie(cookie).with(token).contentType("application/json").content(body)).andReturn().getResponse().getStatus();});}
            var f1=pool.submit(tasks.get(0));var f2=pool.submit(tasks.get(1));assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            return List.of(f1.get(30,TimeUnit.SECONDS),f2.get(30,TimeUnit.SECONDS));
        }
    }
    void oneWinner(List<Integer> outcomes,int success) {assertEquals(1,outcomes.stream().filter(i->i==success).count(),outcomes.toString());assertEquals(1,outcomes.stream().filter(i->i==409).count(),outcomes.toString());}

    @Test
    void simultaneousDraftCreationsAndApprovalsNeverProduceDoubleDraftOrApproval() throws Exception {
        var owner=fixture();var first=login(owner);var second=login(owner);var sample=create(first,"FREE");String p=path(sample);
        oneWinner(race(first,second,"POST",p+"/plans",plan(0,"0",false)),201);
        var activity=current(first,p);var draft=activity.get("latestDraftPlan");
        oneWinner(race(first,second,"POST",p+"/plans/"+draft.get("id").asText()+"/approve",Map.of("expectedActivityVersion",activity.get("version").asLong(),"expectedPlanVersion",0)),200);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM sample_cost_plan_versions WHERE activity_id=? AND status='DRAFT'",Integer.class,UUID.fromString(sample.get("id").asText())));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM sample_cost_plan_versions WHERE activity_id=? AND status='APPROVED'",Integer.class,UUID.fromString(sample.get("id").asText())));
        var replacement=command(first,"POST",p+"/plans",plan(current(first,p).get("version").asLong(),"0",false),201);
        oneWinner(race(first,second,"POST",p+"/plans/"+replacement.get("id").asText()+"/approve",Map.of("expectedActivityVersion",current(first,p).get("version").asLong(),"expectedPlanVersion",0)),200);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM sample_cost_plan_versions WHERE activity_id=? AND status='APPROVED'",Integer.class,UUID.fromString(sample.get("id").asText())));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM sample_cost_plan_versions WHERE activity_id=? AND status='SUPERSEDED'",Integer.class,UUID.fromString(sample.get("id").asText())));
    }

    @Test
    void simultaneousActivityAndPlanEditsRejectStaleVersionsWithoutLostUpdates() throws Exception {
        var owner=fixture();var first=login(owner);var second=login(owner);var sample=create(first,"PAID");String p=path(sample);
        var edit=activity("PAID");edit.put("expectedVersion",0);oneWinner(race(first,second,"PUT",p,edit),200);
        assertEquals(1,current(first,p).get("version").asInt());
        var draft=command(first,"POST",p+"/plans",plan(1,"500",false),201);
        oneWinner(race(first,second,"PUT",p+"/plans/"+draft.get("id").asText(),plan(0,"600",true)),200);
        var updated=current(first,p).get("latestDraftPlan");assertEquals(1,updated.get("version").asInt());assertEquals("300.00",updated.get("plannedContribution").asText());
    }
}