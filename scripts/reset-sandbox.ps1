# FinSight Sandbox Reset - disabled.
# The previous reset rewrote existing users and bypassed immutable audit triggers (session_replication_role = replica).
# Demo data is now created only once, in a new disposable environment, so nothing existing is ever modified.
[Console]::Error.WriteLine("REFUSED: Sandbox reset is disabled because it modified existing data. To return to clean synthetic data: " +
    "run scripts/stop-demo.ps1, remove the disposable demo environment (docker compose -p finsight-demo -f docker-compose.demo.yml down -v " +
    "and the .demo-runtime folder, after confirming they hold nothing you need), then run scripts/init-demo.ps1 -CreateFreshDisposableDemo.")
exit 2
