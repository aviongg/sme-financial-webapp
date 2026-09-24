import psycopg2

dba_pw = "FinSight_Dba_Admin_Sec_2026_!#9xK"
mig_pw = "FinSight_Migrator_Ddl_2026_!$4mP"
app_pw = "FinSight_App_Runtime_2026_!*7vQ"

print("--- Testing pg_hba.conf enforcement ---")

# Test 1: Plaintext rejection (hostnossl ... reject)
try:
    conn = psycopg2.connect(dbname='sme_health', user='finsight_app', password=app_pw, host='localhost', port=5432, sslmode='disable')
    print("FAILED: Plaintext connection should have been rejected!")
except Exception as e:
    print("PASS 1: Plaintext connection rejected as expected:", str(e).strip()[:120])

# Test 2: Network DBA rejection (finsight_dba has NO host rule, only local)
try:
    conn = psycopg2.connect(dbname='sme_health', user='finsight_dba', password=dba_pw, host='localhost', port=5432, sslmode='require')
    print("FAILED: finsight_dba should NOT be allowed over network!")
except Exception as e:
    print("PASS 2: finsight_dba over network rejected as expected:", str(e).strip()[:120])

# Test 3: finsight_app with TLS verify-full
try:
    conn = psycopg2.connect(dbname='sme_health', user='finsight_app', password=app_pw, host='localhost', port=5432, sslmode='verify-full', sslrootcert='certs/postgres-ca.crt')
    cur = conn.cursor()
    cur.execute("SELECT current_user")
    print("PASS 3: finsight_app connected via TLS verify-full, user:", cur.fetchone()[0])
    conn.close()
except Exception as e:
    print("FAIL 3:", e)

# Test 4: finsight_migrator with TLS verify-full
try:
    conn = psycopg2.connect(dbname='sme_health', user='finsight_migrator', password=mig_pw, host='localhost', port=5432, sslmode='verify-full', sslrootcert='certs/postgres-ca.crt')
    cur = conn.cursor()
    cur.execute("SELECT current_user")
    print("PASS 4: finsight_migrator connected via TLS verify-full, user:", cur.fetchone()[0])
    conn.close()
except Exception as e:
    print("FAIL 4:", e)

# Test 5: Untrusted CA rejection with verify-full
try:
    conn = psycopg2.connect(dbname='sme_health', user='finsight_app', password=app_pw, host='localhost', port=5432, sslmode='verify-full', sslrootcert='certs/test-fixtures/rogue-ca.crt')
    print("FAILED: Untrusted CA should have been rejected!")
except Exception as e:
    print("PASS 5: Untrusted CA rejected as expected:", str(e).strip()[:120])

# Test 6: Hostname mismatch rejection with verify-full
try:
    conn = psycopg2.connect(dbname='sme_health', user='finsight_app', password=app_pw, host='127.0.0.2', port=5432, sslmode='verify-full', sslrootcert='certs/postgres-ca.crt')
    print("FAILED: Hostname mismatch should have been rejected!")
except Exception as e:
    print("PASS 6: Hostname mismatch rejected as expected:", str(e).strip()[:120])

print("--- All pg_hba.conf and TLS tests passed ---")
