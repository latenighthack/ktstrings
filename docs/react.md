# Generated React packages

Enable `ktstrings.react`, set `packageName` and `packageVersion`, and run `collectKtstringsReact`. Its output is an application-specific ESM package containing locale JSON, eager JavaScript resources, metadata, declarations and runtime helpers. It performs no automatic npm publication.

```tsx
import { messages, registerKtstrings } from '@example/localization';
import { useKtstrings } from '@example/localization/react';
import { createInstance } from 'i18next';
import { I18nextProvider } from 'react-i18next';
import { createRoot } from 'react-dom/client';

const heading = messages.welcome({ name: 'Ada' });
function Header() {
  const { text } = useKtstrings();
  return <h1>{text(heading)}</h1>;
}

async function mount(element: HTMLElement) {
  const applicationI18next = createInstance();
  registerKtstrings(applicationI18next);
  await applicationI18next.init({ lng: 'en', fallbackLng: 'en' });
  createRoot(element).render(
    <I18nextProvider i18n={applicationI18next}><Header /></I18nextProvider>
  );
  // Re-renders Header while retaining the same heading value.
  await applicationI18next.changeLanguage('fr');
}
```

Keep your existing i18next instance and `I18nextProvider`. Registration adds bundled resources; it does not initialize an instance or mutate a global singleton. The package root can be imported without React. The hook uses react-i18next subscriptions for language changes. `resolveText(instance, value, requestedLocale?)` works independently for servers, tests and other presentation code.

Use `useKtstrings('fr-CA')` when a component needs an explicit locale override. For app-wide switching, call `applicationI18next.changeLanguage(locale)` on the provider's instance. Both paths resolve typed message values at presentation time; catalog translations are already bundled and require no network request.

For SSR, register on a per-request instance, initialize the intended locale before rendering, and reuse that locale during hydration. Do not detect a different browser language during initial hydration.

Whole-message availability selects exact, parent, then source language before invoking i18next. Generated lookups isolate interpolation arguments from i18next configuration and disable nesting and recursive interpolation. Render returned strings as ordinary React text nodes. Locale resource JSON uses compiler-controlled interpolation delimiters recorded in the metadata; consumers using resources directly must also apply those delimiters.

Verify a generated package using the committed acceptance catalog:

```sh
node integration/react/verify.mjs /absolute/path/to/generated/react
```

The script packs the npm tarball, installs it into an isolated temporary consumer, compiles positive and negative TypeScript cases, verifies native i18next fallback and literal handling, runs SSR/hydration and language changes, and builds a production esbuild bundle. The acceptance package must be generated from `integration/react/localization`.

The plugin exposes `ktstrings.react.packageDirectory` and `archiveFile` as task-backed output providers. `archiveKtstringsReact` creates a reproducible ZIP of the complete package without requiring npm tooling. To attach it to a Maven publication, apply `maven-publish` and set `react.publicationName` to the publication name. The plugin creates that publication when absent, or adds a `ktstrings-react` ZIP classifier to an existing publication. Publishing automatically generates and collects the package; ordinary builds do not publish it. npm publication remains an explicit consumer operation.
