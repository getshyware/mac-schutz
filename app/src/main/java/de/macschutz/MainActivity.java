package de.macschutz;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MAC-Schutz: legt für die eingetragenen WLANs "Netzwerk-Vorschläge" an,
 * bei denen Android eine nicht-dauerhafte (wechselnde) zufällige MAC-Adresse nutzt.
 * Funktioniert ohne Root ab Android 12.
 */
public class MainActivity extends Activity {

    private static final int REQ_LOCATION = 1;

    private WifiManager wifi;
    private EditText ssidInput;
    private EditText passInput;
    private CheckBox wpa3Box;
    private TextView resultText;
    private TextView approvalText;
    private LinearLayout listContainer;
    private Object approvalListener; // WifiManager.SuggestionUserApprovalStatusListener (API 31+)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applySystemBarPadding();

        wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            TextView tooOld = findViewById(R.id.tooOld);
            tooOld.setText("Diese App braucht Android 12 oder neuer.\n\n"
                    + "Gute Nachricht: Ab Android 10 nutzt dein Handy schon von selbst "
                    + "für jedes WLAN eine zufällige MAC-Adresse. Deine echte Geräteadresse "
                    + "ist also bereits versteckt.");
            tooOld.setVisibility(View.VISIBLE);
            findViewById(R.id.mainPart).setVisibility(View.GONE);
            return;
        }
        setupMain();
    }

    /** Ab Android 15 zeichnet die App bis unter die Statusleiste – deshalb Abstand einfügen. */
    private void applySystemBarPadding() {
        final View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                top = i.top; bottom = i.bottom; left = i.left; right = i.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            v.setPadding(left, top, right, bottom);
            return insets;
        });
    }

    @TargetApi(Build.VERSION_CODES.S)
    private void setupMain() {
        ssidInput = findViewById(R.id.ssid);
        passInput = findViewById(R.id.password);
        wpa3Box = findViewById(R.id.wpa3);
        resultText = findViewById(R.id.result);
        approvalText = findViewById(R.id.approval);
        listContainer = findViewById(R.id.list);

        CheckBox showPass = findViewById(R.id.showPass);
        showPass.setOnCheckedChangeListener((b, checked) -> {
            passInput.setInputType(InputType.TYPE_CLASS_TEXT | (checked
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            passInput.setSelection(passInput.getText().length());
        });

        findViewById(R.id.btnCurrent).setOnClickListener(v -> takeCurrentWifi());
        findViewById(R.id.btnProtect).setOnClickListener(v -> protect());
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));

        WifiManager.SuggestionUserApprovalStatusListener l = this::showApproval;
        approvalListener = l;
        wifi.addSuggestionUserApprovalStatusListener(getMainExecutor(), l);

        refreshList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && listContainer != null) {
            refreshList();
            if (!wifi.isWifiEnabled()) {
                resultText.setTextColor(getColor(R.color.warn));
                resultText.setText("Hinweis: WLAN ist gerade ausgeschaltet.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && approvalListener != null) {
            removeApprovalListener();
        }
        super.onDestroy();
    }

    @TargetApi(Build.VERSION_CODES.S)
    private void removeApprovalListener() {
        wifi.removeSuggestionUserApprovalStatusListener(
                (WifiManager.SuggestionUserApprovalStatusListener) approvalListener);
    }

    // ---------------------------------------------------------------------------------
    // Aktuelles WLAN übernehmen
    // ---------------------------------------------------------------------------------

    private void takeCurrentWifi() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        fillCurrentSsid();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_LOCATION) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fillCurrentSsid();
        } else {
            toast("Ohne Standort-Erlaubnis verrät Android den WLAN-Namen nicht. "
                    + "Tippe den Namen einfach selbst ein.");
        }
    }

    @SuppressWarnings("deprecation")
    private void fillCurrentSsid() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lm != null && !lm.isLocationEnabled()) {
            toast("Bitte kurz den Standort einschalten – sonst verrät Android den WLAN-Namen nicht.");
            return;
        }
        WifiInfo info = wifi.getConnectionInfo();
        String ssid = info != null ? info.getSSID() : null;
        if (ssid != null && ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        if (ssid == null || ssid.isEmpty() || ssid.equals(WifiManager.UNKNOWN_SSID)
                || ssid.equals("<unknown ssid>")) {
            toast("Kein WLAN gefunden. Bist du gerade mit einem WLAN verbunden?");
            return;
        }
        ssidInput.setText(ssid);
        passInput.requestFocus();
    }

    // ---------------------------------------------------------------------------------
    // Schützen
    // ---------------------------------------------------------------------------------

    @TargetApi(Build.VERSION_CODES.S)
    @SuppressWarnings("deprecation")
    private void protect() {
        String ssid = ssidInput.getText().toString().trim();
        String pass = passInput.getText().toString();

        if (ssid.isEmpty()) {
            showResult(R.color.bad, "Bitte zuerst den WLAN-Namen eintragen.");
            return;
        }
        if (!pass.isEmpty() && (pass.length() < 8 || pass.length() > 63)) {
            showResult(R.color.bad, "WLAN-Passwörter haben 8 bis 63 Zeichen. Bitte prüfen.");
            return;
        }

        WifiNetworkSuggestion suggestion;
        try {
            WifiNetworkSuggestion.Builder b = new WifiNetworkSuggestion.Builder().setSsid(ssid);
            if (!pass.isEmpty()) {
                if (wpa3Box.isChecked()) b.setWpa3Passphrase(pass);
                else b.setWpa2Passphrase(pass);
            }
            // Der eigentliche Schutz: bei jeder Verbindung eine neue Zufalls-MAC
            b.setMacRandomizationSetting(WifiNetworkSuggestion.RANDOMIZATION_NON_PERSISTENT);
            suggestion = b.build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            showResult(R.color.bad, "Die Angaben passen nicht: " + e.getMessage());
            return;
        }

        // Gleichnamigen alten Eintrag ersetzen (z. B. neues Passwort)
        List<WifiNetworkSuggestion> old = new ArrayList<>();
        for (WifiNetworkSuggestion s : wifi.getNetworkSuggestions()) {
            if (ssid.equals(s.getSsid())) old.add(s);
        }
        if (!old.isEmpty()) wifi.removeNetworkSuggestions(old);

        int status = wifi.addNetworkSuggestions(Collections.singletonList(suggestion));
        switch (status) {
            case WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS:
            case WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE:
                showResult(R.color.ok, "✓ „" + ssid + "“ ist jetzt geschützt.\n"
                        + "Denk an den Schritt unter „Wichtig“, falls das WLAN schon "
                        + "im Handy gespeichert ist.");
                passInput.setText("");
                break;
            case WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED:
                showResult(R.color.bad, "Android hat dieser App das Vorschlagen von WLANs "
                        + "verboten (wurde vermutlich mit „Nein“ beantwortet).\n"
                        + "Lösung: App deinstallieren, neu installieren und beim nächsten "
                        + "Mal „Ja“/„Zulassen“ wählen.");
                break;
            case WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_EXCEEDS_MAX_PER_APP:
                showResult(R.color.bad, "Zu viele WLANs gespeichert. Bitte unten alte entfernen.");
                break;
            case WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID:
                showResult(R.color.bad, "Android hat die Angaben abgelehnt. "
                        + "Stimmen Name und Passwort?");
                break;
            default:
                showResult(R.color.bad, "Das hat nicht geklappt (Fehlercode " + status + "). "
                        + "Bitte nochmal versuchen.");
        }
        refreshList();
    }

    // ---------------------------------------------------------------------------------
    // Liste der geschützten WLANs
    // ---------------------------------------------------------------------------------

    @TargetApi(Build.VERSION_CODES.S)
    private void refreshList() {
        listContainer.removeAllViews();
        List<WifiNetworkSuggestion> all = wifi.getNetworkSuggestions();
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Noch kein WLAN geschützt.");
            empty.setAlpha(0.7f);
            listContainer.addView(empty);
            return;
        }
        int pad = dp(10);
        for (final WifiNetworkSuggestion s : all) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.box);
            row.setPadding(pad, pad / 2, pad / 2, pad / 2);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(8);

            TextView name = new TextView(this);
            name.setText("🛡 " + s.getSsid());
            name.setTextSize(17);
            row.addView(name, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button remove = new Button(this);
            remove.setText("Entfernen");
            remove.setOnClickListener(v -> {
                wifi.removeNetworkSuggestions(Collections.singletonList(s));
                showResult(R.color.warn, "„" + s.getSsid() + "“ ist nicht mehr geschützt.");
                refreshList();
            });
            row.addView(remove);

            listContainer.addView(row, rowLp);
        }
    }

    // ---------------------------------------------------------------------------------
    // Erlaubnis-Status von Android
    // ---------------------------------------------------------------------------------

    @TargetApi(Build.VERSION_CODES.S)
    private void showApproval(int status) {
        switch (status) {
            case WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER:
            case WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_CARRIER_PRIVILEGE:
                approvalText.setTextColor(getColor(R.color.ok));
                approvalText.setText("✓ Android erlaubt den Schutz.");
                approvalText.setVisibility(View.VISIBLE);
                break;
            case WifiManager.STATUS_SUGGESTION_APPROVAL_PENDING:
                approvalText.setTextColor(getColor(R.color.warn));
                approvalText.setText("Noch ein Schritt: Sobald das WLAN in Reichweite ist, "
                        + "zeigt Android eine Benachrichtigung „Vorgeschlagene WLANs zulassen?“. "
                        + "Bitte mit „Ja“ bzw. „Zulassen“ bestätigen.");
                approvalText.setVisibility(View.VISIBLE);
                break;
            case WifiManager.STATUS_SUGGESTION_APPROVAL_REJECTED_BY_USER:
                approvalText.setTextColor(getColor(R.color.bad));
                approvalText.setText("Android wurde gesagt, dass diese App keine WLANs "
                        + "vorschlagen darf. Der Schutz wirkt so nicht.\n"
                        + "Lösung: App deinstallieren, neu installieren und beim nächsten Mal "
                        + "„Ja“ bzw. „Zulassen“ wählen.");
                approvalText.setVisibility(View.VISIBLE);
                break;
            default:
                approvalText.setVisibility(View.GONE);
        }
    }

    // ---------------------------------------------------------------------------------

    private void showResult(int colorRes, String text) {
        resultText.setTextColor(getColor(colorRes));
        resultText.setText(text);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
