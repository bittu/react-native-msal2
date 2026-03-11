package com.reactnativemsal

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsService
import com.facebook.react.bridge.Arguments.createArray
import com.facebook.react.bridge.Arguments.createMap
import com.facebook.react.bridge.Arguments.fromArray
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultiTenantAccount
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication.RemoveAccountCallback
import com.microsoft.identity.client.Prompt
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.common.internal.broker.PackageHelper
import com.microsoft.identity.common.internal.ui.browser.AndroidBrowserSelector
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest
import java.util.AbstractMap
import java.util.regex.Matcher
import java.util.regex.Pattern

class RNMSALModule(reactContext: ReactApplicationContext?) :
    ReactContextBaseJavaModule(reactContext) {
    private var publicClientApplication: IMultipleAccountPublicClientApplication? = null

    override fun getName(): String {
        return NAME
    }

    @ReactMethod
    fun createPublicClientApplication(params: ReadableMap, promise: Promise) {
        val context = reactApplicationContext
        try {
            // We have to make a JSON file containing the MSAL configuration, then use that file to
            // create the PublicClientApplication
            // We first need to create the JSON model using the passed in parameters

            val config =
                if (params.hasKey("androidConfigOptions")) params.getMap("androidConfigOptions") else null

            val msalConfigJsonObj =
                if (config != null) ReadableMapUtils.toJsonObject(config) else JSONObject()

            // Account mode. Required to be MULTIPLE for this library
            msalConfigJsonObj.put("account_mode", "MULTIPLE")

            // If broker_redirect_uri_registered is not provided in androidConfigOptions,
            // default it to false
            if (!msalConfigJsonObj.has("broker_redirect_uri_registered")) {
                msalConfigJsonObj.put("broker_redirect_uri_registered", false)
            }

            val auth = params.getMap("auth")

            // Authority
            val authority = ReadableMapUtils.getStringOrDefault(
                auth,
                "authority",
                "https://login.microsoftonline.com/common"
            )
            msalConfigJsonObj.put("authority", authority)

            // Client id
            msalConfigJsonObj.put("client_id", ReadableMapUtils.getStringOrThrow(auth, "clientId"))

            // Redirect URI
            msalConfigJsonObj.put(
                "redirect_uri",
                if (auth!!.hasKey("redirectUri")) auth.getString("redirectUri") else makeRedirectUri(
                    context
                ).toString()
            )

            // Authorities
            val knownAuthorities = auth.getArray("knownAuthorities")
            // List WILL be instantiated and empty if `knownAuthorities` is null
            val authoritiesList = readableArrayToStringList(knownAuthorities)
            // Make sure the `authority` makes it in the authority list
            if (!authoritiesList.contains(authority)) {
                authoritiesList.add(authority)
            }
            // The authoritiesList is just a list of urls (strings), but the native android MSAL
            // library expects an array of objects, so we have to parse the urls
            val authoritiesJsonArr = makeAuthoritiesJsonArray(authoritiesList, authority)
            msalConfigJsonObj.put("authorities", authoritiesJsonArr)

            // Serialize the JSON config to a string
            val serializedMsalConfig = msalConfigJsonObj.toString()
            Log.d("RNMSALModule", serializedMsalConfig)

            // Create a temporary file and write the serialized config to it
            val file = File.createTempFile("RNMSAL_msal_config", ".tmp")
            file.deleteOnExit()
            val writer = FileWriter(file)
            writer.write(serializedMsalConfig)
            writer.close()

            // Finally, create the PCA with the temporary config file we created
            publicClientApplication =
                PublicClientApplication.createMultipleAccountPublicClientApplication(
                    context, file
                )
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject(e)
        }
    }

    @ReactMethod
    fun getSelectedBrowser(promise: Promise) {
        val safeList = publicClientApplication?.configuration?.browserSafeList

        val browser = if (!safeList.isNullOrEmpty()) {
            AndroidBrowserSelector(reactApplicationContext).selectBrowser(safeList, null)
        } else null

        //if (browser == null) {
        val result = browser?.let {
            "${it.packageName} ${it.version} ${it.signatureHashes} (${if (it.isCustomTabsServiceSupported) "CustomTab" else "NoCustomTab"})"
        } ?: "Unknown"

        promise.resolve(result)
    }

    @ReactMethod
    fun getSafeCustomTabsBrowsers(promise: Promise) {
        try {
            val safeList = publicClientApplication?.configuration?.browserSafeList
            if (safeList.isNullOrEmpty()) {
                promise.resolve(createArray())
                return
            }

            val pm = reactApplicationContext.packageManager

            val customTabsIntent = Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION)

            val servicePackages = pm.queryIntentServices(customTabsIntent, 0)
                .mapNotNull { it.serviceInfo?.packageName }
                .distinct()
                .sorted()

            val safeEntriesByPackage = safeList.groupBy { it.packageName }

            val result = createArray()

            for (packageName in servicePackages) {
                val safeEntries = safeEntriesByPackage[packageName].orEmpty()
                if (safeEntries.isEmpty()) {
                    continue
                }

                val packageInfo = getPackageInfoCompat(pm, packageName) ?: continue
                val version = packageInfo.versionName
                    ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toString()
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toString()
                    }
                val installedSignatures = PackageHelper.generateSignatureHashes(packageInfo)

                val matchingSignature = installedSignatures.firstOrNull { installed ->
                    safeEntries.any { safeEntry ->
                        safeEntry.signatureHashes.any { safeHash ->
                            safeHash.equals(installed, ignoreCase = false)
                        }
                    }
                } ?: continue

                val signatures = createArray()
                signatures.pushString(matchingSignature)
                val item = createMap().apply {
                    putString("browser_package_name", packageName)
                    putString("browser_version_lower_bound", version)
                    putArray("browser_signature_hashes", signatures)
                }
                result.pushMap(item)
            }

            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("GET_SAFE_CUSTOM_TABS_BROWSERS_FAILED", e)
        }
    }

    private fun getPackageInfoCompat(pm: PackageManager, packageName: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        } catch (_: Exception) {
            null
        }
    }

    @Throws(JSONException::class, IllegalArgumentException::class)
    private fun makeAuthoritiesJsonArray(
        authorityUrls: MutableList<String>,
        authority: String?
    ): JSONArray {
        val authoritiesJsonArr = JSONArray()
        var foundDefaultAuthority = false

        for (authorityUrl in authorityUrls) {
            val authorityJsonObj = JSONObject()

            // Authority is set as the default if one is not set yet, and it matches `authority`
            if (!foundDefaultAuthority && authorityUrl == authority) {
                authorityJsonObj.put("default", true)
                foundDefaultAuthority = true
            }

            val aadAuthorityMatcher: Matcher = aadAuthorityPattern.matcher(authorityUrl)
            val b2cAuthorityMatcher: Matcher = b2cAuthorityPattern.matcher(authorityUrl)

            if (aadAuthorityMatcher.find()) {
                val group = aadAuthorityMatcher.group(1)
                requireNotNull(group) { "Could not match group 1 for regex https://login.microsoftonline.com/([^/]+) in authority \"$authorityUrl\"" }

                val audience = when (group) {
                    "common" -> JSONObject().put("type", "AzureADandPersonalMicrosoftAccount")
                    "organizations" -> JSONObject().put("type", "AzureADMultipleOrgs")
                    "consumers" -> JSONObject().put("type", "PersonalMicrosoftAccount")
                    else ->  // assume `group` is a tenant id
                        JSONObject().put("type", "AzureADMyOrg").put("tenant_id", group)
                }
                authorityJsonObj.put("type", AUTHORITY_TYPE_AAD)
                authorityJsonObj.put("audience", audience)
            } else if (b2cAuthorityMatcher.find()) {
                authorityJsonObj.put("type", AUTHORITY_TYPE_B2C)
                authorityJsonObj.put("authority_url", authorityUrl)
            } else {
                throw IllegalArgumentException("Authority \"$authorityUrl\" doesn't match AAD regex https://login.microsoftonline.com/([^/]+) or B2C regex https://([^/]+)/tfp/([^/]+)/.+")
            }

            authoritiesJsonArr.put(authorityJsonObj)
        }

        // If a default authority was not found, we set the first authority as the default
        if (!foundDefaultAuthority && authoritiesJsonArr.length() > 0) {
            authoritiesJsonArr.getJSONObject(0).put("default", true)
        }

        return authoritiesJsonArr
    }

    @Throws(Exception::class)
    private fun makeRedirectUri(context: ReactApplicationContext): Uri? {
        try {
            val packageName = context.packageName
            val info = context.packageManager
                .getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            if (info.signatures?.size != 1) {
                throw RuntimeException("RNMSAL expected there to be exactly one signature for package $packageName")
            }
            val signature = info.signatures?.first()
            val messageDigest = MessageDigest.getInstance("SHA")
            messageDigest.update(signature!!.toByteArray())
            val signatureHash = Base64.encodeToString(messageDigest.digest(), Base64.NO_WRAP)
            Log.d("RNMSALModule", signatureHash)

            return Uri.Builder().scheme("msauth")
                .authority(packageName)
                .appendPath(signatureHash)
                .build()
        } catch (ex: Exception) {
            throw Exception(
                "Could not create redirect uri from package name and signature hash",
                ex
            )
        }
    }

    @ReactMethod
    fun acquireToken(params: ReadableMap, promise: Promise) {
        try {
            val acquireTokenParameters =
                AcquireTokenParameters.Builder()
                    .startAuthorizationFromActivity(this.reactApplicationContext.currentActivity)

            // Required parameters
            val scopes = readableArrayToStringList(params.getArray("scopes"))
            acquireTokenParameters.withScopes(scopes)

            // Optional parameters
            if (params.hasKey("authority")) {
                acquireTokenParameters.fromAuthority(params.getString("authority"))
            }

            if (params.hasKey("promptType")) {
                acquireTokenParameters.withPrompt(Prompt.entries[params.getInt("promptType")])
            }

            if (params.hasKey("loginHint")) {
                acquireTokenParameters.withLoginHint(params.getString("loginHint"))
            }

            if (params.hasKey("extraScopesToConsent")) {
                acquireTokenParameters.withOtherScopesToAuthorize(
                    readableArrayToStringList(params.getArray("extraScopesToConsent"))
                )
            }

            if (params.hasKey("extraQueryParameters")) {
                val parameters: MutableList<MutableMap.MutableEntry<String?, String?>?> =
                    ArrayList()
                params.getMap("extraQueryParameters")?.toHashMap()?.entries?.let {
                    for (entry in it) {
                        parameters.add(
                            AbstractMap.SimpleEntry<String?, String?>(
                                entry.key,
                                entry.value.toString()
                            )
                        )
                    }
                }
                acquireTokenParameters.withAuthorizationQueryStringParameters(parameters)
            }

            acquireTokenParameters.withCallback(getAuthInteractiveCallback(promise))
            publicClientApplication!!.acquireToken(acquireTokenParameters.build())
        } catch (e: Exception) {
            promise.reject(e)
        }
    }

    private fun getAuthInteractiveCallback(promise: Promise): AuthenticationCallback {
        return object : AuthenticationCallback {
            override fun onCancel() {
                promise.reject("userCancel", "userCancel")
            }

            override fun onSuccess(authenticationResult: IAuthenticationResult?) {
                if (authenticationResult != null) {
                    promise.resolve(msalResultToDictionary(authenticationResult))
                } else {
                    promise.resolve(null)
                }
            }

            override fun onError(exception: MsalException) {
                promise.reject(exception)
            }
        }
    }

    @ReactMethod
    fun acquireTokenSilent(params: ReadableMap, promise: Promise) {
        try {
            val acquireTokenSilentParameters =
                AcquireTokenSilentParameters.Builder()

            // Required parameters
            val scopes = readableArrayToStringList(params.getArray("scopes"))
            acquireTokenSilentParameters.withScopes(scopes)

            val accountIn = params.getMap("account")
            val accountIdentifier = accountIn?.getString("identifier")
            val account = publicClientApplication!!.getAccount(
                accountIdentifier!!
            )
            acquireTokenSilentParameters.forAccount(account)

            // Optional parameters
            var authority: String? =
                publicClientApplication!!
                    .configuration
                    .defaultAuthority
                    .authorityURL
                    .toString()
            if (params.hasKey("authority")) {
                authority = params.getString("authority")
            }
            acquireTokenSilentParameters.fromAuthority(authority)

            if (params.hasKey("forceRefresh")) {
                acquireTokenSilentParameters.forceRefresh(params.getBoolean("forceRefresh"))
            }

            acquireTokenSilentParameters.withCallback(getAuthSilentCallback(promise))
            publicClientApplication!!.acquireTokenSilentAsync(acquireTokenSilentParameters.build())
        } catch (e: Exception) {
            promise.reject(e)
        }
    }

    private fun getAuthSilentCallback(promise: Promise): SilentAuthenticationCallback {
        return object : SilentAuthenticationCallback {
            override fun onSuccess(authenticationResult: IAuthenticationResult?) {
                if (authenticationResult != null) {
                    promise.resolve(msalResultToDictionary(authenticationResult))
                } else {
                    promise.resolve(null)
                }
            }

            override fun onError(exception: MsalException) {
                promise.reject(exception)
            }
        }
    }

    @ReactMethod
    fun getAccounts(promise: Promise) {
        try {
            val accounts = publicClientApplication!!.accounts
            val array = createArray()
            if (accounts != null) {
                for (account in accounts) {
                    array.pushMap(accountToMap(account))
                }
            }
            promise.resolve(array)
        } catch (e: Exception) {
            promise.reject(e)
        }
    }

    @ReactMethod
    fun getAccount(accountIdentifier: String, promise: Promise) {
        try {
            val account = publicClientApplication!!.getAccount(accountIdentifier)
            if (account != null) {
                promise.resolve(accountToMap(account))
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            promise.reject(e)
        }
    }

    @ReactMethod
    fun removeAccount(accountIn: ReadableMap, promise: Promise) {
        try {
            // Required parameters
            publicClientApplication?.let { app ->
                accountIn.getString(("identifier"))?.let {
                    val account = app.getAccount(it)

                    app.removeAccount(
                        account,
                        object : RemoveAccountCallback {
                            override fun onRemoved() {
                                promise.resolve(true)
                            }

                            override fun onError(exception: MsalException) {
                                promise.reject(exception)
                            }
                        })
                }
            }
        } catch (e: Exception) {
            promise.reject(e)
        }
    }

    private fun msalResultToDictionary(result: IAuthenticationResult): WritableMap {
        val map = createMap()
        map.putString("accessToken", result.accessToken)
        map.putString("expiresOn", String.format("%s", result.expiresOn.time / 1000))
        var idToken = result.account.idToken
        if (idToken == null) {
            idToken =
                (result.account as IMultiTenantAccount).tenantProfiles[result.tenantId]?.idToken
        }
        map.putString("idToken", idToken)
        map.putArray("scopes", fromArray(result.scope))
        map.putString("tenantId", result.tenantId)
        map.putMap("account", accountToMap(result.account))
        return map
    }

    private fun accountToMap(account: IAccount): WritableMap {
        val map = createMap()
        map.putString("identifier", account.id)
        map.putString("username", account.username)
        map.putString("tenantId", account.tenantId)
        val claims = account.claims
        if (claims != null) {
            map.putMap("claims", toWritableMap(claims))
        }
        return map
    }


    private fun readableArrayToStringList(readableArray: ReadableArray?): MutableList<String> {
        val list: MutableList<String> = ArrayList()
        if (readableArray != null) {
            for (item in readableArray.toArrayList()) {
                list.add(item.toString())
            }
        }
        return list
    }

    private fun toWritableMap(map: MutableMap<String?, *>): WritableMap {
        val writableMap = createMap()
        for (entry in map.entries) {
            val key: String = entry.key!!
            when (val value: Any? = entry.value) {
                null -> {
                    writableMap.putNull(key)
                }

                is Boolean -> {
                    writableMap.putBoolean(key, value)
                }

                is Double -> {
                    writableMap.putDouble(key, value)
                }

                is Int -> {
                    writableMap.putInt(key, value)
                }

                is String -> {
                    writableMap.putString(key, value)
                }

                is MutableMap<*, *> -> {
                    writableMap.putMap(key, toWritableMap(value as MutableMap<String?, *>))
                }

                is MutableList<*> -> {
                    writableMap.putArray(key, toWritableArray(value))
                }
            }
        }
        return writableMap
    }

    private fun toWritableArray(list: MutableList<*>): WritableArray {
        val writableArray = createArray()
        for (value in list.toTypedArray()) {
            when (value) {
                null -> {
                    writableArray.pushNull()
                }

                is Boolean -> {
                    writableArray.pushBoolean(value)
                }

                is Double -> {
                    writableArray.pushDouble(value)
                }

                is Int -> {
                    writableArray.pushInt(value)
                }

                is String -> {
                    writableArray.pushString(value)
                }

                is MutableMap<*, *> -> {
                    writableArray.pushMap(toWritableMap(value as MutableMap<String?, *>))
                }

                is MutableList<*> -> {
                    writableArray.pushArray(toWritableArray(value))
                }
            }
        }
        return writableArray
    }

    companion object {
        const val NAME = "RNMSAL"
        private const val AUTHORITY_TYPE_B2C = "B2C"
        private const val AUTHORITY_TYPE_AAD = "AAD"

        private val aadAuthorityPattern: Pattern =
            Pattern.compile("https://login\\.microsoftonline\\.com/([^/]+)")
        private val b2cAuthorityPattern: Pattern = Pattern.compile("https://([^/]+)(/\S*)?")
    }
}
