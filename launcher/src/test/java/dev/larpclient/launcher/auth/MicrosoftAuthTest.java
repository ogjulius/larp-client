package dev.larpclient.launcher.auth;

import dev.larpclient.launcher.util.Http;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicrosoftAuthTest {
    private static final String DEVICE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String PROFILE = "https://api.minecraftservices.com/minecraft/profile";

    /** Scripted responses per URL; records request bodies. */
    private static final class FakeTransport implements MicrosoftAuth.Transport {
        final Map<String, Queue<Object[]>> responses = new HashMap<>();
        final List<String> requests = new ArrayList<>();

        FakeTransport on(String url, int status, String body) {
            responses.computeIfAbsent(url, k -> new ArrayDeque<>()).add(new Object[]{status, body});
            return this;
        }

        private String answer(String url, String request) throws IOException {
            requests.add(url + " " + request);
            Queue<Object[]> q = responses.get(url);
            Object[] r = q == null ? null : q.poll();
            if (r == null) {
                throw new AssertionError("Unexpected request to " + url);
            }
            int status = (Integer) r[0];
            if (status / 100 != 2) {
                throw new Http.HttpStatusException(status, url, (String) r[1]);
            }
            return (String) r[1];
        }

        @Override
        public String postForm(String url, Map<String, String> form) throws IOException {
            return answer(url, form.toString());
        }

        @Override
        public String postJson(String url, String json) throws IOException {
            return answer(url, json);
        }

        @Override
        public String get(String url, String bearer) throws IOException {
            return answer(url, "Bearer " + bearer);
        }
    }

    private final List<Integer> sleeps = new ArrayList<>();

    private MicrosoftAuth auth(FakeTransport t) {
        return new MicrosoftAuth("client-id", t, sleeps::add);
    }

    private static FakeTransport happyXboxChain(FakeTransport t) {
        return t.on(XBL, 200, "{\"Token\":\"xbl-token\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"user-hash\"}]}}")
                .on(XSTS, 200, "{\"Token\":\"xsts-token\"}")
                .on(MC_LOGIN, 200, "{\"access_token\":\"mc-token\",\"expires_in\":86400}")
                .on(PROFILE, 200, "{\"id\":\"0123456789abcdef0123456789abcdef\",\"name\":\"Steve\"}");
    }

    private static FakeTransport withDeviceCode(FakeTransport t, int expiresIn) {
        return t.on(DEVICE, 200, "{\"user_code\":\"ABCD\",\"verification_uri\":\"https://microsoft.com/link\","
                + "\"device_code\":\"dev\",\"interval\":1,\"expires_in\":" + expiresIn + "}");
    }

    @Test
    void deviceCodeFlowHandlesPendingAndSlowDownThenSignsIn() throws IOException {
        FakeTransport t = withDeviceCode(new FakeTransport(), 900)
                .on(TOKEN, 400, "{\"error\":\"authorization_pending\"}")
                .on(TOKEN, 400, "{\"error\":\"slow_down\"}")
                .on(TOKEN, 200, "{\"access_token\":\"ms-token\",\"refresh_token\":\"refresh-1\"}");
        happyXboxChain(t);
        MicrosoftAuth auth = auth(t);
        MicrosoftAuth.DeviceCode code = auth.requestDeviceCode();
        assertEquals("ABCD", code.userCode);

        Account account = auth.awaitLogin(code, () -> false);

        assertEquals(Account.Type.MICROSOFT, account.type);
        assertEquals("Steve", account.username);
        assertEquals("0123456789abcdef0123456789abcdef", account.uuid);
        assertEquals("mc-token", account.accessToken);
        assertEquals("refresh-1", account.refreshToken);
        assertEquals(List.of(1, 1, 6), sleeps, "slow_down adds 5 s to the polling interval");
        assertTrue(t.requests.stream().anyMatch(r -> r.startsWith(MC_LOGIN) && r.contains("XBL3.0 x=user-hash;xsts-token")));
        assertTrue(t.requests.stream().anyMatch(r -> r.startsWith(XBL) && r.contains("d=ms-token")));
    }

    @Test
    void declinedSignInIsReported() throws IOException {
        FakeTransport t = withDeviceCode(new FakeTransport(), 900).on(TOKEN, 400, "{\"error\":\"authorization_declined\"}");
        MicrosoftAuth auth = auth(t);
        IOException e = assertThrows(IOException.class, () -> auth.awaitLogin(auth.requestDeviceCode(), () -> false));
        assertEquals("Sign-in was declined", e.getMessage());
    }

    @Test
    void cancellationStopsPolling() throws IOException {
        FakeTransport t = withDeviceCode(new FakeTransport(), 900);
        MicrosoftAuth auth = auth(t);
        IOException e = assertThrows(IOException.class, () -> auth.awaitLogin(auth.requestDeviceCode(), () -> true));
        assertEquals("Sign-in cancelled", e.getMessage());
    }

    @Test
    void expiredCodeIsReported() throws IOException {
        FakeTransport t = withDeviceCode(new FakeTransport(), 0);
        MicrosoftAuth auth = auth(t);
        IOException e = assertThrows(IOException.class, () -> auth.awaitLogin(auth.requestDeviceCode(), () -> false));
        assertTrue(e.getMessage().contains("expired"));
    }

    @Test
    void xstsErrorCodesBecomeReadableMessages() {
        FakeTransport t = new FakeTransport()
                .on(TOKEN, 200, "{\"access_token\":\"ms\",\"refresh_token\":\"r\"}")
                .on(XBL, 200, "{\"Token\":\"x\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"h\"}]}}")
                .on(XSTS, 401, "{\"XErr\":2148916233}");
        IOException e = assertThrows(IOException.class, () -> auth(t).refresh(account("r")));
        assertTrue(e.getMessage().contains("no Xbox profile"), e.getMessage());
    }

    @Test
    void unapprovedAzureAppIsExplained() {
        FakeTransport t = new FakeTransport()
                .on(TOKEN, 200, "{\"access_token\":\"ms\",\"refresh_token\":\"r\"}")
                .on(XBL, 200, "{\"Token\":\"x\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"h\"}]}}")
                .on(XSTS, 200, "{\"Token\":\"s\"}")
                .on(MC_LOGIN, 403, "");
        IOException e = assertThrows(IOException.class, () -> auth(t).refresh(account("r")));
        assertTrue(e.getMessage().contains("not approved"), e.getMessage());
    }

    @Test
    void accountWithoutGameIsExplained() {
        FakeTransport t = happyXboxChain(new FakeTransport()
                .on(TOKEN, 200, "{\"access_token\":\"ms\",\"refresh_token\":\"r\"}"));
        t.responses.get(PROFILE).clear();
        t.on(PROFILE, 404, "{}");
        IOException e = assertThrows(IOException.class, () -> auth(t).refresh(account("r")));
        assertTrue(e.getMessage().contains("does not own"), e.getMessage());
    }

    @Test
    void refreshUpdatesTokens() throws IOException {
        FakeTransport t = happyXboxChain(new FakeTransport()
                .on(TOKEN, 200, "{\"access_token\":\"ms\",\"refresh_token\":\"refresh-2\"}"));
        Account a = account("refresh-1");
        auth(t).refresh(a);
        assertEquals("mc-token", a.accessToken);
        assertEquals("refresh-2", a.refreshToken);
        assertTrue(a.accessTokenExpiresAt > System.currentTimeMillis());
    }

    /** Regression (audit A16): a refresh response without a new refresh token keeps the old one. */
    @Test
    void refreshWithoutNewRefreshTokenKeepsTheOldOne() throws IOException {
        FakeTransport t = happyXboxChain(new FakeTransport().on(TOKEN, 200, "{\"access_token\":\"ms\"}"));
        Account a = account("refresh-1");
        auth(t).refresh(a);
        assertEquals("refresh-1", a.refreshToken);
    }

    /** Regression (audit A17): malformed responses fail with IOException and a clear message, not a runtime crash. */
    @Test
    void malformedResponsesFailCleanly() throws IOException {
        FakeTransport missingXui = new FakeTransport()
                .on(TOKEN, 200, "{\"access_token\":\"ms\"}")
                .on(XBL, 200, "{\"Token\":\"x\",\"DisplayClaims\":{\"xui\":[]}}");
        IOException e1 = assertThrows(IOException.class, () -> auth(missingXui).refresh(account("r")));
        assertTrue(e1.getMessage().contains("Xbox Live"), e1.getMessage());

        FakeTransport htmlError = withDeviceCode(new FakeTransport(), 900).on(TOKEN, 502, "<html>Bad gateway</html>");
        MicrosoftAuth auth = auth(htmlError);
        MicrosoftAuth.DeviceCode code = auth.requestDeviceCode();
        IOException e2 = assertThrows(IOException.class, () -> auth.awaitLogin(code, () -> false));
        assertTrue(e2.getMessage().contains("502"), e2.getMessage());

        FakeTransport noToken = new FakeTransport().on(TOKEN, 200, "{}");
        IOException e3 = assertThrows(IOException.class, () -> auth(noToken).refresh(account("r")));
        assertTrue(e3.getMessage().contains("access_token"), e3.getMessage());
    }

    private static Account account(String refresh) {
        Account a = new Account();
        a.type = Account.Type.MICROSOFT;
        a.refreshToken = refresh;
        return a;
    }
}
