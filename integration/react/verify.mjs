import { mkdtemp, cp, writeFile, readFile, rm, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { resolve, join } from 'node:path';
import { execFileSync } from 'node:child_process';

const generated = process.argv[2] || process.env.KTSTRINGS_REACT_PACKAGE;
if (!generated) throw new Error('Provide a generated npm package directory: npm test -- /path/to/react');
const temporary = await mkdtemp(join(tmpdir(), 'ktstrings-npm-'));
const run = (command,args,cwd=temporary) => execFileSync(command,args,{cwd,stdio:'inherit'});
try {
  const pack = JSON.parse(execFileSync('npm',['pack','--json','--pack-destination',temporary],{cwd:resolve(generated),encoding:'utf8'}))[0];
  for (const expected of ['index.js','index.d.ts','react.js','react.d.ts','metadata/catalog.json','locales/en.json']) {
    if (!pack.files.some(file => file.path === expected)) throw new Error(`Missing npm asset ${expected}`);
  }
  const manifest = JSON.parse(await readFile(new URL('./package.json',import.meta.url),'utf8'));
  const packageName = JSON.parse(await readFile(join(resolve(generated),'package.json'),'utf8')).name;
  await writeFile(join(temporary,'package.json'),JSON.stringify({...manifest,scripts:{},dependencies:{...manifest.dependencies,[packageName]:`file:./${pack.filename}`}}));
  run('npm',['install','--ignore-scripts','--no-audit','--no-fund']);
  const metadata = JSON.parse(await readFile(join(resolve(generated),'metadata/catalog.json'),'utf8'));
  await writeFile(join(temporary,'contracts.ts'),`
import {messages, literal, resolveText, type UiText} from ${JSON.stringify(packageName)};
import {useKtstrings} from ${JSON.stringify(packageName+'/react')};
const heading: UiText = messages.welcome({name:'Ada'});
messages.itemsCount({count:3});
// @ts-expect-error Missing name
messages.welcome({});
// @ts-expect-error Wrong integer argument
messages.itemsCount({count:'3'});
// @ts-expect-error Extra constructor argument
messages.welcome({name:'Ada',lng:'ar'});
literal(''); useKtstrings; resolveText; heading;
`);
  run(join(temporary,'node_modules/.bin/tsc'),['--noEmit','--strict','--skipLibCheck','--module','NodeNext','--moduleResolution','NodeNext','--target','ES2022','contracts.ts']);
  const fixture = await readFile(new URL('./runtime-fixture.mjs',import.meta.url),'utf8');
  await writeFile(join(temporary,'runtime.mjs'),fixture.replaceAll('__PACKAGE__',packageName));
  run('node',['runtime.mjs']);
  await writeFile(join(temporary,'index.html'),'<div id="root"></div><script type="module" src="/entry.js"></script>');
  await writeFile(join(temporary,'entry.js'),`import React from 'react'; import {createRoot} from 'react-dom/client'; import {I18nextProvider} from 'react-i18next'; import {createInstance} from 'i18next'; import {registerKtstrings,messages} from '${packageName}'; import {useKtstrings} from '${packageName}/react'; const i18n=createInstance(); registerKtstrings(i18n); await i18n.init({lng:'en',initImmediate:false}); function App(){ const {text}=useKtstrings(); return React.createElement('p',null,text(messages.welcome({name:'Ada'}))); } createRoot(document.getElementById('root')).render(React.createElement(I18nextProvider,{i18n},React.createElement(App)));`);
  run(join(temporary,'node_modules/.bin/vite'),['build']);
  console.log('Installed npm tarball, typing, native i18next semantics, SSR/hydration, language switching and production bundle passed.');
} finally { if (!process.env.KTSTRINGS_KEEP_FIXTURE) await rm(temporary,{recursive:true,force:true}); else console.log(temporary); }
