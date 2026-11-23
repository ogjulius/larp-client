package dev.larpclient.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.larpclient.launcher.util.Http;
import dev.larpclient.launcher.util.Json;

import java.io.IOException;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Microsoft → Xbox Live → XSTS → Minecraft sign-in using the OAuth device code flow, so no
 * embedded browser or local web server is needed. Requires an Azure application ID that has been
 * granted access to the Minecraft services API.
 */
public final class MicrosoftAuth {
    private static final String DEVICE_CODE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE = "https://api.minecraftservices.com/minecraft/profile";
    private static final String SCOPE = "XboxLive.signin offline_access";

    /** The HTTP calls this flow makes; replaced in tests. */
    interface Transport {
        String postForm(String url, Map<String, String> form) throws IOException;

        String postJson(String url, String json) throws IOException;

        String get(String url, String bearer) throws IOException;
    }

    /** Waits between device-code polls; replaced in tests. */
    interface Sleeper {
        void sleep(int seconds) throws InterruptedException;
    }

    private static final Transport HTTP = new Transport() {
        @Override
        public String postForm(String url, Map<String, String> form) throws IOException {
            return Http.postForm(url, form);
        }

        @Override
        public String postJson(String url, String json) throws IOException {
            return Http.postJson(url, json);
        }

        @Override
        public String get(String url, String bearer) throws IOException {
            return Http.get(url, bearer);
        }
    };

    private final String clientId;
    private final Transport http;
    private final Sleeper sleeper;

    public MicrosoftAuth(String clientId) {
        this(clientId, HTTP, seconds -> Thread.sleep(seconds * 1000L));
    }

    MicrosoftAuth(String clientId, Transport http, Sleeper sleeper) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("No Microsoft client ID configured (Settings → Microsoft sign-in)");
        }
        this.clientId = clientId.trim();
        this.http = http;
        this.sleeper = sleeper;
    }

    /** Shown to the user: open {@code verificationUri} and type {@code userCode}. */
    public static final class DeviceCode {
        public final String userCode;
        public final String verificationUri;
        final String deviceCode;
        final int interval;
        final long expiresAt;

        DeviceCode(JsonObject json) {
            userCode = Json.str(json, "user_code");
            verificationUri = Json.str(json, "verification_uri");
            deviceCode = Json.str(json, "device_code");
            interval = (int) Json.num(json, "interval", 5);
            expiresAt = System.currentTimeMillis() + Json.num(json, "expires_in", 900) * 1000;
        }
    }

    public DeviceCode requestDeviceCode() throws IOException {
        JsonObject json = parse(http.postForm(DEVICE_CODE, Map.of("client_id", clientId, "scope", SCOPE)), "Microsoft");
        required(json, "device_code", "Microsoft");
        required(json, "user_code", "Microsoft");
        return new DeviceCode(json);
    }

    /** Polls until the user finishes signing in, then completes the Minecraft login. */
    public Account awaitLogin(DeviceCode code, BooleanSupplier cancelled) throws IOException {
        int interval = code.interval;
        while (System.currentTimeMillis() < code.expiresAt) {
            if (cancelled.getAsBoolean()) {
                throw new IOException("Sign-in cancelled");
            }
            sleep(interval);
            try {
                JsonObject token = parse(http.postForm(TOKEN, Map.of(
                        "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                        "client_id", clientId,
                        "device_code", code.deviceCode)), "Microsoft");
                return completeLogin(token);
            } catch (Http.HttpStatusException e) {
                String error = oauthError(e.body);
                if ("authorization_pending".equals(error)) {
                    continue;
                }
                if ("slow_down".equals(error)) {
                    interval += 5;
                    continue;
                }
                throw new IOException(error.isEmpty() ? "Microsoft sign-in failed (HTTP " + e.status + ")" : describe(error), e);
            }
        }
        throw new IOException("The sign-in code expired, please try again");
    }

    /** Use the stored refresh token to obtain a fresh Minecraft access token. */
    public void refresh(Account account) throws IOException {
        if (account.refreshToken == null || account.refreshToken.isEmpty()) {
            throw new IOException("This account's sign-in has expired. Remove it and sign in again.");
        }
        JsonObject token = parse(http.postForm(TOKEN, Map.of(
                "grant_type", "refresh_token",
                "client_id", clientId,
                "refresh_token", account.refreshToken,
                "scope", SCOPE)), "Microsoft");
        Account fresh = completeLogin(token);
        account.username = fresh.username;
        account.uuid = fresh.uuid;
        account.accessToken = fresh.accessToken;
        account.accessTokenExpiresAt = fresh.accessTokenExpiresAt;
        if (fresh.refreshToken != null) { // OAuth may omit it: the old one stays valid
            account.refreshToken = fresh.refreshToken;
        }
    }

    private Account completeLogin(JsonObject msToken) throws IOException {
        String msAccess = required(msToken, "access_token", "Microsoft");

        JsonObject xblRequest = new JsonObject();
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + msAccess);
        xblRequest.add("Properties", xblProps);
        xblRequest.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblRequest.addProperty("TokenType", "JWT");
        JsonObject xbl = parse(http.postJson(XBL, xblRequest.toString()), "Xbox Live");
        String xblToken = required(xbl, "Token", "Xbox Live");
        JsonArray xui = Json.arr(Json.obj(xbl, "DisplayClaims"), "xui");
        String userHash = xui.size() > 0 && xui.get(0).isJsonObject() ? Json.str(xui.get(0).getAsJsonObject(), "uhs") : null;
        if (userHash == null) {
            throw new IOException("Unexpected response from Xbox Live: missing user hash");
        }

        JsonObject xstsRequest = new JsonObject();
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        xstsProps.add("UserTokens", userTokens);
        xstsRequest.add("Properties", xstsProps);
        xstsRequest.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsRequest.addProperty("TokenType", "JWT");
        String xstsToken;
        try {
            xstsToken = required(parse(http.postJson(XSTS, xstsRequest.toString()), "Xbox Live"), "Token", "Xbox Live");
        } catch (Http.HttpStatusException e) {
            throw new IOException(describeXsts(e.body), e);
        }

        JsonObject loginRequest = new JsonObject();
        loginRequest.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        JsonObject login;
        try {
            login = parse(http.postJson(MC_LOGIN, loginRequest.toString()), "Minecraft");
        } catch (Http.HttpStatusException e) {
            if (e.status == 403) {
                throw new IOException("This Azure app is not approved for Minecraft sign-in yet (see DEVELOPMENT.md)", e);
            }
            throw e;
        }
        String mcAccess = required(login, "access_token", "Minecraft");

        JsonObject profile;
        try {
            profile = parse(http.get(MC_PROFILE, mcAccess), "Minecraft");
        } catch (Http.HttpStatusException e) {
            if (e.status == 404) {
                throw new IOException("This Microsoft account does not own Minecraft Java Edition", e);
            }
            throw e;
        }
        Account account = new Account();
        account.type = Account.Type.MICROSOFT;
        account.username = required(profile, "name", "Minecraft");
        account.uuid = required(profile, "id", "Minecraft");
        account.accessToken = mcAccess;
        account.accessTokenExpiresAt = System.currentTimeMillis() + Json.num(login, "expires_in", 86400) * 1000;
        account.refreshToken = Json.str(msToken, "refresh_token");
        return account;
    }

    /** Parse a JSON object response, turning anything else (HTML error pages, garbage) into an IOException. */
    private static JsonObject parse(String body, String service) throws IOException {
        try {
            JsonObject json = Json.parse(body);
            if (json == null) {
                throw new IOException("Unexpected empty response from " + service);
            }
            return json;
        } catch (RuntimeException e) { // JsonParseException, or a non-object root
            throw new IOException("Unexpected response from " + service, e);
        }
    }

    private static String required(JsonObject json, String key, String service) throws IOException {
        String value;
        try {
            value = Json.str(json, key);
        } catch (RuntimeException e) {
            value = null;
        }
        if (value == null || value.isEmpty()) {
            throw new IOException("Unexpected response from " + service + ": missing " + key);
        }
        return value;
    }

    /** The OAuth {@code error} code from an error body, or "" if there is none (e.g. an HTML page). */
    private static String oauthError(String body) {
        try {
            return body == null ? "" : Json.str(Json.parse(body), "error", "");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String describe(String error) {
        switch (error) {
            case "expired_token": return "The sign-in code expired, please try again";
            case "authorization_declined": return "Sign-in was declined";
            case "bad_verification_code": return "Invalid sign-in code";
            default: return "Microsoft sign-in failed: " + error;
        }
    }

    private static String describeXsts(String body) {
        long code = 0;
        try {
            code = Json.num(Json.parse(body), "XErr", 0);
        } catch (Exception ignored) {
            // unknown body
        }
        if (code == 2148916233L) {
            return "This Microsoft account has no Xbox profile. Sign in at minecraft.net once first.";
        }
        if (code == 2148916238L) {
            return "Child accounts must be added to a Microsoft family by an adult first.";
        }
        if (code == 2148916235L) {
            return "Xbox Live is not available in your country.";
        }
        return "Xbox Live sign-in failed" + (code != 0 ? " (" + code + ")" : "");
    }

    private void sleep(int seconds) throws IOException {
        try {
            sleeper.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }
}
