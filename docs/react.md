# Generated React packages

Enable `ktstrings.react`, set `packageName` and `packageVersion`, and run `collectKtstringsReact`. Its output is an application-specific ESM package containing locale JSON, eager JavaScript resources, metadata, declarations and runtime helpers. It performs no automatic npm publication.

```tsx
import { messages, registerKtstrings } from '@example/localization';
import { useKtstrings } from '@example/localization/react';

registerKtstrings(applicationI18next);
const heading = messages.welcome({ name: 'Ada' });
function Header() {
  const { text } = useKtstrings();
  return <h1>{text(heading)}</h1>;
}
```

Keep your existing i18next instance and `I18nextProvider`. Registration adds bundled resources; it does not initialize an instance or mutate a global singleton. The package root can be imported without React. The hook uses react-i18next subscriptions for language changes. `resolveText(instance, value, requestedLocale?)` works independently for servers, tests and other presentation code.

For SSR, register on a per-request instance, initialize the intended locale before rendering, and reuse that locale during hydration. Do not detect a different browser language during initial hydration.

Whole-message availability selects exact, parent, then source language before invoking i18next. Generated lookups isolate interpolation arguments from i18next configuration and disable nesting and recursive interpolation. Render returned strings as ordinary React text nodes. Locale resource JSON uses compiler-controlled interpolation delimiters recorded in the metadata; consumers using resources directly must also apply those delimiters.

Verify a generated package using the committed acceptance catalog:

```sh
node integration/react/verify.mjs /absolute/path/to/generated/react
```

The script packs the npm tarball, installs it into an isolated temporary consumer, compiles positive and negative TypeScript cases, verifies native i18next fallback and literal handling, runs SSR/hydration and language changes, and builds a production esbuild bundle. The acceptance package must be generated from `integration/react/localization`.
