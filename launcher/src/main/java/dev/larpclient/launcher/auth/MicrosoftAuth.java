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

    private final String clientId;

    public MicrosoftAuth(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("No Microsoft client ID configured (Settings → Microsoft sign-in)");
        }
        this.clientId = clientId.trim();
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
        return new DeviceCode(Json.parse(Http.postForm(DEVICE_CODE, Map.of("client_id", clientId, "scope", SCOPE))));
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
                JsonObject token = Json.parse(Http.postForm(TOKEN, Map.of(
                        "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                        "client_id", clientId,
                        "device_code", code.deviceCode)));
                return completeLogin(token);
            } catch (Http.HttpStatusException e) {
                String error = e.body == null ? "" : Json.str(Json.parse(e.body), "error", "");
                if ("authorization_pending".equals(error)) {
                    continue;
                }
                if ("slow_down".equals(error)) {
                    interval += 5;
                    continue;
                }
                throw new IOException(describe(error), e);
            }
        }
        throw new IOException("The sign-in code expired, please try again");
    }

    /** Use the stored refresh token to obtain a fresh Minecraft access token. */
    public void refresh(Account account) throws IOException {
        JsonObject token = Json.parse(Http.postForm(TOKEN, Map.of(
                "grant_type", "refresh_token",
                "client_id", clientId,
                "refresh_token", account.refreshToken,
                "scope", SCOPE)));
        Account fresh = completeLogin(token);
        account.username = fresh.username;
        account.uuid = fresh.uuid;
        account.accessToken = fresh.accessToken;
        account.accessTokenExpiresAt = fresh.accessTokenExpiresAt;
        account.refreshToken = fresh.refreshToken;
    }

    private Account completeLogin(JsonObject msToken) throws IOException {
        String msAccess = Json.str(msToken, "access_token");

        JsonObject xblRequest = new JsonObject();
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + msAccess);
        xblRequest.add("Properties", xblProps);
        xblRequest.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblRequest.addProperty("TokenType", "JWT");
        JsonObject xbl = Json.parse(Http.postJson(XBL, xblRequest.toString()));
        String xblToken = Json.str(xbl, "Token");
        String userHash = Json.str(Json.arr(Json.obj(xbl, "DisplayClaims"), "xui").get(0).getAsJsonObject(), "uhs");

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
            xstsToken = Json.str(Json.parse(Http.postJson(XSTS, xstsRequest.toString())), "Token");
        } catch (Http.HttpStatusException e) {
            throw new IOException(describeXsts(e.body), e);
        }

        JsonObject loginRequest = new JsonObject();
        loginRequest.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        JsonObject login;
        try {
            login = Json.parse(Http.postJson(MC_LOGIN, loginRequest.toString()));
        } catch (Http.HttpStatusException e) {
            if (e.status == 403) {
                throw new IOException("This Azure app is not approved for Minecraft sign-in yet (see DEVELOPMENT.md)", e);
            }
            throw e;
        }
        String mcAccess = Json.str(login, "access_token");

        JsonObject profile;
        try {
            profile = Json.parse(Http.get(MC_PROFILE, mcAccess));
        } catch (Http.HttpStatusException e) {
            if (e.status == 404) {
                throw new IOException("This Microsoft account does not own Minecraft Java Edition", e);
            }
            throw e;
        }
        Account account = new Account();
        account.type = Account.Type.MICROSOFT;
        account.username = Json.str(profile, "name");
        account.uuid = Json.str(profile, "id");
        account.accessToken = mcAccess;
        account.accessTokenExpiresAt = System.currentTimeMillis() + Json.num(login, "expires_in", 86400) * 1000;
        account.refreshToken = Json.str(msToken, "refresh_token");
        return account;
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

    private static void sleep(int seconds) throws IOException {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }
}
