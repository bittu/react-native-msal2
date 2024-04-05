# react-native-msal2


[![npm latest version](https://img.shields.io/npm/v/react-native-msal2/latest.svg)](https://www.npmjs.com/package/react-native-msal2)
[![npm beta version](https://img.shields.io/npm/v/react-native-msal2/beta.svg)](https://www.npmjs.com/package/react-native-msal2)
[![semantic-release](https://img.shields.io/badge/%20%20%F0%9F%93%A6%F0%9F%9A%80-semantic--release-e10079.svg)](https://github.com/semantic-release/semantic-release)

MSAL React Native wrapper for iOS and Android

## Installation

```
npm i react-native-msal2
```

## Usage

```typescript
import PublicClientApplication from 'react-native-msal2';
import type { MSALConfiguration /*, etc */ } from 'react-native-msal2';

const config: MSALConfiguration = {
  auth: {
    clientId: 'your-client-id',
    // This authority is used as the default in `acquireToken` and `acquireTokenSilent` if not provided to those methods.
    // Defaults to 'https://login.microsoftonline.com/common'
    authority: 'https://<authority url>',
  },
};
const scopes = ['scope1', 'scope2'];

// Initialize the public client application:
const pca = new PublicClientApplication(config);
try {
  await pca.init();
} catch (error) {
  console.error('Error initializing the pca, check your config.', error);
}

// Acquiring a token for the first time, you must call pca.acquireToken
const params: MSALInteractiveParams = { scopes };
const result: MSALResult | undefined = await pca.acquireToken(params);

// On subsequent token acquisitions, you can call `pca.acquireTokenSilent`
// Force the token to refresh with the `forceRefresh` option
const params: MSALSilentParams = {
  account: result!.account, // or get this by filtering the result from `pca.getAccounts` (see below)
  scopes,
  forceRefresh: true,
};
const result: MSALResult | undefined = await pca.acquireTokenSilent(params);

// Get all accounts for which this application has refresh tokens
const accounts: MSALAccount[] = await pca.getAccounts();

// Retrieve the account matching the identifier
const account: MSALAccount | undefined = await pca.getAccount(result!.account.identifier);

// Remove all tokens from the cache for this application for the provided account
const success: boolean = await pca.removeAccount(result!.account);

// Same as `pca.removeAccount` with the exception that, if called on iOS with the `signoutFromBrowser` option set to true, it will additionally remove the account from the system browser
const params: MSALSignoutParams = {
  account: result!.account,
  signoutFromBrowser: true,
};
const success: boolean = await pca.signOut(params);
```

## Development

### Build

Run a single build with `npm run build` and find the output in `/dist`.

### Tests

Tests configured for React Native can be run with `npm test` or `npm run test:watch` in watch mode.

### Preview App

To test your plugin on a device run the following to create a React Native app using it.

```
npm run app
cd app
npm run ios / npm run android
```

The following command will automatically copy over changes made to the plugin to the app.

```
npm run watch
```
