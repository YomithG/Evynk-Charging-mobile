package com.example.evynkchargingmobileapp.repo;

import android.content.Context;

import com.example.evynkchargingmobileapp.R;
import com.example.evynkchargingmobileapp.data.db.UserDao;
import com.example.evynkchargingmobileapp.data.model.User;
import com.example.evynkchargingmobileapp.net.ApiClient;
import com.example.evynkchargingmobileapp.util.Prefs;

import org.json.JSONObject;

public class AuthRepository {

    public interface Callback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    // API paths (unchanged)
    private static final String PATH_LOGIN_OWNER     = "api/Auth/login-owner";
    private static final String PATH_REGISTER_OWNER  = "api/Auth/register-owner";
    private static final String PATH_ME              = "api/owner/me";
    private static final String PATH_DEACTIVATE_SELF = "api/owner/me/deactivate";

    private final Context appCtx;
    private final ApiClient api;
    private final UserDao userDao;
    private final String baseUrl; // now comes from resources

    public AuthRepository(Context ctx) {
        this.appCtx = ctx.getApplicationContext();

        // Read from strings.xml and normalize to ALWAYS end with a slash
        String raw = appCtx.getString(R.string.base_url);
        this.baseUrl = ensureTrailingSlash(raw);

        this.api     = new ApiClient(baseUrl);
        this.userDao = new UserDao(appCtx);
    }

    // -------- AUTH (already working in your project) --------
    public void login(String email, String password, final Callback<User> cb) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);

                JSONObject loginResp = api.post(PATH_LOGIN_OWNER, body, null);
                String accessToken  = optDeepString(loginResp, "accessToken", "token", "jwt", "data.accessToken");
                String refreshToken = optDeepString(loginResp, "refreshToken", "data.refreshToken");

                if (accessToken == null || accessToken.isEmpty()) {
                    cb.onError("Login succeeded but no access token returned.");
                    return;
                }

                JSONObject me = api.get(PATH_ME, accessToken);
                JSONObject owner = me.optJSONObject("owner"); if (owner == null) owner = me;

                String realNic   = optDeepString(owner, "nic", "id");
                String fullName  = optDeepString(owner, "fullName", "name");
                String emailResp = optDeepString(owner, "email");
                String phone     = optDeepString(owner, "phone", "mobile");
                int status       = owner.has("isActive") ? (owner.optBoolean("isActive", true) ? 1 : 0)
                        : owner.optInt("status", 1);

                if (realNic == null || realNic.isEmpty()) {
                    cb.onError("Profile missing NIC. Raw: " + me.toString());
                    return;
                }

                User u = new User();
                u.nic   = realNic;
                u.name  = fullName;
                u.email = (emailResp != null ? emailResp : email);
                u.phone = phone;
                u.status= status;

                userDao.upsertUser(u);
                userDao.saveTokens(realNic, accessToken, refreshToken);
                Prefs.setCurrentNic(appCtx, realNic);

                cb.onSuccess(u);
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    public void register(User user, String password, final Callback<User> cb) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("nic", user.nic);
                body.put("fullName", user.name);
                body.put("email", user.email);
                body.put("phone", user.phone);
                body.put("password", password);

                JSONObject regResp  = api.post(PATH_REGISTER_OWNER, body, null);
                String accessToken  = optDeepString(regResp, "accessToken", "token", "jwt", "data.accessToken");
                String refreshToken = optDeepString(regResp, "refreshToken", "data.refreshToken");
                if (accessToken == null || accessToken.isEmpty()) {
                    cb.onError("Register succeeded but no access token returned.");
                    return;
                }

                JSONObject me = api.get(PATH_ME, accessToken);
                JSONObject owner = me.optJSONObject("owner"); if (owner == null) owner = me;

                String realNic   = optDeepString(owner, "nic", "id");
                String fullName  = optDeepString(owner, "fullName", "name");
                String emailResp = optDeepString(owner, "email");
                String phone     = optDeepString(owner, "phone", "mobile");
                int status       = owner.has("isActive") ? (owner.optBoolean("isActive", true) ? 1 : 0)
                        : owner.optInt("status", 1);

                if (realNic == null || realNic.isEmpty()) {
                    cb.onError("Profile missing NIC. Raw: " + me.toString());
                    return;
                }

                User saved = new User();
                saved.nic   = realNic;
                saved.name  = fullName;
                saved.email = (emailResp != null ? emailResp : user.email);
                saved.phone = (phone != null ? phone : user.phone);
                saved.status= status;

                userDao.upsertUser(saved);
                userDao.saveTokens(realNic, accessToken, refreshToken);
                Prefs.setCurrentNic(appCtx, realNic);

                cb.onSuccess(saved);
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    // -------- PROFILE: UPDATE & DEACTIVATE --------

    /** PUT /api/owner/me — send only fields that changed. */
    public void updateProfile(String nicKey, String newFullName, String newPhone, final Callback<User> cb) {
        new Thread(() -> {
            try {
                String token = userDao.getAccessToken(nicKey);
                if (token == null) { cb.onError("Missing access token."); return; }

                JSONObject body = new JSONObject();
                if (newFullName != null && !newFullName.trim().isEmpty()) body.put("fullName", newFullName.trim());
                if (newPhone != null && !newPhone.trim().isEmpty())       body.put("phone", newPhone.trim());
                if (body.length() == 0) { cb.onError("Nothing to update."); return; }

                JSONObject resp = api.put(PATH_ME, body, token);
                // Expect either { owner: {...} } or the updated owner directly
                JSONObject owner = resp.optJSONObject("owner");
                if (owner == null) owner = resp;

                String realNic  = optDeepString(owner, "nic", "id");
                String fullName = optDeepString(owner, "fullName", "name");
                String email    = optDeepString(owner, "email");
                String phone    = optDeepString(owner, "phone", "mobile");
                int status      = owner.has("isActive") ? (owner.optBoolean("isActive", true) ? 1 : 0)
                        : owner.optInt("status", 1);

                if (realNic == null || realNic.isEmpty()) realNic = nicKey;

                User u = new User();
                u.nic = realNic; u.name = fullName; u.email = email; u.phone = phone; u.status = status;

                userDao.upsertUser(u);
                // Keep Prefs pointing to the canonical key (realNic)
                Prefs.setCurrentNic(appCtx, realNic);

                cb.onSuccess(u);
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    /** PUT /api/owner/me/deactivate — irreversible by user. */
    public void deactivate(String nicKey, final Callback<Boolean> cb) {
        new Thread(() -> {
            try {
                String token = userDao.getAccessToken(nicKey);
                if (token == null) { cb.onError("Missing access token."); return; }

                // Body is usually empty for this endpoint
                api.put(PATH_DEACTIVATE_SELF, new JSONObject(), token);

                // Mark local user inactive and log out
                User u = userDao.getUser(nicKey);
                if (u != null) { u.status = 0; userDao.upsertUser(u); }
                Prefs.clear(appCtx);

                cb.onSuccess(true);
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    // -------- helpers --------
    private static String optDeepString(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = optDeepString(o, k);
            if (v != null && !v.isEmpty() && !"null".equalsIgnoreCase(v)) return v;
        }
        return null;
    }

    private static String optDeepString(JSONObject o, String key) {
        if (o == null || key == null) return null;
        if (!key.contains(".")) return o.optString(key, null);
        try {
            String[] parts = key.split("\\.");
            JSONObject cur = o;
            for (int i = 0; i < parts.length - 1; i++) {
                cur = cur.optJSONObject(parts[i]);
                if (cur == null) return null;
            }
            return cur.optString(parts[parts.length - 1], null);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String ensureTrailingSlash(String s) {
        if (s == null || s.trim().isEmpty()) return "";
        return s.endsWith("/") ? s : (s + "/");
    }
}
