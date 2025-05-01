#import "RNMSAL.h"
#import "React/RCTConvert.h"
#import "React/RCTLog.h"
#import <MSAL/MSAL.h>

#import "UIViewController+RNMSALUtils.h"

@implementation RNMSAL

RCT_EXPORT_MODULE()

MSALPublicClientApplication *application;

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

RCT_REMAP_METHOD(createPublicClientApplication,
                 config:(NSDictionary*)config
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        NSError *msalError = nil;

        // Required
        NSDictionary* auth = [RCTConvert NSDictionary:config[@"auth"]];
        NSString* clientId = [RCTConvert NSString:auth[@"clientId"]];

        // Optional
        NSString* authority = [RCTConvert NSString:auth[@"authority"]];
        NSArray<NSString*> * knownAuthorities = [RCTConvert NSStringArray:auth[@"knownAuthorities"]];
        NSString* redirectUri = [RCTConvert NSString:auth[@"redirectUri"]];

        MSALPublicClientApplicationConfig *applicationConfig = [[MSALPublicClientApplicationConfig alloc] initWithClientId:clientId];
        if (authority) {
            MSALB2CAuthority *msalAuthority = [[MSALB2CAuthority alloc] initWithURL:[NSURL URLWithString:authority] error:&msalError];
            if (msalError) {
                @throw(msalError);
            }
            applicationConfig.authority = msalAuthority;
        }

        if (knownAuthorities) {
            NSMutableArray<MSALB2CAuthority*> * msalKnownAuthorities = [NSMutableArray arrayWithCapacity:1];
            for (NSString *authorityString in knownAuthorities) {
                MSALB2CAuthority *a = [[MSALB2CAuthority alloc] initWithURL:[NSURL URLWithString:authorityString] error:&msalError];
                if (msalError) {
                    @throw(msalError);
                }
                [msalKnownAuthorities addObject:a];
            }
            applicationConfig.knownAuthorities = msalKnownAuthorities;
        }

        if (redirectUri) {
            applicationConfig.redirectUri = redirectUri;
        }

        //
        NSBundle *mainBundle = [NSBundle mainBundle];
        applicationConfig.cacheConfig.keychainSharingGroup = mainBundle.bundleIdentifier;
        application = [[MSALPublicClientApplication alloc] initWithConfiguration:applicationConfig error:&msalError];

        if (msalError) {
            @throw(msalError);
        }

        resolve(nil);
    } @catch (NSError *error) {
        reject([[NSString alloc] initWithFormat:@"%d", (int)error.code], error.description, error);
    }
}

RCT_REMAP_METHOD(acquireToken,
                 interactiveParams:(NSDictionary*)params
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        // Required parameters
        NSArray<NSString *> *scopes = [RCTConvert NSStringArray:params[@"scopes"]];

        // Optional parameters
        NSString *authority = [RCTConvert NSString:params[@"authority"]];
        NSUInteger promptType = [RCTConvert NSUInteger:params[@"promptType"]];
        NSString *loginHint = [RCTConvert NSString:params[@"loginHint"]];
        NSDictionary<NSString *,NSString *> *extraQueryParameters = [RCTConvert NSDictionary:params[@"extraQueryParameters"]];
        NSArray<NSString *> *extraScopesToConsent = [RCTConvert NSStringArray:params[@"extraScopesToConsent"]];
        NSDictionary * webviewParameters = [RCTConvert NSDictionary:params[@"webviewParameters"]];
        NSUInteger webviewType = [RCTConvert NSUInteger:webviewParameters[@"ios_webviewType"]];
        NSInteger presentationStyle = [RCTConvert NSInteger:webviewParameters[@"ios_presentationStyle"]];
        BOOL prefersEphemeralWebBrowserSession = [RCTConvert BOOL:webviewParameters[@"ios_prefersEphemeralWebBrowserSession"]];

        // Configure interactive token parameters
        UIViewController *viewController = [UIViewController currentViewController];
        MSALWebviewParameters *webParameters = [[MSALWebviewParameters alloc] initWithAuthPresentationViewController:viewController];
        webParameters.webviewType = webviewType;
        webParameters.presentationStyle = presentationStyle;
        if (@available(iOS 13.0, *)) {
            webParameters.prefersEphemeralWebBrowserSession = prefersEphemeralWebBrowserSession;
        }

        MSALInteractiveTokenParameters *interactiveParams = [[MSALInteractiveTokenParameters alloc] initWithScopes:scopes webviewParameters:webParameters];
        interactiveParams.promptType = promptType;
        interactiveParams.loginHint = loginHint;
        interactiveParams.extraQueryParameters = extraQueryParameters;
        interactiveParams.extraScopesToConsent = extraScopesToConsent;
        if (authority) {
            interactiveParams.authority = [[MSALB2CAuthority alloc] initWithURL:[NSURL URLWithString:authority] error:nil];
        }

        // Send request
        [application acquireTokenWithParameters:interactiveParams completionBlock:^(MSALResult * _Nullable result, NSError * _Nullable error) {
            if (error) {
                reject([[NSString alloc] initWithFormat:@"%d", (int)error.code], error.description, error);
            } else if (result) {
                resolve([self MSALResultToDictionary:result withAuthority:authority]);
            } else {
                resolve(nil);
            }
        }];
    } @catch (NSError *error) {
        reject([[NSString alloc] initWithFormat:@"%d", (int)error.code], error.description, error);
    }
}

RCT_REMAP_METHOD(acquireTokenSilent,
                 silentParams:(NSDictionary*)params
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        NSError *msalError = nil;

        // Required parameters
        NSArray<NSString *> *scopes = [RCTConvert NSStringArray:params[@"scopes"]];
        NSDictionary * accountIn = [RCTConvert NSDictionary:params[@"account"]];
        NSString *accountIdentifier = [RCTConvert NSString:accountIn[@"identifier"]];

        // Optional parameters
        NSString *authority = [RCTConvert NSString:params[@"authority"]];
        BOOL forceRefresh = [RCTConvert BOOL:params[@"forceRefresh"]];

        MSALAccount *account = [application accountForIdentifier:accountIdentifier error:&msalError];

        if (msalError) {
            @throw(msalError);
        }

        // Configure interactive token parameters
        MSALSilentTokenParameters *silentParams = [[MSALSilentTokenParameters alloc] initWithScopes:scopes account:account];
        silentParams.forceRefresh = forceRefresh;
        if (authority) {
            silentParams.authority = [[MSALB2CAuthority alloc] initWithURL:[NSURL URLWithString:authority] error:nil];
        }

        // Send request
        [application acquireTokenSilentWithParameters:silentParams completionBlock:^(MSALResult * _Nullable result, NSError * _Nullable error) {
            if (error) {
                reject([[NSString alloc] initWithFormat:@"%d", (int)error.code], error.description, error);
            } else if (result) {
                resolve([self MSALResultToDictionary:result withAuthority:authority]);
            } else {
                resolve(nil);
            }
        }];
    } @catch (NSError *error) {
        reject([[NSString alloc] initWithFormat:@"%d", (int)error.code], error.description, error);
    }
}

RCT_REMAP_METHOD(getAccounts,
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        NSError *msalError = nil;
        NSArray *_accounts = [application allAccounts:&msalError];

        if (msalError) {
            @throw msalError;
        }

        NSMutableArray *accounts = [NSMutableArray arrayWithCapacity:1];
        if (_accounts) {
            for (MSALAccount *account in _accounts) {
                [accounts addObject:[self MSALAccountToDictionary:account]];
            }
        }
        resolve(accounts);
    } @catch (NSError* error) {
        reject([[NSString alloc] initWithFormat:@"%d", (int)error.code], error.description, error);
    }
}

RCT_REMAP_METHOD(getAccount,
                 accoundIdentifier:(NSString*)accountIdentifier
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        NSError *msalError = nil;
        MSALAccount *account = [application accountForIdentifier:accountIdentifier error:&msalError];

        if (msalError) {
            @throw msalError;
        }

        if (account) {
            resolve([self MSALAccountToDictionary:account]);
        } else {
            resolve(nil);
        }
    } @catch(NSError *error) {
        reject([[NSString alloc] initWithFormat:@"%d", (int)error.code], error.description, error);
    }
}

RCT_REMAP_METHOD(removeAccount,
                 account:(NSDictionary*)account
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        NSError *msalError = nil;

        // Required parameters
        NSString *accountIdentifier = [RCTConvert NSString:account[@"identifier"]];

        MSALAccount *account = [application accountForIdentifier:accountIdentifier error:&msalError];

        if (msalError) {
            @throw msalError;
        }

        BOOL res = [application removeAccount:account error:&msalError];

        if (msalError) {
            @throw msalError;
        }

        resolve(res ? @YES : @NO);

    } @catch(NSError *error) {
        reject([[NSString alloc] initWithFormat:@"%d", (int)error.code], error.description, error);
    }
}

RCT_REMAP_METHOD(signout,
                 signoutParams:(NSDictionary*)params
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        NSError *msalError = nil;

        // Required parameters
        NSDictionary * accountIn = [RCTConvert NSDictionary:params[@"account"]];
        NSString *accountIdentifier = [RCTConvert NSString:accountIn[@"identifier"]];

        // Optional parameters
        BOOL signoutFromBrowser = [RCTConvert BOOL:params[@"signoutFromBrowser"]];
        NSDictionary * webviewParameters = [RCTConvert NSDictionary:params[@"webviewParameters"]];
        BOOL prefersEphemeralWebBrowserSession = [RCTConvert BOOL:webviewParameters[@"ios_prefersEphemeralWebBrowserSession"]];

        MSALAccount *account = [application accountForIdentifier:accountIdentifier error:&msalError];

        if (msalError) {
            @throw msalError;
        }

        UIViewController *viewController = [UIViewController currentViewController];
        MSALWebviewParameters *webParameters = [[MSALWebviewParameters alloc] initWithAuthPresentationViewController:viewController];
        if (@available(iOS 13.0, *)) {
            webParameters.prefersEphemeralWebBrowserSession = prefersEphemeralWebBrowserSession;
        }

        MSALSignoutParameters *signoutParameters = [[MSALSignoutParameters alloc] initWithWebviewParameters:webParameters];
        signoutParameters.signoutFromBrowser = signoutFromBrowser;

        [application signoutWithAccount:account signoutParameters:signoutParameters completionBlock:^(BOOL success, NSError * _Nullable error) {
            if (error) {
                reject([[NSString alloc] initWithFormat:@"%d", (int)error.code], error.description, error);
            } else {
                resolve(success ? @YES : @NO);
            }
        }];

    } @catch(NSError *error) {
        reject([[NSString alloc] initWithFormat:@"%d", (int)error.code], error.description, error);
    }
}

- (NSDictionary*)MSALResultToDictionary:(nonnull MSALResult*)result withAuthority:(NSString*)authority
{
    NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithCapacity:1];

    [dict setObject:result.accessToken forKey:@"accessToken"];
    [dict setObject:[NSNumber numberWithDouble:[result.expiresOn timeIntervalSince1970]] forKey:@"expiresOn"];
    [dict setObject:(result.idToken ?: [NSNull null]) forKey:@"idToken"];
    [dict setObject:result.scopes forKey:@"scopes"];
    [dict setObject:(authority ?: application.configuration.authority.url.absoluteString) forKey:@"authority"];
    [dict setObject:(result.tenantProfile.tenantId ?: [NSNull null]) forKey:@"tenantId"];
    [dict setObject:[self MSALAccountToDictionary:result.account] forKey:@"account"];

    // Unpack payload from JWT to get email user id when accountClaims is null
    if (!result.account.accountClaims || result.account.accountClaims == nil || result.account.accountClaims == NULL) {
        NSLog(@"RNMSAL accountClaims is null, decoding it manually from jwt");

        NSString *jwt = result.accessToken;
        NSDictionary<NSString *, id> *claimsDict = [self decodeJWTPayload:jwt];

        NSMutableDictionary *account = dict[@"account"];
        account[@"claims"] = claimsDict;
    }

    return [dict mutableCopy];
}

- (NSDictionary*)MSALAccountToDictionary:(nonnull MSALAccount*)account
{
    NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithCapacity:1];
    [dict setObject:account.identifier forKey:@"identifier"];
    [dict setObject:(account.username ?: [NSNull null]) forKey:@"username"];
    [dict setObject:account.environment forKey:@"environment"];
    [dict setObject:(account.accountClaims ?: [NSNull null]) forKey:@"claims"];
    [dict setObject:account.homeAccountId.tenantId forKey:@"tenantId"];
    return [dict mutableCopy];
}

/**
 * Call to decode a Base64 encoded string
 */
- (NSString *)decodeBase64StringWithPadding:(NSString *)encodedString {
    NSString *stringTobeEncoded = [encodedString stringByReplacingOccurrencesOfString:@"-" withString:@"+"];
    stringTobeEncoded = [stringTobeEncoded stringByReplacingOccurrencesOfString:@"_" withString:@"/"];

    NSInteger paddingCount = encodedString.length % 4;
    for (NSInteger i = 0; i < paddingCount; i++) {
        stringTobeEncoded = [stringTobeEncoded stringByAppendingString:@"="];
    }

    return stringTobeEncoded;
}

/**
 * Util function for decoding the payload pat of JWT access token
 */
- (NSDictionary<NSString *, id> *)decodeJWTPayload:(NSString *)jwt {
    NSArray<NSString *> *parts = [jwt componentsSeparatedByString:@"."];
    if (parts.count != 3) {
        NSLog(@"%@ %@ called - jwt not valid, parts count: %lu", @(__FILE__), NSStringFromSelector(_cmd), (unsigned long)parts.count);
        return nil;
    }
    NSString *payload = parts[1];

    // decode payload
    NSString *payloadString = [self decodeBase64StringWithPadding:payload];
    NSLog(@"%@ %@ called - payloadString %@", @(__FILE__), NSStringFromSelector(_cmd), payloadString);

    // convert JSON string to dictionary
    NSData *payloadData = [[NSData alloc] initWithBase64EncodedString:payloadString options:0];
    if (!payloadData) {
        NSLog(@"%@ %@ called - unable to convert payload to data", @(__FILE__), NSStringFromSelector(_cmd));
        return nil;
    }

    NSDictionary<NSString *, id> *result = nil;
    @try {
        result = [NSJSONSerialization JSONObjectWithData:payloadData options:0 error:nil];
    } @catch (NSException *exception) {
        // handle it
        NSLog(@"%@ %@ called - error serialising JSON string: %@", @(__FILE__), NSStringFromSelector(_cmd), exception);
    }
    return result;
}

@end
