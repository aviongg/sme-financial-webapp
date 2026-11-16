// Reproduces QA without modifying candidate application/test source. A generated
// runner delegates to the frozen real product harness and adds independent checks.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { execFileSync, spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';

const baseline='71ac4e0e7e74cbfe2f08fe3a049d37cd26b0c7c3';
const root=fileURLToPath(new URL('../../../',import.meta.url));
const git=(...args)=>execFileSync('git',args,{cwd:root,encoding:'utf8'}).trim();
for(const tree of ['frontend/src','backend/src/main','frontend/tests','backend/src/test','scripts']){
  assert.equal(git('rev-parse',`HEAD:${tree}`),git('rev-parse',`${baseline}:${tree}`),`${tree} differs from frozen candidate`);
}
git('diff','--exit-code',baseline,'--','frontend','backend','scripts','docker','database');
const temp=path.join(root,'tmp');mkdirSync(temp,{recursive:true});
const stamp=Date.now().toString();
let source=readFileSync(path.join(root,'frontend/tests/product-completion-browser.integration.mjs'),'utf8');
source=source.replace("const frontendDir = path.resolve(fileURLToPath(new URL('..', import.meta.url)));",`const frontendDir = ${JSON.stringify(path.join(root,'frontend'))};`);
source=source.replace('createRequire(import.meta.url)',"createRequire(new URL('../frontend/package.json',import.meta.url))");
source=source.replace("await import('./candidate-v2-journeys.mjs')",`await import(${JSON.stringify(pathToFileURL(path.join(root,'frontend/tests/candidate-v2-journeys.mjs')).href)})`);
source=source.replace('import { fileURLToPath }','import { fileURLToPath, pathToFileURL }');
source=source.replace('    System.out.println("PRODUCT_RUNTIME_READY");',`    try (var c = pg.getPostgresDatabase().getConnection(); var q = c.createStatement(); var rs = q.executeQuery("SELECT version()")) { rs.next(); System.out.println("QA_POSTGRES_VERSION=" + rs.getString(1)); }
    System.out.println("PRODUCT_RUNTIME_READY");`);
// Each invocation gets a new task-owned scratch directory; no prior worktree's
// isolated frontend, database, document directory or diagnostic files are reused.
source=source.replace("const outDir = path.join(frontendDir, 'test-artifacts', smoke ? 'release-smoke' : 'product-completion-browser');",`const outDir = path.join(frontendDir,'test-artifacts','independent-qa-${stamp}');`);
const hook=`
  writeFileSync(path.join(outDir,'baseline-results.json'),JSON.stringify({testedSHA:'${baseline}',passed:checks.length,checks},null,2));
  const {independentQA}=await import(${JSON.stringify(new URL('./independent-journeys.mjs',import.meta.url).href)});
  await independentQA({origin,browser,call,csrf,cookies,mark,itemId:item.json.id,invoiceId,accountId:account.json.id,businessId:biz.json.businessId,today,outDir});
`;
source=source.replace("  writeFileSync(path.join(outDir, 'results.json')",hook+"  writeFileSync(path.join(outDir, 'results.json')");
const script=path.join(temp,`qa-evidence-runner-${stamp}.mjs`);writeFileSync(script,source);
console.log(`Frozen application ${baseline}; evidence frontend/test-artifacts/independent-qa-${stamp}`);
const child=spawn(process.execPath,[script,...(process.argv.includes('--smoke')?['--smoke']:['--candidate-v2'])],{
  cwd:root,stdio:'inherit',windowsHide:true,
  env:{...process.env,FINSIGHT_BROWSER_TMP_DIR:path.join(temp,`qa-runtime-${stamp}`)},
});
const [code]=await once(child,'exit');process.exitCode=code??1;
