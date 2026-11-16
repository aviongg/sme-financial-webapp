# FinSight Demo Restore - disabled.
# The previous restore force-dropped and recreated finsight_demo inside whatever container was named
# finsight-postgres-demo, without verifying that it was this checkout's disposable demo container. Demo data is now
# created only once, in a new disposable environment (scripts/init-demo.ps1), and start-demo.ps1 refuses a restored
# database (it lacks the init marker), so restoring into the demo container is not supported.
[Console]::Error.WriteLine("REFUSED: Demo restore is disabled because it replaced the database in an unverified container. To return to " +
    "clean synthetic data: run scripts/stop-demo.ps1, remove the disposable demo environment deliberately (docker compose -p finsight-demo " +
    "-f docker-compose.demo.yml down -v and the .demo-runtime folder, after confirming they hold nothing you need), then run " +
    "scripts/init-demo.ps1 -CreateFreshDisposableDemo. Backups from scripts/backup-demo.ps1 can be inspected with pg_restore elsewhere.")
exit 2
