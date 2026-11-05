package com.app.sme_health_backend.samples;

import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Combined V33 -> populated V34 -> V35 acceptance on disposable real PostgreSQL. */
class SamplePlanningMigrationPostgreSqlIT extends DisposablePostgres {
    static final List<String> TABLES=List.of("sample_activities","sample_cost_plan_versions","sample_cost_plan_lines");
    static final List<String> COMMERCIAL=List.of("commercial_documents","commercial_document_lines","commercial_number_sequences");

    @Test
    void freshSchemaHasExactCombinedVersionsAndLeastPrivileges() throws Exception {
        try(var connection=POSTGRES.getPostgresDatabase().getConnection();var sql=connection.createStatement()) {
            List<String> versions=new ArrayList<>();
            try(var rows=sql.executeQuery("SELECT version FROM flyway_schema_history WHERE success AND type='SQL' ORDER BY installed_rank")) {while(rows.next())versions.add(rows.getString(1));}
            List<String> expected=new ArrayList<>();for(int i=1;i<=37;i++)expected.add(String.valueOf(i));
            assertEquals(expected,versions);privileges(sql,"public");
        }
    }

    @Test
    void populatedCombinedUpgradePreservesF04F05ActualsAndEnforcesSqlProtections() throws Exception {
        String schema="f09_upgrade_"+UUID.randomUUID().toString().replace("-","");migrate(schema,"33");
        try(var connection=POSTGRES.getPostgresDatabase().getConnection();var sql=connection.createStatement()) {
            sql.execute("SET search_path TO "+schema);
            UUID business=UUID.randomUUID(),foreign=UUID.randomUUID(),actor=UUID.randomUUID(),project=UUID.randomUUID(),foreignProject=UUID.randomUUID();
            sql.execute("INSERT INTO businesses(id) VALUES ('"+business+"'),('"+foreign+"')");
            sql.execute("INSERT INTO app_users(id,email,password_hash,full_name) VALUES ('"+actor+"','f09-migration@example.test','synthetic-only','Migration fixture')");
            sql.execute("INSERT INTO business_profiles(user_id,business_type,language_preference) VALUES ('"+business+"','services','en')");
            sql.execute("INSERT INTO monthly_records(user_id,month,revenue,cogs,cash_balance_eom) VALUES ('"+business+"','2026-10',250000,120000,85000)");
            sql.execute("INSERT INTO score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness,methodology_version) VALUES ('"+business+"','2026-10',74.2,'Healthy','{}','cashflow',1,'health-score-v1')");
            sql.execute("INSERT INTO business_configuration(business_id,operating_profile,requested_modules,version,configured) VALUES ('"+business+"','PROJECT_ORDER','[\"core_finance\",\"projects\"]',2,true)");
            sql.execute("INSERT INTO dashboard_preferences(business_id,user_id,registry_version,version) VALUES ('"+business+"','"+actor+"','f13-v1',1)");
            sql.execute("INSERT INTO projects(id,business_id,code,name,kind,created_by,updated_by) VALUES ('"+project+"','"+business+"','CONTEXT','Context','JOB','"+actor+"','"+actor+"'),('"+foreignProject+"','"+foreign+"','FOREIGN','Foreign','PROJECT','"+actor+"','"+actor+"')");
            UUID budget=UUID.randomUUID();
            sql.execute("INSERT INTO project_budget_versions(id,business_id,project_id,revision,planned_revenue,planned_cost,planned_margin,planned_margin_pct,created_by,updated_by) VALUES ('"+budget+"','"+business+"','"+project+"',1,1000,600,400,0.4,'"+actor+"','"+actor+"')");
            sql.execute("INSERT INTO project_budget_lines(business_id,project_id,budget_version_id,line_number,category,description,quantity,unit,unit_cost,planned_line_cost) VALUES ('"+business+"','"+project+"','"+budget+"',1,'LABOUR','Existing budget',10,'hour',60,600)");
            sql.execute("UPDATE project_budget_versions SET status='APPROVED',approved_by='"+actor+"',approved_at=now(),version=1 WHERE id='"+budget+"'");
            List<String> preserved=new ArrayList<>(List.of("business_profiles","business_configuration","dashboard_preferences","monthly_records","score_results","document_financial_contributions","projects","project_budget_versions","project_budget_lines"));
            var before=snapshot(sql,preserved);migrate(schema,"34");assertEquals(before,snapshot(sql,preserved));
            UUID invoice=seedCommercial(sql,business,actor,"INVOICE",true),bill=seedCommercial(sql,business,actor,"BILL",false);
            preserved.addAll(COMMERCIAL);before=snapshot(sql,preserved);migrate(schema,"35");
            assertEquals(before,snapshot(sql,preserved),"V35 preserves populated V33 and V34 rows");privileges(sql,schema);
            for(String command:List.of("UPDATE commercial_documents SET total=1,subtotal=1,version=version+1 WHERE id='"+invoice+"'",
                    "UPDATE commercial_document_lines SET description='Changed' WHERE document_id='"+invoice+"'",
                    "DELETE FROM commercial_documents WHERE id='"+invoice+"'",
                    "UPDATE commercial_number_sequences SET next_value=1 WHERE business_id='"+business+"' AND kind='INVOICE'",
                    "UPDATE commercial_document_lines SET document_id='"+bill+"' WHERE document_id='"+invoice+"'")) blocked(sql,command);

            UUID activity=UUID.randomUUID(),first=UUID.randomUUID(),line=UUID.randomUUID(),second=UUID.randomUUID();
            sql.execute(activitySql(activity,business,actor,"SAMPLE","FREE",project));
            blocked(sql,activitySql(UUID.randomUUID(),business,actor,"SAMPLE","FREE",foreignProject));
            blocked(sql,activitySql(UUID.randomUUID(),business,actor,"INVENTORY","FREE",null));
            blocked(sql,activitySql(UUID.randomUUID(),business,actor,"SAMPLE","COLLECTED",null));
            blocked(sql,activitySql(UUID.randomUUID(),business,UUID.randomUUID(),"SAMPLE","FREE",null));
            // Header and lines are one transaction because final exact sums are deferred constraints.
            createPlan(sql,first,line,business,activity,actor,1,"FREE","0","-300",null);
            assertEquals("23505",blocked(sql,planSql(UUID.randomUUID(),business,activity,actor,2,"FREE","0","-300",null)).getSQLState());
            blocked(sql,planSql(UUID.randomUUID(),foreign,activity,actor,2,"FREE","0","-300",null));blocked(sql,lineSql(UUID.randomUUID(),foreign,activity,first,2));
            for(String constraint:List.of("fk_sample_activities_project","fk_sample_cost_plan_versions_activity","fk_sample_cost_plan_lines_plan"))
                try(var rows=sql.executeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='"+constraint+"' AND connamespace='"+schema+"'::regnamespace")) {assertTrue(rows.next(),constraint);assertTrue(rows.getString(1).contains("business_id"));}
            for(String command:List.of("UPDATE sample_cost_plan_lines SET quantity=-1 WHERE id='"+line+"'",
                    "UPDATE sample_cost_plan_lines SET planned_line_cost=299 WHERE id='"+line+"'",
                    "UPDATE sample_cost_plan_versions SET planned_contribution=-299,version=version+1 WHERE id='"+first+"'",
                    "UPDATE sample_cost_plan_versions SET planned_recovery=1,planned_contribution=-299,version=version+1 WHERE id='"+first+"'",
                    "UPDATE sample_activities SET recovery_intent='PAID',version=version+1 WHERE id='"+activity+"'"))blocked(sql,command);
            sql.execute("UPDATE sample_cost_plan_lines SET description='Editable draft' WHERE id='"+line+"'");
            sql.execute(approveSql(first,actor));
            UUID secondLine=UUID.randomUUID();createPlan(sql,second,secondLine,business,activity,actor,2,"FREE","0","-300",null);
            assertEquals("23505",blocked(sql,approveSql(second,actor)).getSQLState());
            for(String command:List.of("UPDATE sample_cost_plan_versions SET planned_recovery=100,planned_contribution=-200,version=version+1 WHERE id='"+first+"'",
                    "DELETE FROM sample_cost_plan_versions WHERE id='"+first+"'",
                    "UPDATE sample_cost_plan_lines SET description='Alter history' WHERE id='"+line+"'",
                    "UPDATE sample_cost_plan_lines SET plan_version_id='"+second+"',line_number=2 WHERE id='"+line+"'",
                    "UPDATE sample_cost_plan_lines SET plan_version_id='"+first+"',line_number=2 WHERE id='"+secondLine+"'",
                    "DELETE FROM sample_cost_plan_lines WHERE id='"+line+"'",lineSql(UUID.randomUUID(),business,activity,first,2),
                    "DELETE FROM sample_activities WHERE id='"+activity+"'",
                    "TRUNCATE sample_cost_plan_lines","TRUNCATE sample_cost_plan_versions CASCADE","TRUNCATE sample_activities CASCADE"))blocked(sql,command);
            // An unpaired supersession fails its deferred final-state requirement.
            String supersede="UPDATE sample_cost_plan_versions SET status='SUPERSEDED',superseded_by_plan_id='"+second+"',superseded_at=now(),version=version+1 WHERE id='"+first+"'";
            assertEquals("23514",blocked(sql,supersede).getSQLState());
            connection.setAutoCommit(false);sql.execute(supersede);sql.execute(approveSql(second,actor));connection.commit();connection.setAutoCommit(true);
            blocked(sql,"UPDATE sample_cost_plan_versions SET updated_at=now(),version=version+1 WHERE id='"+first+"'");
            blocked(sql,"UPDATE sample_cost_plan_lines SET plan_version_id='"+second+"',line_number=2 WHERE id='"+line+"'");
            blocked(sql,"UPDATE sample_activities SET status='COMPLETED',version=version+1 WHERE id='"+activity+"'");
            for(String status:List.of("ACTIVE","COMPLETED","ARCHIVED"))sql.execute("UPDATE sample_activities SET status='"+status+"',version=version+1 WHERE id='"+activity+"'");
            blocked(sql,"UPDATE sample_activities SET name='Changed archive',version=version+1 WHERE id='"+activity+"'");
            blocked(sql,"UPDATE sample_activities SET status='ACTIVE',version=version+1 WHERE id='"+activity+"'");
            blocked(sql,planSql(UUID.randomUUID(),business,activity,actor,3,"FREE","0","-300",null));blocked(sql,"DELETE FROM sample_activities WHERE id='"+activity+"'");
            UUID draftActivity=UUID.randomUUID(),draftPlan=UUID.randomUUID();sql.execute(activitySql(draftActivity,business,actor,"PRODUCTION_COST_ACTIVITY","PARTIAL",null));
            // Empty plans cannot commit; valid unknown recovery stays NULL, separately from zero.
            assertEquals("23514",blocked(sql,planSql(draftPlan,business,draftActivity,actor,1,"PARTIAL","NULL","NULL","PLANNED_RECOVERY_UNKNOWN")).getSQLState());
            createPlan(sql,draftPlan,UUID.randomUUID(),business,draftActivity,actor,1,"PARTIAL","NULL","NULL","PLANNED_RECOVERY_UNKNOWN");
            blocked(sql,"UPDATE sample_cost_plan_versions SET planned_contribution=0,version=version+1 WHERE id='"+draftPlan+"'");
            blocked(sql,"UPDATE sample_cost_plan_versions SET planned_cost=299,version=version+1 WHERE id='"+draftPlan+"'");
            for(String status:List.of("CANCELLED","ARCHIVED"))blocked(sql,"UPDATE sample_activities SET status='"+status+"',version=version+1 WHERE id='"+draftActivity+"'");
            assertEquals(1,sql.executeUpdate("DELETE FROM sample_cost_plan_versions WHERE id='"+draftPlan+"'"));
            try(var rows=sql.executeQuery("SELECT count(*) FROM sample_cost_plan_lines WHERE plan_version_id='"+draftPlan+"'")){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
            assertEquals(1,sql.executeUpdate("DELETE FROM sample_activities WHERE id='"+draftActivity+"'"));
            assertEquals(before,snapshot(sql,preserved),"Every F09 action preserves F04, F05 and actual rows");
        } finally {try(var connection=POSTGRES.getPostgresDatabase().getConnection();var sql=connection.createStatement()){sql.execute("DROP SCHEMA "+schema+" CASCADE");}}
    }
    void migrate(String schema,String target){Flyway.configure().dataSource(POSTGRES.getJdbcUrl("postgres","postgres"),"postgres","").schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load().migrate();}
    Map<String,String> snapshot(Statement sql,List<String> tables)throws Exception {Map<String,String> result=new LinkedHashMap<>();for(String table:tables)try(var rows=sql.executeQuery("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM "+table+" t")){assertTrue(rows.next());result.put(table,rows.getString(1));}return result;}
    void privileges(Statement sql,String schema)throws Exception {
        for(String table:TABLES)for(String privilege:List.of("SELECT","INSERT","UPDATE","DELETE","TRUNCATE","TRIGGER","REFERENCES"))try(var rows=sql.executeQuery("SELECT has_table_privilege('finsight_app','"+schema+"."+table+"','"+privilege+"'),has_table_privilege('finsight_backup','"+schema+"."+table+"','"+privilege+"')")){assertTrue(rows.next());assertEquals(List.of("SELECT","INSERT","UPDATE","DELETE").contains(privilege),rows.getBoolean(1),table+privilege);assertEquals(privilege.equals("SELECT"),rows.getBoolean(2),table+privilege);}
    }
    SQLException blocked(Statement sql,String command){return assertThrows(SQLException.class,()->sql.execute(command),command);}
    void createPlan(Statement sql,UUID id,UUID line,UUID business,UUID activity,UUID actor,int revision,String intent,String recovery,String contribution,String reason)throws Exception {
        var connection=sql.getConnection();connection.setAutoCommit(false);
        try{sql.execute(planSql(id,business,activity,actor,revision,intent,recovery,contribution,reason));sql.execute(lineSql(line,business,activity,id,1));connection.commit();}
        catch(Exception ex){connection.rollback();throw ex;}finally{connection.setAutoCommit(true);}
    }
    UUID seedCommercial(Statement sql,UUID business,UUID actor,String kind,boolean finalized)throws Exception {
        UUID document=UUID.randomUUID();sql.execute("INSERT INTO commercial_documents(id,business_id,kind,counterparty_name_ciphertext,document_date,subtotal,total,created_by_user_id,updated_by_user_id) VALUES ('"+document+"','"+business+"','"+kind+"','enc:v1:fixture:QUJDREVGRw==','2026-10-28',100,100,'"+actor+"','"+actor+"')");
        sql.execute("INSERT INTO commercial_document_lines(business_id,document_id,line_number,description,quantity,unit,unit_price,line_amount) VALUES ('"+business+"','"+document+"',1,'Existing commercial fixture',2,'batch',50,100)");
        if(finalized){sql.execute("UPDATE commercial_documents SET status='FINALIZED',document_number='INV-000001',finalized_by_user_id='"+actor+"',finalized_at=now(),version=1 WHERE id='"+document+"'");sql.execute("INSERT INTO commercial_number_sequences(business_id,kind,next_value) VALUES ('"+business+"','INVOICE',2)");}return document;
    }
    String approveSql(UUID plan,UUID actor){return "UPDATE sample_cost_plan_versions SET status='APPROVED',approved_by='"+actor+"',approved_at=now(),version=version+1 WHERE id='"+plan+"'";}
    String activitySql(UUID id,UUID business,UUID actor,String kind,String intent,UUID project){return "INSERT INTO sample_activities(id,business_id,code,name,kind,recovery_intent,project_id,created_by,updated_by) VALUES ('"+id+"','"+business+"','S-"+id.toString().substring(0,8).toUpperCase()+"','Planning fixture','"+kind+"','"+intent+"',"+(project==null?"NULL":"'"+project+"'")+",'"+actor+"','"+actor+"')";}
    String planSql(UUID id,UUID business,UUID activity,UUID actor,int revision,String intent,String recovery,String contribution,String reason){return "INSERT INTO sample_cost_plan_versions(id,business_id,activity_id,revision,recovery_intent,planned_recovery,planned_cost,planned_contribution,contribution_unavailable_reason,created_by,updated_by) VALUES ('"+id+"','"+business+"','"+activity+"',"+revision+",'"+intent+"',"+recovery+",300,"+contribution+","+(reason==null?"NULL":"'"+reason+"'")+",'"+actor+"','"+actor+"')";}
    String lineSql(UUID id,UUID business,UUID activity,UUID plan,int number){return "INSERT INTO sample_cost_plan_lines(id,business_id,activity_id,plan_version_id,line_number,category,description,quantity,unit,unit_cost,planned_line_cost) VALUES ('"+id+"','"+business+"','"+activity+"','"+plan+"',"+number+",'MATERIAL','Cost plan',3,'batch',100,300)";}
}