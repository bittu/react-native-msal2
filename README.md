# react-native-msal2

[![npm latest version](https://img.shields.io/npm/v/react-native-msal2/latest.svg)](https://www.npmjs.com/package/react-native-msal2)
[![semantic-release](https://img.shields.io/badge/%20%20%F0%9F%93%A6%F0%9F%9A%80-semantic--release-e10079.svg)](https://github.com/semantic-release/semantic-release)

A React Native wrapper around Microsoft Authentication Library (MSAL) for iOS and Android. Enables authentication with Microsoft identity platform (Azure AD, Azure AD B2C, Microsoft personal accounts) in your React Native apps.

## Table of Contents

- [Features](#features)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Platform Setup](#platform-setup)
  - [iOS Setup](#ios-setup)
  - [Android Setup](#android-setup)
- [Usage](#usage)
  - [Configuration](#configuration)
  - [Initialization](#initialization)
  - [Acquire Token Interactively](#acquire-token-interactively)
  - [Acquire Token Silently](#acquire-token-silently)
  - [Get Accounts](#get-accounts)
  - [Remove Account / Sign Out](#remove-account--sign-out)
- [API Reference](#api-reference)
  - [PublicClientApplication](#publicclientapplication)
  - [Types](#types)
- [B2C Example](#b2c-example)
- [Troubleshooting](#troubleshooting)
- [Development](#development)
- [License](#license)

## Features

- Interactive and silent token acquisition
- Azure AD and Azure AD B2C support
- Multiple account management
- Customizable webview parameters (iOS)
- Android Custom Tabs browser configuration
- TypeScript support out of the box

## Prerequisites

- React Native >= 0.70
- iOS >= 12.0
- Android minSdkVersion >= 21
- An app registered in the [Azure Portal](https://portal.azure.com/#blade/Microsoft_AAD_RegisteredApps/ApplicationsListBlade)

## Installation

```bash
npm install react-native-msal2
```

### iOS

```bash
cd ios && pod install
```

### Android

No additional install steps required — autolinking handles it.

## Platform Setup

### iOS Setup

#### 1. Register a Redirect URI

In the Azure Portal, add a redirect URI for iOS:

```
msauth.<your.bundle.id>://auth
```

#### 2. Configure URL Scheme

Add the following to your `Info.plist`:

```xml
<key>CFBundleURLTypes</key>
<array>
  <dict>
    <key>CFBundleURLSchemes</key>
    <array>
      <string>msauth.$(PRODUCT_BUNDLE_IDENTIFIER)</string>
    </array>
  </dict>
</array>
```

#### 3. Handle Auth Redirects

In your `AppDelegate.m` (or `AppDelegate.mm`), add:

```objc
#import <MSAL/MSAL.h>

- (BOOL)application:(UIApplication *)app openURL:(NSURL *)url options:(NSDictionary<UIApplicationOpenURLOptionsKey,id> *)options
{
  return [MSALPublicClientApplication handleMSALResponse:url sourceApplication:options[UIApplicationOpenURLOptionsSourceApplicationKey]];
}
```

#### 4. Keychain Sharing (Optional)

If you need keychain sharing, add the `Keychain Sharing` capability in Xcode and add your bundle identifier as a keychain group.

### Android Setup

#### 1. Register a Redirect URI

The library automatically generates a redirect URI in the format:

```
msauth://<your.package.name>/<base64-encoded-signature-hash>
```

You can also provide a custom `redirectUri` in the config. Register whichever URI you use in the Azure Portal.

#### 2. Configure BrowserTabActivity

Add the following activity to your `AndroidManifest.xml` inside the `<application>` tag:

```xml
<activity android:name="com.microsoft.identity.client.BrowserTabActivity">
  <intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
      android:scheme="msauth"
      android:host="<your.package.name>"
      android:path="/<url-encoded-signature-hash>" />
  </intent-filter>
</activity>
```

#### 3. Get Your Signature Hash

To find your signature hash for the redirect URI:

```bash
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore | openssl sha1 -binary | openssl base64
```

Default debug keystore password is `android`.

## Usage

### Configuration

```typescript
import PublicClientApplication from 'react-native-msal2';
import type { MSALConfiguration } from 'react-native-msal2';
import { Platform } from 'react-native';

const config: MSALConfiguration = {
  auth: {
    clientId: '<your-client-id>',
    // Defaults to 'https://login.microsoftonline.com/common'
    authority: 'https://login.microsoftonline.com/<tenant-id>',
    knownAuthorities: ['https://login.microsoftonline.com/<tenant-id>'],
    redirectUri: Platform.select({
      ios: 'msauth.<your.bundle.id>://auth',
      android: 'msauth://<your.package.name>/<signature-hash>',
    }),
  },
  // Android-specific options (optional)
  androidConfigOptions: {
    authorization_user_agent: 'DEFAULT',
    broker_redirect_uri_registered: false,
    logging: {
      pii_enabled: false,
      log_level: 'ERROR',
      logcat_enabled: true,
    },
  },
};
```

### Initialization

You must call `init()` before using any other method:

```typescript
const pca = new PublicClientApplication(config);

try {
  await pca.init();
} catch (error) {
  console.error('Error initializing MSAL:', error);
}
```

### Acquire Token Interactively

Use this for the first-time login or when a silent token acquisition fails:

```typescript
import type { MSALInteractiveParams, MSALResult } from 'react-native-msal2';

const params: MSALInteractiveParams = {
  scopes: ['User.Read'],
  promptType: MSALPromptType.SELECT_ACCOUNT,
  loginHint: '<email>',
};

const result: MSALResult | undefined = await pca.acquireToken(params);
console.log('Access token:', result?.accessToken);
```

### Acquire Token Silently

Use this for subsequent token acquisitions using a cached account:

```typescript
import type { MSALSilentParams } from 'react-native-msal2';

const params: MSALSilentParams = {
  scopes: ['User.Read'],
  account: result!.account,
  forceRefresh: false,
};

const silentResult = await pca.acquireTokenSilent(params);
```

### Get Accounts

```typescript
// Get all accounts with cached refresh tokens
const accounts = await pca.getAccounts();

// Get a specific account by identifier
const account = await pca.getAccount(accountIdentifier);
```

### Remove Account / Sign Out

```typescript
// Remove account from cache (works on both platforms)
await pca.removeAccount(account);

// Sign out with browser session cleanup (iOS only — falls back to removeAccount on Android)
import type { MSALSignoutParams } from 'react-native-msal2';

await pca.signOut({
  account,
  signoutFromBrowser: true,
});
```

## API Reference

### PublicClientApplication

| Method | Returns | Description |
|---|---|---|
| `init()` | `Promise<this>` | Initializes the native MSAL client. Must be called first. |
| `acquireToken(params)` | `Promise<MSALResult \| undefined>` | Acquires a token interactively via a webview/browser. |
| `acquireTokenSilent(params)` | `Promise<MSALResult \| undefined>` | Acquires a token silently from cache or by refreshing. |
| `getAccounts()` | `Promise<MSALAccount[]>` | Returns all accounts with cached refresh tokens. |
| `getAccount(identifier)` | `Promise<MSALAccount \| undefined>` | Returns the account matching the given identifier. |
| `removeAccount(account)` | `Promise<boolean>` | Removes all cached tokens for the given account. |
| `signOut(params)` | `Promise<boolean>` | Removes cached tokens and optionally signs out from the browser (iOS). |
| `getSelectedBrowser()` | `Promise<string>` | Returns the browser used for auth. Android only (returns `'N/A'` on iOS). |
| `getSafeCustomTabsBrowsers()` | `Promise<MSALAndroidPreferredBrowser[]>` | Returns installed browsers supporting Custom Tabs. Android only. |

### Types

#### MSALConfiguration

```typescript
interface MSALConfiguration {
  auth: {
    clientId: string;
    authority?: string;           // Default: 'https://login.microsoftonline.com/common'
    knownAuthorities?: string[];
    redirectUri?: string;         // Platform-specific, auto-generated on Android if omitted
  };
  androidConfigOptions?: MSALAndroidConfigOptions;
}
```

#### MSALInteractiveParams

```typescript
interface MSALInteractiveParams {
  scopes: string[];
  authority?: string;
  promptType?: MSALPromptType;
  loginHint?: string;
  extraQueryParameters?: Record<string, string>;
  extraScopesToConsent?: string[];
  webviewParameters?: MSALWebviewParams;
}
```

#### MSALSilentParams

```typescript
interface MSALSilentParams {
  scopes: string[];
  account: MSALAccount;
  authority?: string;
  forceRefresh?: boolean;
}
```

#### MSALSignoutParams

```typescript
interface MSALSignoutParams {
  account: MSALAccount;
  signoutFromBrowser?: boolean;   // iOS only, default: false
  webviewParameters?: MSALWebviewParams;
}
```

#### MSALResult

```typescript
interface MSALResult {
  accessToken: string;
  account: MSALAccount;
  expiresOn: number;              // Unix timestamp (seconds)
  idToken?: string;
  scopes: string[];
  tenantId?: string;
}
```

#### MSALAccount

```typescript
interface MSALAccount {
  identifier: string;
  environment?: string;
  tenantId: string;
  username: string;
  claims?: object;
}
```

#### MSALPromptType

```typescript
enum MSALPromptType {
  SELECT_ACCOUNT = 0,
  LOGIN = 1,
  CONSENT = 2,
  WHEN_REQUIRED = 3,
  DEFAULT = WHEN_REQUIRED,
}
```

#### MSALWebviewParams (iOS)

```typescript
interface MSALWebviewParams {
  ios_prefersEphemeralWebBrowserSession?: boolean;  // iOS 13+
  ios_webviewType?: Ios_MSALWebviewType;
  ios_presentationStyle?: Ios_ModalPresentationStyle;
}
```

#### MSALAndroidConfigOptions

```typescript
interface MSALAndroidConfigOptions {
  authorization_user_agent?: 'DEFAULT' | 'BROWSER' | 'WEBVIEW';
  broker_redirect_uri_registered?: boolean;
  preferred_browser?: MSALAndroidPreferredBrowser;
  browser_safelist?: {
    browser_package_name: string;
    browser_signature_hashes: string[];
    browser_use_customTab: boolean;
  }[];
  http?: { connect_timeout?: number; read_timeout?: number };
  logging?: {
    pii_enabled?: boolean;
    log_level?: 'ERROR' | 'WARNING' | 'INFO' | 'VERBOSE';
    logcat_enabled?: boolean;
  };
  multiple_clouds_supported?: boolean;
}
```

## B2C Example

```typescript
import PublicClientApplication, {
  MSALConfiguration,
  MSALInteractiveParams,
} from 'react-native-msal2';

const b2cConfig: MSALConfiguration = {
  auth: {
    clientId: '<your-client-id>',
    authority: 'https://<tenant>.b2clogin.com/tfp/<tenant>.onmicrosoft.com/<sign-in-policy>',
    knownAuthorities: ['https://<tenant>.b2clogin.com'],
  },
};

const pca = new PublicClientApplication(b2cConfig);
await pca.init();

const result = await pca.acquireToken({
  scopes: ['https://<tenant>.onmicrosoft.com/<api-id>/access_as_user'],
});
```

## Troubleshooting

- **"PublicClientApplication is not initialized"** — Ensure you call `await pca.init()` before any other method.
- **iOS redirect issues** — Verify your URL scheme in `Info.plist` matches the redirect URI registered in Azure Portal, and that `AppDelegate` handles the MSAL response.
- **Android signature hash mismatch** — Regenerate your signature hash and ensure it matches the redirect URI in Azure Portal. Debug and release builds use different keystores.
- **B2C authority not recognized** — Make sure the authority URL follows the pattern `https://<tenant>.b2clogin.com/tfp/<tenant>.onmicrosoft.com/<policy>` and is included in `knownAuthorities`.
- **Silent token acquisition fails** — The refresh token may have expired. Fall back to `acquireToken` (interactive) and catch the error from `acquireTokenSilent`.

## Development

### Build

```bash
npm run build
```

Output is in `/dist`.

### Tests

```bash
npm test
npm run test:watch   # watch mode
```

### Preview App

Create a test app and run it on a device:

```bash
npm run app
cd app
npm run ios    # or npm run android
```

Auto-copy plugin changes to the app:

```bash
npm run watch
```

## License

MIT
